'use strict';

const crypto = require('crypto');
const env = require('../config/env');
const logger = require('../core/logger');
const { supabaseServiceRequest, storageServiceRequest } = require('../core/supabase');

// ESP32 App Descriptor Magic Word defined in esp_app_format.h
const ESP_APP_DESC_MAGIC_WORD = 0xABCD5432;
const MAX_FIRMWARE_SIZE = 3.5 * 1024 * 1024; // 3.5 MB max for safe 4MB Flash OTA partition (1.875MB app slot)
const MIN_FIRMWARE_SIZE = 10 * 1024; // 10 KB min

/**
 * Parses ESP32 Image Header and App Descriptor (esp_app_desc_t at offset 0x20)
 * @param {Buffer} buffer
 */
function parseEspAppDescriptor(buffer) {
    if (!Buffer.isBuffer(buffer) || buffer.length < 128) {
        return { isValidEspImage: false, hasAppDesc: false, error: 'File binary quá nhỏ hoặc không hợp lệ.' };
    }

    const firstByte = buffer[0];
    const isMagicE9 = firstByte === 0xE9;

    // Check App Descriptor struct at offset 32 (0x20)
    let hasAppDesc = false;
    let appDesc = null;

    if (buffer.length >= 0x20 + 256) {
        const magicWord = buffer.readUInt32LE(0x20);
        if (magicWord === ESP_APP_DESC_MAGIC_WORD) {
            hasAppDesc = true;
            const secureVersion = buffer.readUInt32LE(0x24);
            const version = buffer.toString('utf8', 0x30, 0x50).replace(/\0.*$/g, '').trim();
            const projectName = buffer.toString('utf8', 0x50, 0x70).replace(/\0.*$/g, '').trim();
            const time = buffer.toString('utf8', 0x70, 0x80).replace(/\0.*$/g, '').trim();
            const date = buffer.toString('utf8', 0x80, 0x90).replace(/\0.*$/g, '').trim();
            const idfVer = buffer.toString('utf8', 0x90, 0xB0).replace(/\0.*$/g, '').trim();
            const elfSha256 = buffer.subarray(0xB0, 0xD0).toString('hex');

            appDesc = {
                secureVersion,
                version,
                projectName,
                compileTime: `${date} ${time}`.trim(),
                idfVersion: idfVer,
                elfSha256
            };
        }
    }

    return {
        isValidEspImage: isMagicE9 || hasAppDesc,
        hasAppDesc,
        appDesc
    };
}

/**
 * Calculates SHA-256 hex string of a buffer
 */
function calculateSha256(buffer) {
    return crypto.createHash('sha256').update(buffer).digest('hex');
}

/**
 * Signs firmware binary using internal cryptographic secret or system key
 */
function signFirmware(buffer) {
    const secret = process.env.FIRMWARE_SIGNING_SECRET || env.SUPABASE_SERVICE_ROLE_KEY || 'smartwaste-ota-signing-key';
    return crypto.createHmac('sha256', secret).update(buffer).digest('hex');
}

/**
 * Validates SemVer version string
 */
function isValidSemVer(version) {
    if (!version || typeof version !== 'string') return false;
    const clean = version.startsWith('v') ? version.substring(1) : version;
    return /^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/.test(clean);
}

/**
 * Uploads firmware binary to Supabase Storage and creates firmware_releases record
 */
async function createFirmwareRelease({ fileBuffer, fileName, version, deviceModel = 'ESP32-S3-SMARTBIN', releaseNotes = '', userId = null }) {
    if (!Buffer.isBuffer(fileBuffer) || fileBuffer.length === 0) {
        const err = new Error('File firmware .bin rỗng hoặc không hợp lệ.');
        err.statusCode = 400;
        throw err;
    }

    if (fileBuffer.length > MAX_FIRMWARE_SIZE) {
        const err = new Error(`Dung lượng file (${(fileBuffer.length / (1024 * 1024)).toFixed(2)} MB) vượt quá giới hạn an toàn 3.5 MB của phân vùng OTA 4MB flash.`);
        err.statusCode = 400;
        throw err;
    }

    if (fileBuffer.length < MIN_FIRMWARE_SIZE) {
        const err = new Error('Dung lượng file quá nhỏ để là một bản firmware ESP32 hợp lệ.');
        err.statusCode = 400;
        throw err;
    }

    // 1. Parse image descriptor
    const parsed = parseEspAppDescriptor(fileBuffer);
    if (!parsed.isValidEspImage) {
        const err = new Error('File không đúng cấu trúc ESP32 image (thiếu magic byte 0xE9 hoặc App Descriptor).');
        err.statusCode = 400;
        throw err;
    }

    // 2. Validate SemVer
    const normalizedVersion = version.startsWith('v') ? version : `v${version}`;
    if (!isValidSemVer(normalizedVersion)) {
        const err = new Error('Phiên bản firmware phải tuân thủ chuẩn SemVer (ví dụ: v1.0.0, v1.2.3-beta).');
        err.statusCode = 400;
        throw err;
    }

    // 3. Compute Official SHA-256 and Signature
    const sha256 = calculateSha256(fileBuffer);
    const signature = signFirmware(fileBuffer);

    // 4. Check for duplicate version or sha256 in DB
    const existing = await supabaseServiceRequest(`firmware_releases?select=id,version,sha256&or=(and(device_model.eq.${encodeURIComponent(deviceModel)},version.eq.${encodeURIComponent(normalizedVersion)}),sha256.eq.${encodeURIComponent(sha256)})`);
    if (Array.isArray(existing) && existing.length > 0) {
        const match = existing[0];
        if (match.sha256 === sha256) {
            const err = new Error(`File firmware này (SHA-256: ${sha256.substring(0, 12)}...) đã tồn tại trong hệ thống ở bản ${match.version}.`);
            err.statusCode = 409;
            throw err;
        }
        const err = new Error(`Phiên bản ${normalizedVersion} cho dòng thiết bị ${deviceModel} đã tồn tại.`);
        err.statusCode = 409;
        throw err;
    }

    // 5. Upload to Supabase Storage Bucket 'firmware-releases'
    const storagePath = `firmware/${deviceModel}/${normalizedVersion}/${sha256}.bin`;
    
    try {
        await storageServiceRequest(`object/firmware-releases/${encodeURIComponent(storagePath)}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/octet-stream',
                'x-upsert': 'true'
            },
            body: fileBuffer
        });
    } catch (uploadErr) {
        logger.error('Firmware Storage Upload Error', uploadErr.message);
        // If bucket does not exist, attempt to auto-create
        try {
            await storageServiceRequest('bucket', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: 'firmware-releases', name: 'firmware-releases', public: false })
            });
            // Retry upload
            await storageServiceRequest(`object/firmware-releases/${encodeURIComponent(storagePath)}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/octet-stream',
                    'x-upsert': 'true'
                },
                body: fileBuffer
            });
        } catch (retryErr) {
            logger.error('Firmware Storage Retry Error', retryErr.message);
            const err = new Error(`Lỗi lưu trữ Supabase Storage: ${uploadErr.message}`);
            err.statusCode = 502;
            throw err;
        }
    }

    // 6. Insert record into firmware_releases table
    const releaseRecord = {
        version: normalizedVersion,
        device_model: deviceModel,
        file_name: fileName || `smartwaste_${deviceModel}_${normalizedVersion}.bin`,
        object_path: storagePath,
        size_bytes: fileBuffer.length,
        sha256,
        signature,
        release_notes: releaseNotes || (parsed.appDesc ? `Built on IDF ${parsed.appDesc.idfVersion} (${parsed.appDesc.compileTime})` : ''),
        status: 'READY',
        created_by: userId || null,
        created_at: new Date().toISOString(),
        published_at: new Date().toISOString()
    };

    const inserted = await supabaseServiceRequest('firmware_releases', {
        method: 'POST',
        headers: { Prefer: 'return=representation' },
        body: JSON.stringify(releaseRecord)
    });

    const release = Array.isArray(inserted) ? inserted[0] : inserted;
    logger.info('Firmware Release Created', `${normalizedVersion} (${deviceModel}) - SHA-256: ${sha256.substring(0, 16)}...`);

    return {
        ...release,
        appDesc: parsed.appDesc
    };
}

/**
 * Lists all firmware releases
 */
async function listReleases(deviceModel = null) {
    let query = 'firmware_releases?select=*&order=created_at.desc';
    if (deviceModel) {
        query += `&device_model=eq.${encodeURIComponent(deviceModel)}`;
    }
    const rows = await supabaseServiceRequest(query);
    return rows || [];
}

/**
 * Gets a single release by ID
 */
async function getReleaseById(id) {
    if (!id) return null;
    const rows = await supabaseServiceRequest(`firmware_releases?select=*&id=eq.${encodeURIComponent(id)}`);
    return Array.isArray(rows) && rows.length > 0 ? rows[0] : null;
}

/**
 * Generates a temporary Signed Download URL (valid for 3600 seconds = 1 hour)
 */
async function getSignedFirmwareUrl(objectPath, expiresInSeconds = 3600) {
    const encodedPath = encodeURIComponent(objectPath);
    const data = await storageServiceRequest(`object/sign/firmware-releases/${encodedPath}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ expiresIn: expiresInSeconds })
    });

    if (!data || !data.signedURL) {
        throw new Error('Không tạo được Signed Download URL từ Supabase Storage.');
    }

    const signedPath = data.signedURL;
    return signedPath.startsWith('http')
        ? signedPath
        : `${env.SUPABASE_URL}/storage/v1${signedPath.startsWith('/') ? '' : '/'}${signedPath}`;
}

module.exports = {
    parseEspAppDescriptor,
    calculateSha256,
    signFirmware,
    isValidSemVer,
    createFirmwareRelease,
    listReleases,
    getReleaseById,
    getSignedFirmwareUrl
};
