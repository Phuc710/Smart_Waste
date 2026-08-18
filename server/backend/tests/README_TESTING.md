# 🧪 SỔ TAY HƯỚNG DẪN KIỂM THỬ HỆ THỐNG SMARTWASTE

Tài liệu hướng dẫn chi tiết quy trình kiểm thử toàn diện hệ thống **SmartWaste (Backend, MQTT Broker, Hardware ESP32 & Mobile App)** theo chuẩn kiến trúc Senior QA/DevOps: **1 File = 1 Chức năng**, log trực quan, phân màu rõ ràng và an toàn dữ liệu.

---

## 📁 Cấu Trúc Bộ Kiểm Thử (`server/backend/tests/`)

| File Test | Loại | Chức năng chính | Thời gian |
| :--- | :--- | :--- | :--- |
| **`00_clean_and_seed_data.js`** | Database | Dọn dẹp sạch CSDL Supabase & nạp bộ dữ liệu chuẩn (Admin, 3 Drivers, 6 Thùng rác Q.1 TP.HCM, Ca gom mẫu). | ~2s |
| **`01_esp32_simulator.py`** | Hardware | Trình giả lập vi điều khiển ESP32 S3 bằng Python (Telemetry 3s, lắng nghe MQTT, xoay Servo 90°/0°, trả 2-Way ACK). | Tuỳ chọn |
| **`02_test_mobile_notifications.js`** | Realtime | Kiểm thử 4 loại thông báo Mobile qua Socket.IO: Phân công ca, Hủy ca, Rác đầy $\ge 85\%$, Báo cáo sự cố. | ~3s |
| **`03_test_lid_open_ack.js`** | Handshake | Kiểm thử chu trình bấm Mở / Đóng nắp từ Mobile $\to$ Server $\to$ ESP32 $\to$ 2-Way ACK $(<4.5\text{s})$. | ~2s |
| **`04_test_failure_and_security.js`** | Security | Kiểm thử 5 ca lỗi: Timeout 504 khi ESP32 tắt nguồn, Zero-Trust RBAC 403, 401 Unauthorized, 400 Bad Request, Admin Override. | ~6s |
| **`05_test_nearest_driver_dispatch.js`** | Algorithm | Kiểm thử thuật toán tính khoảng cách Haversine & tự động gợi ý tài xế rảnh rỗi gần nhất khi thùng rác quá tải. | ~2s |
| **`run_all_tests.js`** | Master Suite | Bộ điều phối chạy toàn bộ chuỗi test và xuất bảng tổng kết ANSI. | ~15s |

---

## 🚀 1. Hướng Dẫn Chạy Toàn Bộ Chuỗi Test (Master Test Runner)

Chạy 1 lệnh duy nhất để kiểm tra toàn diện 100% tính năng backend:

```bash
cd server/backend
node tests/run_all_tests.js
```

---

## 🎯 2. Hướng Dẫn Chạy Từng Bài Test Độc Lập

### 🔹 Bước 0: Dọn dẹp & nạp CSDL mẫu chuẩn
```bash
node tests/00_clean_and_seed_data.js
```
> **Dữ liệu được nạp:**
> - **Admin**: `admin` / `Password123!` (admin@smartwaste.vn)
> - **Driver 1**: `driver1` / `Password123!` (Đang có ca `JOB_HCM_001` gồm `BIN_001` và `BIN_003`)
> - **Driver 2**: `driver2` / `Password123!` (Rảnh rỗi, gần Q.1)
> - **Driver 3**: `driver3` / `Password123!` (Rảnh rỗi)
> - **6 Thùng rác**: `BIN_001` (Phố Đi Bộ), `BIN_002` (Nhà Thờ Đức Bà), `BIN_003` (Chợ Bến Thành), `BIN_004` (Thảo Cầm Viên), `BIN_005` (Bạch Đằng), `BIN_006` (Công viên 23/9 - Offline).

---

### 🔹 Bước 1: Giả lập phần cứng ESP32 (Python Simulator)
Mô phỏng vi điều khiển ESP32 S3 thật kết nối tới MQTT Broker:

```bash
# Chạy thùng BIN_001 bình thường (mức rác 45%, chu kỳ 3s)
python tests/01_esp32_simulator.py --bin-id BIN_001 --level 45

# Giả lập thùng BIN_002 bị đầy rác 92% (kích hoạt Overfill Alert)
python tests/01_esp32_simulator.py --bin-id BIN_002 --level 92

# Giả lập thiết bị lỗi bị đơ (không phản hồi ACK để test 504 Timeout)
python tests/01_esp32_simulator.py --bin-id BIN_001 --no-ack
```

---

### 🔹 Bước 2: Kiểm thử Thông báo Mobile Realtime
Kiểm tra xem Mobile App có nhận đúng sự kiện Socket.IO khi Admin phân công hoặc thùng rác quá tải hay không:

```bash
node tests/02_test_mobile_notifications.js
```

---

### 🔹 Bước 3: Kiểm thử Bấm Mở Nắp & Nhận 2-Way Handshake ACK
Kiểm tra chu trình: Tài xế bấm **"MỞ NẮP"** trên App $\to$ Server gửi MQTT $\to$ ESP32 quay Servo 90° $\to$ ESP32 trả `commandAckId` $\to$ Mobile nhận kết quả trong $<1\text{s}$:

```bash
node tests/03_test_lid_open_ack.js
```

---

### 🔹 Bước 4: Kiểm thử Ca Lỗi & Bảo Mật Zero-Trust
Kiểm tra các kịch bản tiêu cực (Negative Testing):

```bash
node tests/04_test_failure_and_security.js
```
- **Test 1**: Thùng rác tắt nguồn $\to$ Bấm mở nắp server đợi đúng 4.5s trả `504 Gateway Timeout`.
- **Test 2**: Tài xế 2 cố tình mở thùng của Tài xế 1 $\to$ Server trả `403 Forbidden` (Zero-Trust RBAC).
- **Test 3**: Gọi API không có Token $\to$ Trả `401 Unauthorized`.
- **Test 4**: Gửi action rác `EXPLODE` $\to$ Trả `400 Bad Request`.
- **Test 5**: Admin luôn có quyền can thiệp toàn bộ thùng rác.

---

### 🔹 Bước 5: Kiểm thử Thuật toán Gợi ý Xe Gần Nhất (Haversine)
Kiểm tra thuật toán điều phối thông minh khi có thùng rác bị quá tải:

```bash
node tests/05_test_nearest_driver_dispatch.js
```

---

## 📱 3. Hướng Dẫn Test Trực Tiếp Bằng Mobile App Thật (Android)

1. **Khởi động Backend Server**:
   ```bash
   cd server/backend
   node server.js
   ```
2. **Khởi động ESP32 Simulator**:
   ```bash
   python tests/01_esp32_simulator.py --bin-id BIN_001
   ```
3. **Mở App Android**:
   - Đăng nhập với tài khoản `driver1` / `Password123!`.
   - Vào mục **"Nhiệm vụ thu gom"** $\to$ Thấy ca gom `JOB_HCM_001`.
   - Chọn thùng rác `BIN_001` (Phố Đi Bộ Nguyễn Huệ) $\to$ Bấm nút **"MỞ NẮP"**.
   - Quan sát màn hình terminal của Python Simulator: Bạn sẽ thấy log Servo quay 90° và nắp mở, đồng thời trên điện thoại nắp chuyển sang màu xanh **OPEN** tức thì!
