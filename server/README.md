# SmartWaste Web App (Backend & Frontend)

Hệ thống Quản trị & Điều phối Thu gom Rác Thông minh (Web Admin Dashboard & MQTT/Socket.IO Server).

---

## 📁 Cấu Trúc Thư Mục

```
web/
├── backend/                       <-- Dịch vụ Máy chủ & Xử lý Dữ liệu
│   ├── server.js                  (Express API, MQTT Broker cổng 1883, Socket.IO, Supabase Sync)
│   ├── jobsDb.js                  (Tiện ích tương tác CSDL Jobs)
│   ├── seed_vietnam_data.js       (Script nạp dữ liệu mẫu TP.HCM)
│   ├── supabase_schema.sql        (Schema CSDL PostgreSQL cho Supabase)
│   ├── .env                       (Cấu hình biến môi trường)
│   └── package.json
│
├── frontend/                      <-- Giao diện Quản trị Web (SPA React)
│   ├── public/                    (Logo, favicon, hình ảnh tĩnh)
│   ├── src/                       (React 19, Leaflet Map, Điều khiển nắp, Quản lý Nhân viên)
│   ├── index.html
│   ├── vite.config.js             (Vite 8 Dev Proxy tới Backend)
│   └── package.json
│
└── package.json                   <-- Quản lý Workspaces & Scripts tổng
```

---

## 🚀 Hướng Dẫn Chạy Dự Án

### 1. Khởi động Toàn Bộ Hệ Thống (Production Mode)

1. Build giao diện Frontend:
   ```powershell
   npm run build
   ```
2. Khởi chạy Backend Server:
   ```powershell
   npm start
   ```
3. Mở trình duyệt tại: `http://localhost:3000`

---

### 2. Chế Độ Lập Trình (Development Mode)

Chạy đồng thời hoặc độc lập 2 tiến trình:

- **Chạy Backend (Cổng 3000 & MQTT 1883)**:
  ```powershell
  npm run server
  ```
- **Chạy Frontend Vite Hot-Reload (Cổng 5173)**:
  ```powershell
  npm run client
  ```
  *(Truy cập `http://localhost:5173`, Vite tự động proxy các yêu cầu `/api` và `/socket.io` sang Backend cổng 3000).*

---

## 🔑 Tài Khoản Quản Trị Mặc Định

| Username | Password | Quyền |
| :--- | :--- | :--- |
| `admin` | `admin123` | Quản trị viên (Toàn quyền) |

---

## 🗄️ Cài Đặt Cơ Sở Dữ Liệu Supabase

1. Mở **Supabase Dashboard → SQL Editor**.
2. Sao chép và chạy toàn bộ nội dung file [backend/supabase_schema.sql](file:///c:/Users/Phucx/Downloads/PIO/web/backend/supabase_schema.sql).
3. Nếu muốn tạo dữ liệu giả lập mẫu tại các địa điểm trọng điểm TP.HCM:
   ```powershell
   npm run seed
   ```