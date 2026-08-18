#include <Arduino.h>
#include <ESP32Servo.h>
#include <WiFiManager.h>
#include <PubSubClient.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include "config.h"
#include "ota_client.h"

// =========================================================
// STATE MACHINE & BIẾN GLOBAL
// =========================================================
enum BinState {
    CLOSED,
    CONFIRMING,
    OPEN
};

BinState currentState = CLOSED;
Servo lidServo;

uint32_t stateTimer = 0;
uint32_t logTimer = 0;

int currentServoAngle = SERVO_POS_CLOSED;
float distUser = 0.0;
float distLevel = 0.0;
float binLevelPercent = 0.0;
bool automaticMode = true;
bool collectionPaused = false;
String lastCommandId = "";
String lastCommandAction = "";

// WiFi & MQTT Variables
WiFiClient espClient;
PubSubClient mqttClient(espClient);
Preferences preferences;

char mqtt_server[40] = "";
char bin_name[64] = "";
char bin_location[128] = "";
const int mqtt_port = 1883;

// Lấy MAC address làm ID thùng rác
String binId = "";

// =========================================================
// HÀM BỔ TRỢ (HELPER FUNCTIONS)
// =========================================================

const char* getStateStr(BinState state) {
    switch (state) {
        case CLOSED:     return "CLOSED";
        case CONFIRMING: return "CONFIRMING";
        case OPEN:       return "OPEN";
        default:         return "UNKNOWN";
    }
}

float readDistance(uint8_t trigPin, uint8_t echoPin) {
    digitalWrite(trigPin, LOW);
    delayMicroseconds(2);
    digitalWrite(trigPin, HIGH);
    delayMicroseconds(10);
    digitalWrite(trigPin, LOW);

    unsigned long duration = pulseIn(echoPin, HIGH, 25000);
    if (duration == 0) return 999.0;

    return (duration * 0.0343) / 2.0;
}

float calculateLevel(float distCm) {
    if (distCm >= BIN_DEPTH_EMPTY_CM) return 0.0;
    if (distCm <= BIN_DEPTH_FULL_CM) return 100.0;
    return ((BIN_DEPTH_EMPTY_CM - distCm) / (BIN_DEPTH_EMPTY_CM - BIN_DEPTH_FULL_CM)) * 100.0;
}

void setServo(bool open) {
    int targetAngle = open ? SERVO_POS_OPEN : SERVO_POS_CLOSED;
    if (currentServoAngle != targetAngle) {
        currentServoAngle = targetAngle;
        lidServo.write(currentServoAngle);
    }
}

void publishMqttData() {
    if (!mqttClient.connected()) return;

    // Tạo chuỗi JSON
    StaticJsonDocument<512> doc;
    doc["state"] = getStateStr(currentState);
    doc["distUser"] = distUser;
    doc["distLevel"] = distLevel;
    doc["levelPercent"] = binLevelPercent;
    doc["servoAngle"] = currentServoAngle;
    doc["controlMode"] = automaticMode ? "AUTO" : "MANUAL";
    doc["collectionPaused"] = collectionPaused;
    doc["ipAddress"] = WiFi.localIP().toString();
    doc["name"] = bin_name;
    doc["location"] = bin_location;
    if (lastCommandId.length() > 0) {
        doc["commandAckId"] = lastCommandId;
        doc["commandAckAction"] = lastCommandAction;
    }

    char payload[512];
    size_t payloadLength = serializeJson(doc, payload, sizeof(payload));

    String topic = "wastebin/" + binId + "/status";
    bool published = mqttClient.publish(topic.c_str(), payload, payloadLength);
    if (!published) {
        Serial.printf("[MQTT] Publish FAILED (payload=%u bytes, buffer=%u bytes)\n",
                      static_cast<unsigned int>(payloadLength),
                      static_cast<unsigned int>(mqttClient.getBufferSize()));
    }
}

const int RECENT_CMD_CAPACITY = 16;
String recentCommandIds[RECENT_CMD_CAPACITY];
int recentCmdIndex = 0;

bool isRecentCommand(const String& cmdId) {
    if (cmdId.length() == 0) return false;
    for (int i = 0; i < RECENT_CMD_CAPACITY; i++) {
        if (recentCommandIds[i] == cmdId) return true;
    }
    return false;
}

void recordRecentCommand(const String& cmdId) {
    if (cmdId.length() == 0) return;
    recentCommandIds[recentCmdIndex] = cmdId;
    recentCmdIndex = (recentCmdIndex + 1) % RECENT_CMD_CAPACITY;
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
    if (!payload || length == 0) return; // Bỏ qua gói tin xóa retain từ broker

    String otaTopic = "wastebin/" + binId + "/ota";
    if (String(topic) == otaTopic) {
        StaticJsonDocument<768> otaDoc;
        DeserializationError err = deserializeJson(otaDoc, payload, length);
        if (!err) {
            Serial.println("[MQTT] Received OTA command packet. Forwarding to OtaClient...");
            OtaClient::handleOtaCommand(mqttClient, binId, otaDoc);
        } else {
            Serial.println("[MQTT] Failed to parse OTA command JSON.");
        }
        return;
    }

    String commandTopic = "wastebin/" + binId + "/command";
    if (String(topic) != commandTopic) return;

    StaticJsonDocument<256> doc;
    DeserializationError error = deserializeJson(doc, payload, length);
    if (error) {
        Serial.println("[MQTT] Invalid command payload");
        return;
    }

    String action = doc["action"] | "";
    String commandId = doc["commandId"] | "";
    action.toUpperCase();

    // Check circular buffer for deduplication (Anti-Replay Protection)
    if (commandId.length() > 0 && (commandId == lastCommandId || isRecentCommand(commandId))) {
        Serial.println("[MQTT] Duplicate command detected, re-sending ACK only: " + action);
        lastCommandId = commandId;
        lastCommandAction = action;
        publishMqttData();
        return;
    }

    if (action == "OPEN") {
        automaticMode = false;
        setServo(true);
        currentState = OPEN;
    } else if (action == "CLOSE") {
        automaticMode = false;
        setServo(false);
        currentState = CLOSED;
    } else if (action == "AUTO") {
        collectionPaused = false;
        preferences.putBool("collection_paused", false);
        automaticMode = true;
        setServo(false);
        currentState = CLOSED;
        stateTimer = millis();
    } else if (action == "MANUAL") {
        automaticMode = false;
    } else if (action == "PAUSE") {
        collectionPaused = true;
        preferences.putBool("collection_paused", true);
        automaticMode = false;
        setServo(false);
        currentState = CLOSED;
    } else if (action == "RESUME") {
        collectionPaused = false;
        preferences.putBool("collection_paused", false);
        automaticMode = true;
        setServo(false);
        currentState = CLOSED;
        stateTimer = millis();
    } else {
        Serial.println("[MQTT] Unknown command: " + action);
        return;
    }

    lastCommandId = commandId;
    lastCommandAction = action;
    if (commandId.length() > 0) {
        recordRecentCommand(commandId);
        preferences.putString("last_cmd_id", lastCommandId);
        preferences.putString("last_cmd_action", lastCommandAction);
    }

    Serial.println("[MQTT] Applied command: " + action);
    publishMqttData();
}

void printSingleLineLog() {
    char levelStr[16];
    if (collectionPaused) {
        snprintf(levelStr, sizeof(levelStr), "PAUSED      ");
    } else if (currentState == CLOSED) {
        snprintf(levelStr, sizeof(levelStr), "%4.1fcm (%2.0f%%)", distLevel, binLevelPercent);
    } else {
        snprintf(levelStr, sizeof(levelStr), "PAUSED      ");
    }

    Serial.printf("[STATE: %-10s] | [S1_USER: %4.1fcm] | [S2_LEVEL: %s] | [SERVO: %2d deg]\n",
                  getStateStr(currentState),
                  distUser,
                  levelStr,
                  currentServoAngle);
}

uint32_t lastMqttReconnectAttempt = 0;
void reconnectMqtt() {
    if (!mqttClient.connected()) {
        uint32_t now = millis();
        if (now - lastMqttReconnectAttempt < 3000) return; // Non-blocking throttle: thử lại mỗi 3s
        lastMqttReconnectAttempt = now;

        Serial.print("Connecting to MQTT Server: ");
        Serial.print(mqtt_server);
        Serial.println("...");
        
        String clientId = "ESP32-SmartBin-" + binId;
        
        if (mqttClient.connect(clientId.c_str())) {
            Serial.println("MQTT Connected!");
            String commandTopic = "wastebin/" + binId + "/command";
            String otaTopic = "wastebin/" + binId + "/ota";
            mqttClient.subscribe(commandTopic.c_str(), 1);
            mqttClient.subscribe(otaTopic.c_str(), 1);
            publishMqttData();
        } else {
            Serial.print("MQTT Connect Failed, rc=");
            Serial.print(mqttClient.state());
            Serial.println(" - Sẽ thử lại sau 3 giây...");
        }
    }
}

// Cờ lưu việc cần save config sau khi WiFiManager chạy
bool shouldSaveConfig = false;
void saveConfigCallback() {
    Serial.println("Should save config");
    shouldSaveConfig = true;
}

// =========================================================
// SETUP & LOOP
// =========================================================

void setup() {
    Serial.begin(115200);
    delay(500);

    // --- CẤU HÌNH NÚT RESET CONFIG ---
    pinMode(PIN_RESET_CONFIG, INPUT_PULLUP);

    // --- CẤU HÌNH HARDWARE ---
    pinMode(PIN_TRIG_USER, OUTPUT);
    pinMode(PIN_ECHO_USER, INPUT);
    pinMode(PIN_TRIG_LEVEL, OUTPUT);
    pinMode(PIN_ECHO_LEVEL, INPUT);

    ESP32PWM::allocateTimer(0);
    lidServo.setPeriodHertz(50);
    lidServo.attach(PIN_SERVO, 500, 2400);

    setServo(false);
    Serial.println(F("\n=================== SYSTEM STARTING ==================="));

    // Lấy ID thùng rác
    binId = WiFi.macAddress();
    binId.replace(":", "");
    Serial.println("Bin ID (MAC): " + binId);

    // --- CẤU HÌNH WIFIMANAGER & PREFERENCES ---
    preferences.begin("smartbin", false);
    String saved_mqtt = preferences.getString("mqtt_ip", "");
    String saved_name = preferences.getString("bin_name", "");
    String saved_location = preferences.getString("location", "");
    lastCommandId = preferences.getString("last_cmd_id", "");
    lastCommandAction = preferences.getString("last_cmd_action", "");
    collectionPaused = preferences.getBool("collection_paused", false);
    if (collectionPaused) automaticMode = false;
    if (saved_mqtt.length() > 0) {
        saved_mqtt.toCharArray(mqtt_server, 40);
    }
    if (saved_name.length() > 0) {
        saved_name.toCharArray(bin_name, sizeof(bin_name));
    } else {
        String defaultName = "Thung rac " + binId.substring(binId.length() - 6);
        defaultName.toCharArray(bin_name, sizeof(bin_name));
    }
    if (saved_location.length() > 0) {
        saved_location.toCharArray(bin_location, sizeof(bin_location));
    }

    WiFiManager wm;
    wm.setSaveConfigCallback(saveConfigCallback);
    
    // Custom Parameter: (id, label, default, length)
    WiFiManagerParameter custom_mqtt_server("server", "MQTT Broker IP", mqtt_server, 40);
    WiFiManagerParameter custom_bin_name("bin_name", "Ten thung rac", bin_name, sizeof(bin_name));
    WiFiManagerParameter custom_bin_location("location", "Ten dia diem / dia chi day du", bin_location, sizeof(bin_location));
    wm.addParameter(&custom_mqtt_server);
    wm.addParameter(&custom_bin_name);
    wm.addParameter(&custom_bin_location);

    // Bật AP tự động với tên SmartBin_Setup
    String apName = "SmartBin_" + binId.substring(binId.length() - 4);
    if (!wm.autoConnect(apName.c_str())) {
        Serial.println("Failed to connect and hit timeout");
        delay(3000);
        ESP.restart();
    }

    // Đã kết nối WiFi
    Serial.println("");
    Serial.println("WiFi Connected!");
    Serial.print("IP Address: ");
    Serial.println(WiFi.localIP());

    // Cập nhật MQTT IP từ WiFiManager
    strcpy(mqtt_server, custom_mqtt_server.getValue());
    strlcpy(bin_name, custom_bin_name.getValue(), sizeof(bin_name));
    strlcpy(bin_location, custom_bin_location.getValue(), sizeof(bin_location));

    if (shouldSaveConfig) {
        preferences.putString("mqtt_ip", String(mqtt_server));
        preferences.putString("bin_name", String(bin_name));
        preferences.putString("location", String(bin_location));
        Serial.println("Saved new MQTT Config: " + String(mqtt_server));
        Serial.println("Saved bin name: " + String(bin_name));
        Serial.println("Saved location: " + String(bin_location));
    }

    // --- CẤU HÌNH MQTT ---
    if (String(mqtt_server).length() > 0) {
        mqttClient.setBufferSize(768);
        mqttClient.setServer(mqtt_server, mqtt_port);
        mqttClient.setCallback(mqttCallback);
    }

    // --- LOCAL HEALTH-CHECK & BOOT VERIFICATION ---
    OtaClient::initAndVerifyBoot(mqttClient, binId);

    Serial.println(F("\n=================== SYSTEM READY ==================="));
}

void loop() {
    uint32_t now = millis();

    // 0. Kiểm tra nút RESET CONFIG (giữ nút BOOT 3 giây khi đang chạy)
    if (digitalRead(PIN_RESET_CONFIG) == LOW) {
        Serial.println("[RESET] Đang giữ nút BOOT... Giữ thêm để xóa cấu hình.");
        uint32_t pressStart = millis();
        while (digitalRead(PIN_RESET_CONFIG) == LOW) {
            if (millis() - pressStart >= RESET_HOLD_MS) {
                Serial.println("[RESET] Đã giữ đủ 3 giây! Xóa cấu hình WiFi & MQTT...");
                preferences.begin("smartbin", false);
                preferences.clear();
                preferences.end();
                WiFiManager wm;
                wm.resetSettings();
                Serial.println("[RESET] Khởi động lại vào chế độ cài đặt...");
                delay(500);
                ESP.restart();
            }
            delay(50);
        }
        Serial.println("[RESET] Thả tay quá sớm, tiếp tục bình thường.");
    }

    // MQTT Loop
    if (WiFi.status() == WL_CONNECTED && String(mqtt_server).length() > 0) {
        if (!mqttClient.connected()) {
            reconnectMqtt();
        }
        mqttClient.loop();
    }

    // Khi nhân viên đang thu gom, vẫn duy trì Wi-Fi/MQTT nhưng không kích hai cảm biến.
    bool userPresent = false;
    if (!collectionPaused) {
        // 1. Đọc Cảm biến 1
        distUser = readDistance(PIN_TRIG_USER, PIN_ECHO_USER);
        userPresent = (distUser <= USER_DETECT_DIST_CM);
    }

    // 2. Đọc Cảm biến 2 (Chỉ đọc khi CLOSED)
    if (!collectionPaused && currentState == CLOSED) {
        distLevel = readDistance(PIN_TRIG_LEVEL, PIN_ECHO_LEVEL);
        if (distLevel < 900.0) {
            binLevelPercent = calculateLevel(distLevel);
        }
    }

    // 3. State Machine
    bool stateChanged = false;
    BinState lastState = currentState;

    if (!collectionPaused && automaticMode) switch (currentState) {
        case CLOSED:
            if (userPresent) {
                stateTimer = now;
                currentState = CONFIRMING;
            }
            break;

        case CONFIRMING:
            if (!userPresent) {
                currentState = CLOSED;
            } else if (now - stateTimer >= USER_CONFIRM_MS) {
                setServo(true);
                stateTimer = now;
                currentState = OPEN;
            }
            break;

        case OPEN:
            if (userPresent) {
                stateTimer = now;
            } else if (now - stateTimer >= AUTO_CLOSE_MS) {
                setServo(false);
                currentState = CLOSED;
            }
            break;
    }

    if (currentState != lastState) {
        stateChanged = true;
    }

    // 4. Print Log định kỳ hoặc khi có sự thay đổi State
    if (now - logTimer >= LOG_INTERVAL_MS || stateChanged) {
        // Tránh reset timer nếu chỉ là stateChanged, để vẫn có log định kỳ ổn định
        if (now - logTimer >= LOG_INTERVAL_MS) {
            logTimer = now;
        }
        
        printSingleLineLog();
        publishMqttData();
    }

    delay(20);
}
