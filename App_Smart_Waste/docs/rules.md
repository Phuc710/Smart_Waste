# 📐 SMARTWASTE MOBILE — ARCHITECTURE & SECURITY RULES

> **Source of Truth for Android Engineering Standards, Security, and Code Quality**

---

## 1. Authentication & Session Security (OWASP Standard)

1. **Không lưu trữ nhạy cảm (Zero Plaintext Policy)**:
   - Tuyệt đối không lưu mật khẩu người dùng vào thiết bị.
   - Token xác thực (`accessToken`) PHẢI được lưu trữ trong `EncryptedSharedPreferences` được mã hóa bằng **Android Keystore (AES-256-GCM / MasterKey)**.
2. **Xác thực phiên làm việc khi mở ứng dụng (Startup Session Validation)**:
   - Khi ứng dụng khởi động, nếu có token được lưu, tiến hành gọi `GET /api/auth/me`.
   - Kiểm tra trường `isActive`:
     - Nếu `isActive == true`: Xác thực thành công $\rightarrow$ Điều hướng vào **Home (MainActivity)**.
     - Nếu `isActive == false`: Tài khoản bị khóa $\rightarrow$ Xóa token trong Keystore, hiển thị thông báo lỗi rõ ràng: *"Tài khoản đã bị khóa bởi Quản trị viên"* $\rightarrow$ Chuyển về màn hình **Login**.
     - Nếu token hết hạn (HTTP 401): Xóa session $\rightarrow$ Chuyển về **Login**.
3. **Mọi Request xác thực**:
   - Header bắt buộc: `Authorization: Bearer <token>`.

---

## 2. Kiến Trúc Mã Nguồn (Clean MVVM Architecture)

```
UI (Activity / Fragment / ViewBinding)
  └── StateFlow / LiveData Observe
ViewModel (Quản lý UiState, Coroutine Scope)
  └── Suspend Functions
Repository (Single Source of Truth, Caching, Session)
  ├── RemoteDataSource (Retrofit ApiService / OkHttp)
  └── LocalDataSource (EncryptedSharedPreferences / In-Memory Cache)
```

1. **Quy tắc View (UI Layer)**:
   - View (Activity/Fragment) CHỈ chịu trách nhiệm hiển thị giao diện, lắng nghe sự kiện bấm của người dùng và render trạng thái `UiState`.
   - KHÔNG chứa logic nghiệp vụ, KHÔNG gọi trực tiếp Retrofit.
2. **Quy tắc ViewModel**:
   - Quản lý `UiState` dạng `sealed interface` rõ ràng (`Loading`, `Success`, `Error`, `Empty`).
   - KHÔNG lưu trữ tham chiếu tới `Context`, `View` hoặc `Activity` để tránh rò rỉ bộ nhớ (Memory Leak).
3. **Xử lý trạng thái mạng**:
   - Tất cả các màn hình phải xử lý đầy đủ 5 trạng thái: `Loading`, `Content / Success`, `Empty`, `Error (Có nút Thử lại)`, `Offline`.

---

## 3. Quản Lý Vị Trí & GPS (Location Guidelines)

1. **Cấp quyền theo ngữ cảnh (Just-in-Time Permissions)**:
   - KHÔNG xin quyền GPS ở màn hình Splash.
   - Chỉ xin quyền khi vào màn hình Bản đồ / Bắt đầu tuyến thu gom hoặc khi cần gửi vị trí GPS xe.
2. **Điều tốc phát sóng GPS (Throttling)**:
   - Vị trí xe (`latitude`, `longitude`, `speed`, `heading`, `accuracy`) được gửi định kỳ `5s - 10s` một lần qua `POST /api/location` khi tài xế đang làm nhiệm vụ.
   - Khi tài xế ở màn hình chờ hoặc dừng ứng dụng, dừng gửi GPS để tiết kiệm pin.

---

## 4. Giao Tiếp Bản Đồ & Định Tuyến (100% Free GIS Engine)

1. **Bản đồ mở OpenStreetMap (OSM)**:
   - Dùng Tile Server miễn phí của OpenStreetMap standard `https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png`.
   - Không phụ thuộc Google Maps API Key hay phát sinh chi phí duy trì.
2. **Bộ định tuyến OSRM**:
   - Backend tính toán tuyến đường tối ưu qua OSRM và trả về danh sách tọa độ polyline, khoảng cách (km), thời gian dự kiến (phút).
   - Mobile chỉ việc vẽ polyline màu xanh `#2563eb`, hiển thị thứ tự điểm dừng (Numbered Waypoints) và điều hướng.
