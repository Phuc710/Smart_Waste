import time
import json
import random
import argparse
import paho.mqtt.client as mqtt

class MockSmartBin:
    def __init__(self, broker, port, bin_id, name, location):
        self.broker = broker
        self.port = port
        self.bin_id = bin_id
        
        # Các biến trạng thái mô phỏng ESP32 (dựa theo main.cpp)
        self.state = "CLOSED"
        self.distUser = 100.0
        self.distLevel = 40.0 # Khoảng cách thùng rỗng (BIN_DEPTH_EMPTY_CM)
        self.levelPercent = 0.0
        self.servoAngle = 0
        self.controlMode = "AUTO"
        self.collectionPaused = False
        self.ipAddress = "192.168.1.100" # IP giả mạo
        self.name = name
        self.location = location
        self.lastCommandId = ""
        self.lastCommandAction = ""
        
        # Biến trạng thái chạy của giả lập
        self.running = True
        
        # Cấu hình MQTT
        self.client = mqtt.Client(f"FakeESP32-SmartBin-{self.bin_id}")
        self.client.on_connect = self.on_connect
        self.client.on_message = self.on_message
        
        # Topics tương tự ESP32
        self.status_topic = f"wastebin/{self.bin_id}/status"
        self.command_topic = f"wastebin/{self.bin_id}/command"

    def on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            print(f"[MQTT] Đã kết nối tới broker {self.broker}:{self.port}")
            self.client.subscribe(self.command_topic)
            print(f"[MQTT] Đã theo dõi topic: {self.command_topic}")
        else:
            print(f"[MQTT] Lỗi kết nối. Mã lỗi: {rc}")

    def on_message(self, client, userdata, msg):
        if not msg.payload or len(msg.payload) == 0:
            return # Gói tin xóa retain từ broker
        try:
            payload_str = msg.payload.decode("utf-8")
            doc = json.loads(payload_str)
            action = doc.get("action", "").upper()
            command_id = doc.get("commandId", "")
            
            print(f"\n[MQTT] Nhận được lệnh điều khiển: {action} (ID: {command_id})")
            
            # Xử lý lệnh theo logic trong main.cpp
            if action == "OPEN":
                self.controlMode = "MANUAL"
                self.servoAngle = 90
                self.state = "OPEN"
            elif action == "CLOSE":
                self.controlMode = "MANUAL"
                self.servoAngle = 0
                self.state = "CLOSED"
            elif action == "AUTO":
                self.collectionPaused = False
                self.controlMode = "AUTO"
                self.servoAngle = 0
                self.state = "CLOSED"
            elif action == "MANUAL":
                self.controlMode = "MANUAL"
            elif action == "PAUSE":
                self.collectionPaused = True
                self.controlMode = "MANUAL"
                self.servoAngle = 0
                self.state = "CLOSED"
            elif action == "RESUME":
                self.collectionPaused = False
                self.controlMode = "AUTO"
                self.servoAngle = 0
                self.state = "CLOSED"
            else:
                print(f"[MQTT] Không hiểu lệnh: {action}")
                return
                
            self.lastCommandId = command_id
            self.lastCommandAction = action
            self.publish_status()
            
        except Exception as e:
            print(f"[MQTT] Lỗi khi xử lý gói tin JSON: {e}")

    def publish_status(self):
        doc = {
            "state": self.state,
            "distUser": round(self.distUser, 1),
            "distLevel": round(self.distLevel, 1),
            "levelPercent": round(self.levelPercent, 1),
            "servoAngle": self.servoAngle,
            "controlMode": self.controlMode,
            "collectionPaused": self.collectionPaused,
            "ipAddress": self.ipAddress,
            "name": self.name,
            "location": self.location
        }
        
        if self.lastCommandId:
            doc["commandAckId"] = self.lastCommandId
            doc["commandAckAction"] = self.lastCommandAction
            
        payload = json.dumps(doc)
        self.client.publish(self.status_topic, payload)
        
        # Log giống như hàm printSingleLineLog()
        level_str = "PAUSED      " if self.collectionPaused else f"{self.distLevel:4.1f}cm ({self.levelPercent:2.0f}%)"
        print(f"[STATE: {self.state:10s}] | [S1_USER: {self.distUser:4.1f}cm] | [S2_LEVEL: {level_str}] | [SERVO: {self.servoAngle:2d} deg]")

    def start(self):
        print(f"Đang kết nối tới MQTT broker {self.broker}:{self.port}...")
        try:
            self.client.connect(self.broker, self.port, 60)
            self.client.loop_start()
        except Exception as e:
            print(f"Không thể kết nối MQTT broker: {e}")
            print("Vui lòng đảm bảo MQTT Broker (Mosquitto) đang chạy hoặc truyền đúng địa chỉ broker.")
            return

        print("\n=================== HỆ THỐNG GIẢ LẬP ĐÃ SẴN SÀNG ===================")
        print(f"Bin ID (Fake MAC): {self.bin_id}")
        print("Nhấn Ctrl+C để dừng mô phỏng")
        print("====================================================================\n")
        
        try:
            while self.running:
                self.simulate_sensors()
                self.publish_status()
                time.sleep(1) # Gửi data mỗi 1 giây giống ESP32 (LOG_INTERVAL_MS = 1000)
        except KeyboardInterrupt:
            print("\nĐang dừng hệ thống giả lập...")
        finally:
            self.running = False
            self.client.loop_stop()
            self.client.disconnect()

    def simulate_sensors(self):
        # Nếu đang tạm ngưng thu gom thì không đo đạc
        if not self.collectionPaused:
            
            # 1. Mô phỏng mức rác đầy lên từ từ
            if self.state == "CLOSED" and self.levelPercent < 100:
                self.levelPercent += random.uniform(0.1, 0.8) # Mức rác tăng ngẫu nhiên
                if self.levelPercent > 100:
                    self.levelPercent = 100.0
                
                # Cập nhật khoảng cách dựa trên mức rác (%)
                # BIN_DEPTH_EMPTY_CM = 40, BIN_DEPTH_FULL_CM = 5 => 100% = 5cm, 0% = 40cm
                self.distLevel = 40.0 - (self.levelPercent / 100.0 * 35.0)
                
            # 2. Mô phỏng người dùng tới gần vứt rác (chỉ khi đang AUTO mode)
            if self.controlMode == "AUTO":
                if self.state == "CLOSED":
                    # Xác suất 10% có người bước tới
                    if random.random() < 0.1:
                        self.distUser = random.uniform(10.0, 25.0)
                        self.state = "CONFIRMING"
                        print("[SIMULATION] Có người đi tới thùng rác...")
                    else:
                        self.distUser = 100.0 # Không có ai
                        
                elif self.state == "CONFIRMING":
                    # Chờ đủ lâu -> chuyển sang OPEN
                    self.state = "OPEN"
                    self.servoAngle = 90
                    print("[SIMULATION] Đang mở nắp...")
                    
                elif self.state == "OPEN":
                    # Ở trạng thái OPEN một vài giây rồi đóng nắp
                    if random.random() < 0.4: # Khoảng 40% cơ hội đóng mỗi giây (mô phỏng AUTO_CLOSE_MS)
                        self.state = "CLOSED"
                        self.servoAngle = 0
                        self.distUser = 100.0
                        print("[SIMULATION] Đã đóng nắp...")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Trình giả lập thùng rác thông minh (ESP32 Simulator)")
    parser.add_argument("--broker", default="localhost", help="Địa chỉ MQTT Broker IP (mặc định: localhost)")
    parser.add_argument("--port", type=int, default=1883, help="Cổng MQTT Broker (mặc định: 1883)")
    parser.add_argument("--id", default="FAKE_MAC_123456", help="ID Thùng rác (Fake MAC Address)")
    parser.add_argument("--name", default="Thùng Rác Giả Lập 1", help="Tên hiển thị của thùng rác")
    parser.add_argument("--location", default="Sảnh A - Tầng 1", help="Vị trí của thùng rác")
    
    args = parser.parse_args()
    
    sim = MockSmartBin(args.broker, args.port, args.id, args.name, args.location)
    sim.start()
