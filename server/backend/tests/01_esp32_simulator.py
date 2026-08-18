#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
SMARTWASTE — TRÌNH GIẢ LẬP PHẦN CỨNG IOT ESP32 S3 (PYTHON SIMULATOR)
Tương thích 100% Firmware C++ (Esp32_S3/src/main.cpp)
Hỗ trợ 2-Way Handshake ACK, Cảm biến siêu âm, Góc Servo, Cảnh báo quá tải
=============================================================================
"""

import sys
import os
import time
import json
import argparse
import subprocess
import threading

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

# Tự động cài đặt paho-mqtt nếu môi trường chưa có
try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("[SETUP] Đang cài đặt thư viện 'paho-mqtt' tự động...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "paho-mqtt<2.0.0"])
    import paho.mqtt.client as mqtt

# ANSI Color Codes
C_RESET = "\033[0m"
C_BRIGHT = "\033[1m"
C_DIM = "\033[2m"
C_GREEN = "\033[32m"
C_RED = "\033[31m"
C_YELLOW = "\033[33m"
C_CYAN = "\033[36m"
C_BLUE = "\033[34m"
C_MAGENTA = "\033[35m"


class ESP32Simulator:
    def __init__(self, broker_host="127.0.0.1", broker_port=1883, bin_id="BIN_001",
                 name="Thùng rác Phố Đi Bộ Nguyễn Huệ", location="Số 22 Nguyễn Huệ, Q.1",
                 initial_level=45.0, delay_ack=0.0, no_ack=False):
        self.broker_host = broker_host
        self.broker_port = broker_port
        self.bin_id = bin_id
        self.name = name
        self.location = location
        self.level_percent = float(initial_level)
        self.delay_ack = float(delay_ack)
        self.no_ack = bool(no_ack)

        # Hardware State Machine
        self.state = "CLOSED"  # CLOSED, CONFIRMING, OPEN
        self.control_mode = "AUTO"  # AUTO, MANUAL
        self.servo_angle = 0  # 0: CLOSED, 90: OPEN
        self.dist_user = 85.0
        self.dist_level = 42.0
        self.collection_paused = False
        self.ip_address = "192.168.1.105"

        # 2-Way Handshake ACK Variables
        self.last_command_id = ""
        self.last_command_action = ""
        self.recent_commands = []
        self.recent_capacity = 16

        # MQTT Client setup
        self.client = mqtt.Client(client_id=self.bin_id)
        self.client.on_connect = self.on_connect
        self.client.on_disconnect = self.on_disconnect
        self.client.on_message = self.on_message

        self.running = False
        self.lock = threading.Lock()

    def on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            print(f"{C_GREEN}✔ [ESP32 #{self.bin_id}] Đã kết nối MQTT Broker ({self.broker_host}:{self.broker_port}){C_RESET}")
            cmd_topic = f"wastebin/{self.bin_id}/command"
            ota_topic = f"wastebin/{self.bin_id}/ota"
            client.subscribe(cmd_topic, qos=1)
            client.subscribe(ota_topic, qos=1)
            print(f"  {C_DIM}Subscribed: {cmd_topic} & {ota_topic}{C_RESET}")
            # Gửi gói status chào mừng ngay lập tức
            self.publish_status()
        else:
            print(f"{C_RED}✖ [ESP32 #{self.bin_id}] Lỗi kết nối MQTT: RC = {rc}{C_RESET}")

    def on_disconnect(self, client, userdata, rc):
        print(f"{C_YELLOW}⚠ [ESP32 #{self.bin_id}] Đã ngắt kết nối MQTT Broker (RC = {rc}){C_RESET}")

    def on_message(self, client, userdata, msg):
        payload_str = msg.payload.decode("utf-8", errors="ignore").strip()
        if not payload_str:
            return

        topic = msg.topic
        print(f"\n{C_MAGENTA}⚡ [MQTT IN] Nhận tin từ topic: {topic}{C_RESET}")
        print(f"  {C_DIM}Payload: {payload_str}{C_RESET}")

        try:
            doc = json.loads(payload_str)
        except Exception as e:
            print(f"  {C_RED}✖ Lỗi parse JSON command: {e}{C_RESET}")
            return

        action = str(doc.get("action", "")).upper()
        command_id = str(doc.get("commandId", ""))

        if self.no_ack:
            print(f"  {C_YELLOW}⚠ [SIMULATOR] Chế độ NO-ACK bật: Bỏ qua không phản hồi lệnh!{C_RESET}")
            return

        def process_cmd():
            if self.delay_ack > 0:
                print(f"  {C_YELLOW}⏳ [SIMULATOR] Trễ ACK cố ý {self.delay_ack}s để test Timeout 504...{C_RESET}")
                time.sleep(self.delay_ack)

            with self.lock:
                # Chống lặp lệnh (Anti-Replay Protection)
                if command_id and (command_id == self.last_command_id or command_id in self.recent_commands):
                    print(f"  {C_YELLOW}⚠ Phát hiện lệnh trùng lặp ({command_id}), chỉ gửi lại ACK: {action}{C_RESET}")
                    self.last_command_id = command_id
                    self.last_command_action = action
                    self.publish_status()
                    return

                if action in ("OPEN", "OPEN_LID"):
                    self.control_mode = "MANUAL"
                    self.servo_angle = 90
                    self.state = "OPEN"
                    print(f"  {C_GREEN}⚙ [ACTUATOR] Động cơ Servo quay 90° -> NẮP ĐÃ MỞ (OPEN){C_RESET}")
                elif action in ("CLOSE", "CLOSE_LID"):
                    self.control_mode = "MANUAL"
                    self.servo_angle = 0
                    self.state = "CLOSED"
                    print(f"  {C_GREEN}⚙ [ACTUATOR] Động cơ Servo quay 0° -> NẮP ĐÃ ĐÓNG (CLOSED){C_RESET}")
                elif action == "AUTO":
                    self.collection_paused = False
                    self.control_mode = "AUTO"
                    self.servo_angle = 0
                    self.state = "CLOSED"
                    print(f"  {C_GREEN}⚙ [ACTUATOR] Chuyển chế độ TỰ ĐỘNG (AUTO MODE){C_RESET}")
                elif action == "MANUAL":
                    self.control_mode = "MANUAL"
                    print(f"  {C_GREEN}⚙ [ACTUATOR] Chuyển chế độ THỦ CÔNG (MANUAL MODE){C_RESET}")
                elif action == "PAUSE":
                    self.collection_paused = True
                    self.control_mode = "MANUAL"
                    self.servo_angle = 0
                    self.state = "CLOSED"
                    print(f"  {C_GREEN}⚙ [ACTUATOR] TẠM DỪNG THU GOM (COLLECTION PAUSED){C_RESET}")
                elif action == "RESUME":
                    self.collection_paused = False
                    self.control_mode = "AUTO"
                    self.servo_angle = 0
                    self.state = "CLOSED"
                    print(f"  {C_GREEN}⚙ [ACTUATOR] TIẾP TỤC THU GOM (COLLECTION RESUMED){C_RESET}")
                else:
                    print(f"  {C_RED}✖ Lệnh không xác định: {action}{C_RESET}")
                    return

                self.last_command_id = command_id
                self.last_command_action = action
                if command_id:
                    self.recent_commands.append(command_id)
                    if len(self.recent_commands) > self.recent_capacity:
                        self.recent_commands.pop(0)

                print(f"  {C_CYAN}✔ [2-WAY ACK] Đã thực thi lệnh '{action}' (ID: {command_id}){C_RESET}")
                self.publish_status()

        threading.Thread(target=process_cmd, daemon=True).start()

    def publish_status(self):
        with self.lock:
            payload = {
                "deviceId": self.bin_id,
                "state": self.state,
                "controlMode": self.control_mode,
                "servoAngle": self.servo_angle,
                "distUser": round(self.dist_user, 1),
                "distLevel": round(self.dist_level, 1),
                "levelPercent": round(self.level_percent, 1),
                "collectionPaused": self.collection_paused,
                "ipAddress": self.ip_address,
                "name": self.name,
                "location": self.location
            }
            if self.last_command_id:
                payload["commandAckId"] = self.last_command_id
                payload["commandAckAction"] = self.last_command_action
                # Xóa sau khi bắn ACK 1 lần để các heartbeat kế tiếp không gửi lại
                self.last_command_id = ""
                self.last_command_action = ""

        topic = f"wastebin/{self.bin_id}/status"
        payload_json = json.dumps(payload, ensure_ascii=False)
        self.client.publish(topic, payload_json, qos=0)

    def start(self, interval=3.0, duration=0):
        print(f"\n{C_CYAN}╔═══════════════════════════════════════════════════════════════════════╗{C_RESET}")
        print(f"{C_CYAN}║   {C_BRIGHT}SMARTWASTE — ESP32 HARDWARE SIMULATOR #{self.bin_id:<18}{C_RESET}{C_CYAN}║{C_RESET}")
        print(f"{C_CYAN}║   Mức rác: {self.level_percent}% | Trạng thái: {self.state:<6} | Servo: {self.servo_angle}°             ║{C_RESET}")
        print(f"{C_CYAN}╚═══════════════════════════════════════════════════════════════════════╝{C_RESET}\n")

        self.running = True
        try:
            self.client.connect(self.broker_host, self.broker_port, 60)
            self.client.loop_start()
        except Exception as e:
            print(f"{C_RED}✖ Không thể kết nối tới MQTT Broker tại {self.broker_host}:{self.broker_port}: {e}{C_RESET}")
            return

        start_time = time.time()
        count = 0
        try:
            while self.running:
                time.sleep(interval)
                count += 1
                self.publish_status()
                print(f"{C_DIM}[Heartbeat #{count}] Đã gửi telemetry #{self.bin_id}: {self.level_percent}% | Nắp: {self.state} ({self.servo_angle}°){C_RESET}")

                if duration > 0 and (time.time() - start_time) >= duration:
                    print(f"\n{C_GREEN}✔ Đã hết thời gian mô phỏng ({duration}s). Dừng simulator.{C_RESET}")
                    break
        except KeyboardInterrupt:
            print(f"\n{C_YELLOW}Dừng ESP32 Simulator theo yêu cầu người dùng.{C_RESET}")
        finally:
            self.stop()

    def stop(self):
        self.running = False
        try:
            self.client.loop_stop()
            self.client.disconnect()
        except Exception:
            pass


def main():
    parser = argparse.ArgumentParser(description="SmartWaste ESP32 S3 Python Simulator")
    parser.add_argument("--bin-id", default="BIN_001", help="Mã thùng rác (Mặc định: BIN_001)")
    parser.add_argument("--host", default="127.0.0.1", help="Địa chỉ MQTT Broker (Mặc định: 127.0.0.1)")
    parser.add_argument("--port", type=int, default=1883, help="Cổng MQTT Broker (Mặc định: 1883)")
    parser.add_argument("--level", type=float, default=45.0, help="Mức rác ban đầu (%)")
    parser.add_argument("--interval", type=float, default=3.0, help="Chu kỳ gửi viễn trắc (giây)")
    parser.add_argument("--duration", type=int, default=0, help="Thời gian chạy (giây, 0 = liên tục)")
    parser.add_argument("--delay-ack", type=float, default=0.0, help="Trễ gửi ACK (giây, dùng để test timeout)")
    parser.add_argument("--no-ack", action="store_true", help="Không gửi gói tin ACK (dùng để test timeout 504)")

    args = parser.parse_args()

    sim = ESP32Simulator(
        broker_host=args.host,
        broker_port=args.port,
        bin_id=args.bin_id,
        initial_level=args.level,
        delay_ack=args.delay_ack,
        no_ack=args.no_ack
    )
    sim.start(interval=args.interval, duration=args.duration)


if __name__ == "__main__":
    main()
