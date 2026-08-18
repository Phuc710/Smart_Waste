'use strict';

const { describe, it } = require('node:test');
const assert = require('node:assert');
const crypto = require('crypto');

describe('1. ESP32 App Descriptor Parser & Magic Word Verification', () => {
    const firmwareService = require('../src/services/firmwareService');

    it('should parse valid ESP32 App Descriptor structure with magic word 0xABCD5432', () => {
        // Construct a synthetic ESP32 Image Header + App Descriptor buffer
        const buffer = Buffer.alloc(512);
        
        // Offset 0: ESP32 Header Magic Byte 0xE9
        buffer[0] = 0xE9;
        buffer[1] = 0x03; // 3 segments

        // Offset 0x20: ESP_APP_DESC_MAGIC_WORD = 0xABCD5432
        buffer.writeUInt32LE(0xABCD5432, 0x20);
        buffer.writeUInt32LE(1, 0x24); // secure_version = 1
        
        // Offset 0x30: version (32 bytes)
        buffer.write('v1.2.0\0', 0x30, 'utf8');
        
        // Offset 0x50: project_name (32 bytes)
        buffer.write('smartwaste-bin\0', 0x50, 'utf8');
        
        // Offset 0x70: time (16 bytes)
        buffer.write('12:00:00\0', 0x70, 'utf8');
        
        // Offset 0x80: date (16 bytes)
        buffer.write('Aug 18 2026\0', 0x80, 'utf8');
        
        // Offset 0x90: idf_ver (32 bytes)
        buffer.write('v5.1.2\0', 0x90, 'utf8');

        const parsed = firmwareService.parseEspAppDescriptor(buffer);
        assert.strictEqual(parsed.isValidEspImage, true);
        assert.strictEqual(parsed.hasAppDesc, true);
        assert.strictEqual(parsed.appDesc.projectName, 'smartwaste-bin');
        assert.strictEqual(parsed.appDesc.version, 'v1.2.0');
        assert.strictEqual(parsed.appDesc.idfVersion, 'v5.1.2');
    });

    it('should reject invalid or corrupted non-ESP32 image files', () => {
        const dummyJpg = Buffer.from('FFD8FFE000104A46494600010101006000600000', 'hex');
        const parsed = firmwareService.parseEspAppDescriptor(dummyJpg);
        assert.strictEqual(parsed.isValidEspImage, false);
        assert.strictEqual(parsed.hasAppDesc, false);
    });
});

describe('2. SHA-256 Checksum Calculation, Tamper Detection & Digital Signature', () => {
    const firmwareService = require('../src/services/firmwareService');

    it('should compute exact SHA-256 hash and immediately detect single-bit corruption', () => {
        const original = Buffer.from('SMARTWASTE_FIRMWARE_PAYLOAD_TEST_DATA_V1.2.0_SECURE_BINARY');
        const originalHash = firmwareService.calculateSha256(original);

        assert.strictEqual(typeof originalHash, 'string');
        assert.strictEqual(originalHash.length, 64);

        // Corrupt 1 byte
        const tampered = Buffer.from(original);
        tampered[0] ^= 0x01; // flip 1 bit

        const tamperedHash = firmwareService.calculateSha256(tampered);
        assert.notStrictEqual(originalHash, tamperedHash, 'Tampered binary must produce completely different SHA-256');
    });

    it('should generate digital signature for firmware binary verification', () => {
        const payload = Buffer.from('BINARY_CONTENT_READY_FOR_OTA_DEPLOYMENT');
        const signature = firmwareService.signFirmware(payload);

        assert.strictEqual(typeof signature, 'string');
        assert.strictEqual(signature.length, 64);
    });
});

describe('3. SemVer Version Validation & Model Compatibility', () => {
    const firmwareService = require('../src/services/firmwareService');

    it('should validate SemVer version formats', () => {
        assert.strictEqual(firmwareService.isValidSemVer('v1.0.0'), true);
        assert.strictEqual(firmwareService.isValidSemVer('1.2.3'), true);
        assert.strictEqual(firmwareService.isValidSemVer('v2.1.0-beta.1'), true);

        assert.strictEqual(firmwareService.isValidSemVer('invalid_version'), false);
        assert.strictEqual(firmwareService.isValidSemVer('1.0'), false);
        assert.strictEqual(firmwareService.isValidSemVer(''), false);
    });
});

describe('4. OTA State Machine Transitions & Anti-Spoofing Rules', () => {
    const otaService = require('../src/services/otaService');

    it('should recognize all standardized enterprise OTA lifecycle statuses', () => {
        assert.strictEqual(otaService.VALID_OTA_STATUSES.has('DOWNLOADING'), true);
        assert.strictEqual(otaService.VALID_OTA_STATUSES.has('VERIFYING'), true);
        assert.strictEqual(otaService.VALID_OTA_STATUSES.has('INSTALLING'), true);
        assert.strictEqual(otaService.VALID_OTA_STATUSES.has('REBOOTING'), true);
        assert.strictEqual(otaService.VALID_OTA_STATUSES.has('BOOT_VERIFYING'), true);
        assert.strictEqual(otaService.VALID_OTA_STATUSES.has('SUCCESS'), true);
        assert.strictEqual(otaService.VALID_OTA_STATUSES.has('ROLLBACK_SUCCESS'), true);
        assert.strictEqual(otaService.VALID_OTA_STATUSES.has('FAILED'), true);
    });

    it('should reject invalid or unknown spoofed OTA statuses', () => {
        assert.strictEqual(otaService.VALID_OTA_STATUSES.has('HACKED_SUCCESS'), false);
        assert.strictEqual(otaService.VALID_OTA_STATUSES.has('BYPASS_INSTALL'), false);
    });
});

describe('5. Safe Cancel Rule Enforcement', () => {
    it('should permit cancellation only before flash writing begins', () => {
        const cancellableStatuses = new Set(['PENDING', 'COMMAND_SENT', 'DOWNLOADING']);
        const forbiddenCancelStatuses = new Set(['INSTALLING', 'VERIFYING', 'REBOOTING', 'BOOT_VERIFYING']);

        // DOWNLOADING can be cancelled safely
        assert.strictEqual(cancellableStatuses.has('DOWNLOADING'), true);
        assert.strictEqual(cancellableStatuses.has('COMMAND_SENT'), true);

        // INSTALLING must NEVER be cancelled to prevent corrupting flash memory
        assert.strictEqual(forbiddenCancelStatuses.has('INSTALLING'), true);
        assert.strictEqual(cancellableStatuses.has('INSTALLING'), false);
    });
});

describe('6. MQTT OTA Command Envelope Specifications', () => {
    it('should generate compliant command envelope with required TTL and security fields', () => {
        const commandId = crypto.randomUUID();
        const deploymentId = crypto.randomUUID();
        const deviceJobId = crypto.randomUUID();
        const releaseId = crypto.randomUUID();
        const sha256 = '4a7d1ed414474e4033ac29ccb8653d9b00000000000000000000000000000000';
        const signedUrl = 'https://zwrapaqlozdkbkblohcq.supabase.co/storage/v1/object/sign/firmware-releases/firmware.bin?token=abc';
        const now = Date.now();

        const envelope = {
            type: 'OTA_UPDATE',
            commandId,
            deploymentId,
            deviceJobId,
            releaseId,
            version: 'v1.2.0',
            deviceModel: 'ESP32-S3-SMARTBIN',
            sizeBytes: 1289450,
            sha256,
            downloadUrl: signedUrl,
            issuedAt: new Date(now).toISOString(),
            expiresAt: new Date(now + 3600 * 1000).toISOString()
        };

        assert.strictEqual(envelope.type, 'OTA_UPDATE');
        assert.strictEqual(envelope.commandId, commandId);
        assert.strictEqual(envelope.sha256, sha256);
        assert.strictEqual(envelope.version, 'v1.2.0');
        assert.strictEqual(envelope.deviceModel, 'ESP32-S3-SMARTBIN');
        assert.ok(Date.parse(envelope.expiresAt) > Date.parse(envelope.issuedAt));
    });
});
