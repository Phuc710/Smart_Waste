# 🌍 Hệ Sinh Thái Quản Trị & Thu Gom Rác Thông Minh SmartWaste
## (SmartWaste Urban IoT, Server & Mobile Driver Ecosystem)

> **Nền tảng Đô thị Thông minh Toàn diện — Smart City Waste Management Platform**  
> Bao gồm: Trạm Cảm biến IoT (ESP32), Máy chủ Điều phối Đa giao thức (Node.js/Express, MQTT, Socket.IO, Supabase), Bảng Điều khiển Web Admin (React 19, Leaflet GIS) và Ứng dụng Di động Dẫn đường dành cho Tài xế (Android Native Kotlin).

---

## 📚 TRUNG TÂM TÀI LIỆU KỸ THUẬT (DOCUMENTATION HUB)

Toàn bộ tài liệu kỹ thuật chuyên sâu chuẩn Senior đã được chuẩn hóa và gom vào thư mục **[`docs/`](file:///c:/Users/Phucx/Downloads/waste/docs/README.md)**:

| Phân Hệ | Đường Dẫn Tài Liệu Chi Tiết | Mô Tả Trọng Tâm |
| :--- | :--- | :--- |
| **📘 Server (Backend + Frontend)** | 👉 **[docs/server/README.md](file:///c:/Users/Phucx/Downloads/waste/docs/server/README.md)** | • Máy chủ Node.js/Express 5.x, Aedes MQTT Broker :1883, Socket.IO :3000.<br>• Supabase PostgreSQL (11 bảng, 18+ RPCs ACID, OCC Versioning).<br>• Đặc tả 100% RESTful APIs, 13 WebSocket Events, Background Workers.<br>• Web Admin Dashboard React 19 SPA, Leaflet GIS, Design Tokens. |
| **📱 Mobile App (Android Native)** | 👉 **[docs/mobile/README.md](file:///c:/Users/Phucx/Downloads/waste/docs/mobile/README.md)** | • Android Native Kotlin (SDK 34), Clean Architecture + MVI/MVVM.<br>• Dẫn đường **Heading-Up / Course-Up Turn-by-Turn** neo xe 72% chiều cao.<br>• Thuật toán Anti-Spin 360°, Dual-Stage Fallback, Tự động Reroute >65m.<br>• Quét Radar 500m Self-Pick, Mở nắp thùng IoT, Offline-First Service. |

---

## 📁 Cấu Trúc Thư Mục Dự Án

```
waste/
├── docs/                          ⭐ TRUNG TÂM TÀI LIỆU KỸ THUẬT CHUẨN SENIOR
│   ├── README.md                  # Chỉ mục điều hướng & sơ đồ hệ sinh thái
│   ├── server/
│   │   └── README.md              # Tài liệu Toàn diện Server (Backend + Frontend Web)
│   └── mobile/
│       └── README.md              # Tài liệu Toàn diện Mobile App (Android Native Driver)
│
├── server/                        <-- TOÀN BỘ HỆ THỐNG MÁY CHỦ & WEB ADMIN
│   ├── package.json               # Workspaces Root (Scripts: start, server, client, build)
│   ├── supabase_schema.sql        # DDL CSDL, Triggers, 18+ Stored Procedures (RPCs)
│   ├── backend/                   # Node.js Server (REST API, MQTT Broker :1883, Socket.IO :3000)
│   └── frontend/                  # React 19 SPA (Leaflet OpenStreetMap GIS Web Admin)
│
└── App_Smart_Waste/               <-- ỨNG DỤNG DI ĐỘNG TÀI XẾ THU GOM (ANDROID NATIVE)
    ├── app/src/main/              # Mã nguồn Kotlin, WebView GIS Engine, Layouts, Drawables
    ├── build.gradle.kts           # Cấu hình Gradle dependencies
    └── gradlew                    # Gradle Wrapper
```

---

## 🚀 Hướng Dẫn Khởi Chạy Toàn Bộ Hệ Thống

### 1. Khởi chạy Máy chủ Backend & Giao diện Web Admin

```powershell
# Di chuyển vào thư mục server
cd server

# Cài đặt tất cả dependencies (Root, Backend, Frontend)
npm run install:all

# Chế độ Lập trình (Development Mode: Backend :3000 + Frontend Vite :5173 Hot-Reload)
npm run server   # Terminal 1: Chạy Backend & MQTT Broker
npm run client   # Terminal 2: Chạy Frontend Vite Web Admin

# Chế độ Sản phẩm (Production Mode: Build React SPA + Single Express Process)
npm run build
npm start
```
*Mở trình duyệt truy cập Web Admin tại:* `http://localhost:3000` (hoặc `http://localhost:5173` trong chế độ Dev).

---

### 2. Khởi chạy Ứng dụng Di động Android Driver

```powershell
# Di chuyển vào thư mục Android App
cd App_Smart_Waste

# Build bản cài đặt Debug APK
./gradlew assembleDebug

# Cài đặt trực tiếp lên điện thoại / máy ảo Android (qua USB/ADB)
./gradlew installDebug
```

---

## 👥 Bản Quyền & Phát Triển
- **Dự Án**: SmartWaste Urban IoT & Fleet Management Platform.
- **Bản Quyền**: © 2026 SmartWaste Team. All Rights Reserved.