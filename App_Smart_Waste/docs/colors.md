# 🎨 SMARTWASTE MOBILE — DESIGN TOKENS & COLOR PALETTE

> **Bảng Mã Màu & Hệ Thống Khoảng Cách Chuẩn Cho Ứng Dụng Android Native**

---

## 1. Bảng Màu Thương Hiệu & Giao Diện (Brand & Surface Tokens)

```xml
<!-- res/values/colors.xml -->
<resources>
    <!-- Brand Primary (Eco Emerald) -->
    <color name="primary_50">#ECFDF5</color>
    <color name="primary_100">#D1FAE5</color>
    <color name="primary_200">#A7F3D0</color>
    <color name="primary_500">#10B981</color> <!-- Main Brand Color -->
    <color name="primary_600">#059669</color> <!-- Hover / Active / CTA -->
    <color name="primary_800">#065F46</color>
    
    <!-- Navy & Text (Headings, Body, Muted) -->
    <color name="navy_950">#111A4A</color>    <!-- H1, H2, Logo, Bold Titles -->
    <color name="navy_900">#0F172A</color>    <!-- Big Numbers, System Clock -->
    <color name="navy_800">#1E293B</color>    <!-- Body Text -->
    <color name="navy_600">#475569</color>    <!-- Sub-labels -->
    <color name="navy_500">#64748B</color>    <!-- Muted Text, Timestamps -->
    <color name="navy_300">#CBD5E1</color>    <!-- Border Inputs -->
    <color name="navy_200">#E2E8F0</color>    <!-- Card Borders, Dividers -->
    <color name="navy_100">#F1F5F9</color>    <!-- Light Neutral Bg -->
    <color name="navy_50">#F8FAFC</color>     <!-- App Background -->
    
    <!-- Status: Danger / Critical (Mức rác >= 85% / Lỗi) -->
    <color name="status_danger_main">#EF4444</color>
    <color name="status_danger_dark">#DC2626</color>
    <color name="status_danger_bg">#FEE2E2</color>
    <color name="status_danger_border">#FECACA</color>
    <color name="status_danger_text">#991B1B</color>
    
    <!-- Status: Warning / Sắp đầy (Mức rác 70% - 84% / Chờ nhận việc) -->
    <color name="status_warning_main">#F59E0B</color>
    <color name="status_warning_dark">#D97706</color>
    <color name="status_warning_bg">#FEF3C7</color>
    <color name="status_warning_border">#FDE68A</color>
    <color name="status_warning_text">#92400E</color>
    
    <!-- Status: Info / Đang xử lý / Tuyến đường OSRM -->
    <color name="status_info_main">#3B82F6</color>
    <color name="status_info_dark">#2563eb</color>
    <color name="status_info_bg">#EFF6FF</color>
    <color name="status_info_border">#BFDBFE</color>
    <color name="status_info_text">#1E40AF</color>
    
    <!-- Status: Purple / Chờ tài xế xác nhận -->
    <color name="status_purple_main">#A855F7</color>
    <color name="status_purple_dark">#7C3AED</color>
    <color name="status_purple_bg">#F5F3FF</color>
    <color name="status_purple_border">#DDD6FE</color>
    
    <!-- Surface & General -->
    <color name="surface_card">#FFFFFF</color>
    <color name="surface_bg">#F8FAFC</color>
    <color name="white">#FFFFFF</color>
    <color name="black">#000000</color>
</resources>
```

---

## 2. Hệ Thống Khoảng Cách & Bo Góc (Spacing Scale & Radii)

```xml
<!-- res/values/dimens.xml -->
<resources>
    <!-- Spacing Scale -->
    <dimen name="space_4">4dp</dimen>
    <dimen name="space_8">8dp</dimen>
    <dimen name="space_12">12dp</dimen>
    <dimen name="space_16">16dp</dimen>
    <dimen name="space_20">20dp</dimen> <!-- Screen Gutter Padding chuẩn -->
    <dimen name="space_24">24dp</dimen>
    <dimen name="space_32">32dp</dimen>
    
    <!-- Border Radii -->
    <dimen name="radius_badge">6dp</dimen>
    <dimen name="radius_input">10dp</dimen>
    <dimen name="radius_card">16dp</dimen>
    <dimen name="radius_button">12dp</dimen>
    <dimen name="radius_pill">999dp</dimen>
    
    <!-- Component Heights -->
    <dimen name="button_height">48dp</dimen>
    <dimen name="input_height">48dp</dimen>
    <dimen name="top_bar_height">56dp</dimen>
    <dimen name="bottom_nav_height">64dp</dimen>
</resources>
```
