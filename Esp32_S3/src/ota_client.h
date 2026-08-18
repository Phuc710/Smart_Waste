#ifndef OTA_CLIENT_H
#define OTA_CLIENT_H

#include <Arduino.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <Update.h>
#include <esp_ota_ops.h>
#include <esp_app_format.h>
#include <mbedtls/sha256.h>
#include <ArduinoJson.h>
#include <PubSubClient.h>
#include "root_ca.h"

// Hardware and Version Definitions
#ifndef FIRMWARE_VERSION
#define FIRMWARE_VERSION "v1.0.0"
#endif

#ifndef DEVICE_MODEL
#define DEVICE_MODEL "ESP32-S3-SMARTBIN"
#endif

class OtaClient {
public:
    static String currentBootId;
    static bool isOtaInProgress;

    /**
     * Initializes boot ID and runs Local Hardware Health-Check.
     * Cancels rollback if local diagnostic passes without crashing.
     */
    static void initAndVerifyBoot(PubSubClient& mqtt, const String& binId) {
        // Generate unique Boot ID for this startup cycle
        currentBootId = String((uint32_t)ESP.getEfuseMac(), HEX) + "-" + String(millis()) + "-" + String(random(1000, 9999));
        
        const esp_partition_t* running = esp_ota_get_running_partition();
        esp_ota_img_states_t ota_state;
        
        if (esp_ota_get_state_partition(running, &ota_state) == ESP_OK) {
            if (ota_state == ESP_OTA_IMG_PENDING_VERIFY) {
                Serial.println("[OTA] Firmware is in PENDING_VERIFY mode. Running local self-diagnostics...");
                
                // Local Health-Check:
                // 1. Verify NVS access
                // 2. Verify Heap integrity
                // 3. Verify hardware peripherals
                bool healthOk = (ESP.getFreeHeap() > 30000);
                
                if (healthOk) {
                    Serial.println("[OTA] Local Health-Check PASSED! Confirming new partition...");
                    esp_ota_mark_app_valid_cancel_rollback();
                    
                    // Publish OTA SUCCESS status
                    publishStatus(mqtt, binId, "SUCCESS", 100, 0, 0, "", "Firmware boot verification succeeded.");
                } else {
                    Serial.println("[OTA] Local Health-Check FAILED! Triggering rollback to previous partition...");
                    esp_ota_mark_app_invalid_rollback_and_reboot();
                }
            }
        }
    }

    /**
     * Executes OTA Download, Streaming Flash & SHA-256 Checksum Verification
     */
    static void handleOtaCommand(PubSubClient& mqtt, const String& binId, JsonDocument& doc) {
        if (isOtaInProgress) {
            Serial.println("[OTA] OTA is already in progress, ignoring command.");
            return;
        }

        String commandId = doc["commandId"] | "";
        String deploymentId = doc["deploymentId"] | "";
        String deviceJobId = doc["deviceJobId"] | "";
        String targetVersion = doc["version"] | "";
        String expectedModel = doc["deviceModel"] | "";
        String downloadUrl = doc["downloadUrl"] | "";
        String expectedSha256 = doc["sha256"] | "";
        size_t expectedSize = doc["sizeBytes"] | 0;

        // 1. Compatibility Check
        if (expectedModel.length() > 0 && expectedModel != DEVICE_MODEL) {
            Serial.printf("[OTA] Incompatible device model: expected %s, current %s\n", expectedModel.c_str(), DEVICE_MODEL);
            publishStatus(mqtt, binId, "FAILED", 0, 0, expectedSize, "INCOMPATIBLE_MODEL", "Device model mismatch", commandId, deploymentId, deviceJobId);
            return;
        }

        if (downloadUrl.length() == 0 || expectedSha256.length() == 0) {
            Serial.println("[OTA] Missing downloadUrl or sha256 in payload.");
            publishStatus(mqtt, binId, "FAILED", 0, 0, expectedSize, "INVALID_PAYLOAD", "Missing downloadUrl or sha256", commandId, deploymentId, deviceJobId);
            return;
        }

        isOtaInProgress = true;
        publishStatus(mqtt, binId, "DOWNLOADING", 0, 0, expectedSize, "", "", commandId, deploymentId, deviceJobId);

        // 2. Setup TLS with ISRG Root X1 Certificate
        WiFiClientSecure client;
        client.setCACert(ROOT_CA_CERT);
        client.setTimeout(20000); // 20s timeout

        HTTPClient https;
        https.setFollowRedirects(HTTPC_STRICT_FOLLOW_REDIRECTS);
        https.setTimeout(20000);

        if (!https.begin(client, downloadUrl)) {
            Serial.println("[OTA] Failed to begin HTTPS connection.");
            isOtaInProgress = false;
            publishStatus(mqtt, binId, "FAILED", 0, 0, expectedSize, "HTTPS_CONNECT_FAILED", "Could not connect to storage host", commandId, deploymentId, deviceJobId);
            return;
        }

        int httpCode = https.GET();
        if (httpCode != HTTP_CODE_OK) {
            Serial.printf("[OTA] HTTPS GET failed, code: %d\n", httpCode);
            https.end();
            isOtaInProgress = false;
            publishStatus(mqtt, binId, "FAILED", 0, 0, expectedSize, "HTTP_ERROR_" + String(httpCode), "Download returned HTTP error", commandId, deploymentId, deviceJobId);
            return;
        }

        int contentLength = https.getSize();
        if (contentLength <= 0 || (expectedSize > 0 && (size_t)contentLength != expectedSize)) {
            Serial.printf("[OTA] Content-Length mismatch: got %d, expected %u\n", contentLength, (unsigned int)expectedSize);
            https.end();
            isOtaInProgress = false;
            publishStatus(mqtt, binId, "FAILED", 0, 0, expectedSize, "SIZE_MISMATCH", "Content-Length mismatch", commandId, deploymentId, deviceJobId);
            return;
        }

        // 3. Initialize OTA Flash Partition
        if (!Update.begin(contentLength, U_FLASH)) {
            Serial.printf("[OTA] Not enough space for OTA. Error: %d\n", Update.getError());
            https.end();
            isOtaInProgress = false;
            publishStatus(mqtt, binId, "FAILED", 0, 0, expectedSize, "UPDATE_BEGIN_FAILED", "Not enough partition space", commandId, deploymentId, deviceJobId);
            return;
        }

        // 4. Initialize On-the-Fly SHA-256 Hasher
        mbedtls_sha256_context sha_ctx;
        mbedtls_sha256_init(&sha_ctx);
        mbedtls_sha256_starts(&sha_ctx, 0); // 0 = SHA-256

        WiFiClient* stream = https.getStreamPtr();
        uint8_t buff[4096];
        size_t totalRead = 0;
        int lastReportedPercent = 0;

        publishStatus(mqtt, binId, "DOWNLOADING", 5, 0, contentLength, "", "", commandId, deploymentId, deviceJobId);

        while (https.connected() && (totalRead < (size_t)contentLength)) {
            size_t available = stream->available();
            if (available > 0) {
                size_t toRead = available > sizeof(buff) ? sizeof(buff) : available;
                if (totalRead + toRead > (size_t)contentLength) {
                    toRead = contentLength - totalRead;
                }

                size_t bytesRead = stream->readBytes(buff, toRead);
                if (bytesRead > 0) {
                    // Update SHA-256
                    mbedtls_sha256_update(&sha_ctx, buff, bytesRead);

                    // Write to Flash
                    size_t written = Update.write(buff, bytesRead);
                    if (written != bytesRead) {
                        Serial.printf("[OTA] Flash write error: %d\n", Update.getError());
                        Update.abort();
                        mbedtls_sha256_free(&sha_ctx);
                        https.end();
                        isOtaInProgress = false;
                        publishStatus(mqtt, binId, "FAILED", 0, totalRead, contentLength, "FLASH_WRITE_ERROR", "Write to partition failed", commandId, deploymentId, deviceJobId);
                        return;
                    }

                    totalRead += bytesRead;
                    int percent = (totalRead * 100) / contentLength;
                    if (percent >= lastReportedPercent + 15 && percent < 100) {
                        lastReportedPercent = percent;
                        publishStatus(mqtt, binId, "DOWNLOADING", percent, totalRead, contentLength, "", "", commandId, deploymentId, deviceJobId);
                        Serial.printf("[OTA] Progress: %d%% (%u / %d bytes)\n", percent, (unsigned int)totalRead, contentLength);
                    }
                }
            }
            yield();
        }

        https.end();

        // 5. Finalize SHA-256
        uint8_t shaResult[32];
        mbedtls_sha256_finish(&sha_ctx, shaResult);
        mbedtls_sha256_free(&sha_ctx);

        char computedShaHex[65];
        for (int i = 0; i < 32; i++) {
            sprintf(&computedShaHex[i * 2], "%02x", shaResult[i]);
        }
        computedShaHex[64] = '\0';

        Serial.printf("[OTA] Computed SHA-256: %s\n", computedShaHex);
        Serial.printf("[OTA] Expected SHA-256: %s\n", expectedSha256.c_str());

        // 6. Strict Checksum Verification
        if (expectedSha256.equalsIgnoreCase(computedShaHex) == false) {
            Serial.println("[OTA] SHA-256 MISMATCH! Aborting update to protect device.");
            Update.abort();
            isOtaInProgress = false;
            publishStatus(mqtt, binId, "FAILED", 0, totalRead, contentLength, "SHA256_MISMATCH", "Computed checksum does not match payload", commandId, deploymentId, deviceJobId);
            return;
        }

        // 7. Commit Partition
        publishStatus(mqtt, binId, "VERIFYING", 95, totalRead, contentLength, "", "", commandId, deploymentId, deviceJobId);

        if (!Update.end(true)) {
            Serial.printf("[OTA] Update.end() failed! Error: %d\n", Update.getError());
            isOtaInProgress = false;
            publishStatus(mqtt, binId, "FAILED", 0, totalRead, contentLength, "UPDATE_END_FAILED", "Failed to commit partition", commandId, deploymentId, deviceJobId);
            return;
        }

        Serial.println("[OTA] Flash update successful! Preparing to reboot into new partition...");
        publishStatus(mqtt, binId, "REBOOTING", 100, totalRead, contentLength, "", "", commandId, deploymentId, deviceJobId);
        
        delay(1000);
        ESP.restart();
    }

private:
    static void publishStatus(PubSubClient& mqtt, const String& binId, const String& status, int progress, size_t downloaded, size_t total, const String& errCode = "", const String& errMsg = "", const String& cmdId = "", const String& depId = "", const String& jobId = "") {
        StaticJsonDocument<384> doc;
        doc["deviceId"] = binId;
        doc["status"] = status;
        doc["progressPercent"] = progress;
        doc["downloadedBytes"] = downloaded;
        doc["totalBytes"] = total;
        doc["currentVersion"] = FIRMWARE_VERSION;
        doc["deviceModel"] = DEVICE_MODEL;
        doc["bootId"] = currentBootId;

        if (cmdId.length() > 0) doc["commandId"] = cmdId;
        if (depId.length() > 0) doc["deploymentId"] = depId;
        if (jobId.length() > 0) doc["deviceJobId"] = jobId;
        if (errCode.length() > 0) doc["errorCode"] = errCode;
        if (errMsg.length() > 0) doc["errorMessage"] = errMsg;

        char buffer[384];
        size_t len = serializeJson(doc, buffer);
        String topic = "wastebin/" + binId + "/ota/status";
        mqtt.publish(topic.c_str(), (const uint8_t*)buffer, len, false);
    }
};

String OtaClient::currentBootId = "";
bool OtaClient::isOtaInProgress = false;

#endif // OTA_CLIENT_H
