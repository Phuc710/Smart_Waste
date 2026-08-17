# 🎬 SMARTWASTE MOBILE — MOTION SYSTEM & ANIMATION SPECIFICATIONS

> **Quy Chuẩn Chuyển Động & Hiệu Ứng Mượt Mà Cho Ứng Dụng Tài Xế**

---

## 1. Thời Gian & Easing Chuẩn

* **Micro-interactions (Bấm nút / Icon):** `100ms - 150ms`
* **Card Press / Feedback:** `150ms - 200ms`
* **Page Transitions (Chuyển trang):** `250ms - 300ms`
* **Bottom Sheet (Kéo mở danh sách điểm):** `300ms - 400ms`
* **Route Polyline Drawing (Vẽ tuyến đường):** `500ms - 800ms`
* **Standard Easing Curve:** `cubic-bezier(0.22, 1, 0.36, 1)` (Ease-Out mượt mà)

---

## 2. Danh Sách 10 Hiệu Ứng Trọng Tâm

1. **Page Transition**: Trượt nhẹ ngang `12dp` kết hợp Fade in `250ms`.
2. **Staggered Home Entrance**: Header (`0ms`) $\rightarrow$ Thẻ Xe (`80ms`) $\rightarrow$ Thống Kê (`160ms`) $\rightarrow$ Tuyến Đường (`240ms`).
3. **Button Press Scale**: Khi bấm nút, co nhẹ `scale(0.97)` trong `120ms` rồi nảy lại `1.0`.
4. **Card Press Feedback**: Khi chạm thẻ thùng rác, co nhẹ `scale(0.985)` với nền highlight nhẹ.
5. **Number Counter**: Khi mở Home, các con số thống kê (Tổng nhiệm vụ, Cần gom, Hoàn thành) tăng mượt mà từ 0 đến giá trị thật trong `600ms`.
6. **Progress Bar Animation**: Thanh mức đầy rác hoặc thanh tiến độ thu gom lướt từ `0%` đến mức hiện tại trong `500ms`.
7. **Truck Live Marker Pulse**: Marker xe tải hiển thị vòng tròn sóng xung kích màu xanh dương nhẹ toả ra chu kỳ `2.5s` báo hiệu xe đang trực tuyến.
8. **Smooth GPS Interpolation**: Khi nhận tọa độ GPS mới từ thiết bị, vị trí marker xe trượt từ điểm A sang điểm B trong `1000ms`, không nhảy giật (teleport).
9. **Route Drawing**: Khi bắt đầu tuyến, đường polyline OSRM vẽ từ vị trí xe qua từng điểm dừng `1, 2, 3, 4` với mũi tên chỉ hướng.
10. **Collection Complete State Transition**: Khi bấm **[Đã thu gom]**:
    - Nút bấm chuyển icon checkmark xanh lá $\rightarrow$ Mức rác hạ từ `92%` về `0%` $\rightarrow$ Thẻ chuyển từ Đỏ sang Xanh $\rightarrow$ Tự động chuyển sang điểm thu gom tiếp theo.
