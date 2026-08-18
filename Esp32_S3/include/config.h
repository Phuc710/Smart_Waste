#ifndef CONFIG_H
#define CONFIG_H

// =========================================================
// 1. CẤU HÌNH CHÂN GPIO
// =========================================================
// CẢNH BÁO AN TOÀN PHẦN CỨNG (HARDWARE SAFETY):
// - HC-SR04 cấp nguồn 5V, chân ECHO xuất mức 5V.
// - BẮT BUỘC dùng cầu phân áp (1kΩ nối tiếp từ ECHO đến GPIO, 2kΩ từ GPIO xuống GND)
//   để hạ áp 5V -> 3.33V an toàn cho chân GPIO ESP32 (tối đa 3.6V).
// - Động cơ Servo BẮT BUỘC dùng nguồn ngoài 5V/2A riêng biệt, chung mass GND với ESP32,
//   kèm tụ lọc 220uF-470uF chống sụt áp brownout reset.
#define PIN_TRIG_USER    5      // Sensor 1: Phát hiện người (Trig 3.3V)
#define PIN_ECHO_USER    18     // Sensor 1: Echo qua cầu phân áp 1k/2k -> 3.3V

#define PIN_TRIG_LEVEL   19     // Sensor 2: Đo lượng rác (Trig 3.3V)
#define PIN_ECHO_LEVEL   21     // Sensor 2: Echo qua cầu phân áp 1k/2k -> 3.3V

#define PIN_SERVO        13     // Chân tín hiệu PWM điều khiển Servo

// Nút nhấn để xóa cấu hình WiFi/MQTT và vào chế độ cài đặt lại
// GPIO 0 = nút BOOT có sẵn trên hầu hết board ESP32 (không cần nối thêm dây!)
#define PIN_RESET_CONFIG 0
#define RESET_HOLD_MS    3000   // Giữ 3 giây để reset

// =========================================================
// 2. CẤU HÌNH KHOẢNG CÁCH (CM)
// =========================================================
const float USER_DETECT_DIST_CM = 30.0;    // Ngưỡng phát hiện người (cm)
const float BIN_DEPTH_EMPTY_CM  = 40.0;    // Đáy thùng rác (cm)
const float BIN_DEPTH_FULL_CM   = 5.0;     // Mức rác đầy (cm)

// =========================================================
// 3. CẤU HÌNH THỜI GIAN (MILLISECONDS)
// =========================================================
const uint32_t USER_CONFIRM_MS  = 2000;    // Phải ở đúng khoảng cách liên tục 2 giây mới mở nắp
const uint32_t AUTO_CLOSE_MS    = 3000;    // Thời gian giữ nắp mở trước khi đóng (ms)

// *** CHỈNH TỐC ĐỘ XUẤT LOG TẠI ĐÂY ***
const uint32_t LOG_INTERVAL_MS  = 1000;    // Chu kỳ in Log (1000ms = 1 giây/lần cho dễ đọc)

// =========================================================
// 4. CẤU HÌNH SERVO
// =========================================================
const int SERVO_POS_CLOSED      = 0;       // Góc đóng nắp
const int SERVO_POS_OPEN        = 90;      // Góc mở nắp

#endif // CONFIG_H
