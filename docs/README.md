# 📚 HỆ THỐNG TÀI LIỆU KỸ THUẬT DỰ ÁN SMARTWASTE
## (SMARTWASTE URBAN IOT PLATFORM DOCUMENTATION HUB)

> **Hệ sinh thái Quản trị & Điều phối Thu gom Rác Thông minh Đô thị — SmartWaste Platform**  
> **Phiên bản:** 1.0.0 • **Chuẩn Kỹ thuật:** Enterprise Senior Architecture Specification 2026

---

## 🧭 BẢN ĐỒ ĐIỀU HƯỚNG TÀI LIỆU (DOCUMENTATION SITEMAP)

Hệ thống tài liệu kỹ thuật toàn diện được chuẩn hóa và phân tách thành 2 phân hệ chuyên biệt:

```
docs/
├── README.md                      ⭐ [BẠN ĐANG Ở ĐÂY] Trung tâm điều phối tài liệu dự án
│
├── server/
│   └── README.md                  📘 TÀI LIỆU MÁY CHỦ SERVER (BACKEND + FRONTEND WEB ADMIN)
│                                  • Kiến trúc 4 tầng & Multi-Protocol Concurrency (Express, Aedes MQTT, Socket.IO)
│                                  • Supabase PostgreSQL (11 bảng CSDL, 18+ Stored Procedures ACID, OCC)
│                                  • Đặc tả 100% RESTful APIs, WebSocket Events, Background Workers
│                                  • Frontend React 19 SPA, Leaflet GIS Map, Design Tokens & Cài đặt
│
└── mobile/
    └── README.md                  📱 TÀI LIỆU ỨNG DỤNG DI ĐỘNG (MOBILE DRIVER APP - ANDROID NATIVE)
                                   • Clean Architecture + MVI/MVVM, StateFlow, Coroutines, ViewBinding
                                   • Hệ thống Dẫn đường Heading-Up / Course-Up Navigation (Leaflet WebView Bridge)
                                   • Thuật toán Anti-Spin 360°, Dual-Stage Fallback, Tự động Reroute khi lệch >65m
                                   • Quét Radar 500m Self-Pick, Điều khiển nắp thùng IoT, Offline-First & Background Service
```

---

## 🚀 TRUY CẬP NHANH TÀI LIỆU CHI TIẾT

| Phân Hệ | Đường Dẫn Tài Liệu | Nội Dung Trọng Tâm |
| :--- | :--- | :--- |
| **🌐 Server (Backend + Frontend)** | [docs/server/README.md](file:///c:/Users/Phucx/Downloads/waste/docs/server/README.md) | Node.js 18+, Express 5.x, Aedes MQTT Broker :1883, Socket.IO :3000, Supabase PostgreSQL, Storage Signed URLs, OSRM GIS Engine, React 19 SPA, Leaflet Map. |
| **📱 Mobile App (Android Native)** | [docs/mobile/README.md](file:///c:/Users/Phucx/Downloads/waste/docs/mobile/README.md) | Kotlin 1.9+, Android SDK 34, Heading-Up Navigation 72% offset, Anti-Spin Accumulator, Retrofit 2, Socket.IO Realtime, FusedLocationProviderClient, Foreground Service. |

---

## 🏛️ SƠ ĐỒ KIẾN TRÚC TỔNG THỂ TOÀN HỆ SINH THÁI

```mermaid
flowchart TB
    subgraph IOT_LAYER ["1. TẦNG THIẾT BỊ PHẦN CỨNG IOT"]
        ESP32["Vi điều khiển ESP32 / Mock Python\n• Cảm biến siêu âm (Mức rác % & Phát hiện người)\n• Động cơ Servo 180° (Mở/Đóng nắp)\n• Cảm biến hồng ngoại PIR\n• WiFi Client & MQTT Publisher/Subscriber"]
    end

    subgraph SERVER_LAYER ["2. MÁY CHỦ TRUNG TÂM (SERVER ECOSYSTEM)"]
        direction TB
        subgraph GATEWAYS ["Cổng Giao tiếp Mạng Đa giao thức"]
            MQTT_BROKER["Aedes MQTT Broker\n(TCP Port 1883)"]
            SOCKET_IO["Socket.IO Server\n(HTTP/WSS Port 3000)"]
            EXPRESS_API["Express 5.x REST API\n(HTTP Port 3000)"]
        end

        subgraph BACKEND_CORE ["Core Services & In-Memory StateStore"]
            STATE_STORE[("StateStore Singleton\n• latestBins Cache\n• employeeLocationsCache\n• 2-Way ACK Waiters")]
            SERVICES["Domain Services\n• binService • dispatchService • employeeService\n• incidentService • routingService • statsService"]
            WORKERS["Background Workers\n• commandPoller (400ms)\n• binLivenessWorker (3s)\n• jobMonitorCron (30s)"]
        end

        GATEWAYS <--> STATE_STORE
        EXPRESS_API --> SERVICES
        SERVICES <--> STATE_STORE
        WORKERS --> SERVICES
    end

    subgraph DATABASE_LAYER ["3. CƠ SỞ DỮ LIỆU ĐÁM MÂY (SUPABASE CLOUD)"]
        POSTGRES[("PostgreSQL 15+ Database\n• 11 Bảng chuẩn hóa quan hệ\n• 18+ Transactional RPCs\n• Optimistic Concurrency Control")]
        STORAGE["Supabase Object Storage\n• Bucket 'incident-images' (Private)"]
    end

    subgraph CLIENT_LAYER ["4. TẦNG ỨNG DỤNG KHÁCH (CLIENT APPS)"]
        WEB_ADMIN["Web Admin Dashboard (React 19 SPA)\n• Bản đồ Leaflet GIS & Marker cụm\n• Điều khiển nắp thùng 2 chiều\n• Điều phối ca gom & Quản lý nhân sự"]
        MOBILE_DRIVER["Mobile Driver App (Android Native Kotlin)\n• Dẫn đường Heading-Up Turn-by-Turn\n• Quét Radar 500m tự gom rác\n• Chụp ảnh minh chứng sự cố & GPS Tracking"]
    end

    ESP32 <== "MQTT TCP :1883\nwastebin/{id}/status (Pub)\nwastebin/{id}/command (Sub)" ==> MQTT_BROKER
    EXPRESS_API <== "REST API / Cookie Session" ==> WEB_ADMIN
    SOCKET_IO <== "WebSocket wss:// (Realtime Events)" ==> WEB_ADMIN
    EXPRESS_API <== "REST API / Bearer Token" ==> MOBILE_DRIVER
    SOCKET_IO <== "WebSocket (GPS / Notifications)" ==> MOBILE_DRIVER

    SERVICES <== "PostgREST & Service Role RPC" ==> POSTGRES
    SERVICES <== "Storage Admin API (Signed URLs)" ==> STORAGE
```

---

> **SmartWaste Urban IoT Platform — Kiến trúc Kỹ thuật Tiêu chuẩn Doanh nghiệp 2026**
