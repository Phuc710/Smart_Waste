/**
 * SMARTWASTE UNIVERSAL DESIGN SYSTEM & COLOR TOKENS
 * Đồng bộ màu sắc và quy chuẩn UI giữa Web Admin Dashboard & Mobile App (React Native / Flutter / iOS / Android)
 */

export const COLORS = {
  // 1. Primary Palette (Màu chủ đạo - Xanh sinh thái Eco Emerald)
  primary: {
    50: '#ecfdf5',
    100: '#d1fae5',
    200: '#a7f3d0',
    300: '#6ee7b7',
    400: '#34d399',
    500: '#10b981', // Main Brand Color
    600: '#059669',
    700: '#047857',
    800: '#065f46',
    900: '#064e3b'
  },

  // 2. Navy & Brand Inks (Tiêu đề, văn bản đậm, thanh điều hướng)
  navy: {
    50: '#f8fafc',
    100: '#f1f5f9',
    200: '#e2e8f0',
    300: '#cbd5e1',
    400: '#94a3b8',
    500: '#64748b',
    600: '#475569',
    700: '#334155',
    800: '#1e293b',
    900: '#0f172a',
    950: '#111a4a' // Primary Headings & Top Nav
  },

  // 3. Status & Alert Tokens (Trạng thái mức rác & cảnh báo)
  status: {
    // Nguy cấp / Quá tải (> 85%) / Lỗi
    danger: {
      light: '#fef2f2',
      border: '#fecaca',
      badgeBg: '#fee2e2',
      main: '#ef4444',
      dark: '#dc2626',
      text: '#991b1b'
    },
    // Cảnh báo / Sắp đầy (70% - 85%)
    warning: {
      light: '#fffbeb',
      border: '#fde68a',
      badgeBg: '#fef3c7',
      main: '#f59e0b',
      dark: '#d97706',
      text: '#92400e'
    },
    // Bình thường / An toàn (< 70%)
    success: {
      light: '#f0fdf4',
      border: '#bbf7d0',
      badgeBg: '#ecfdf5',
      main: '#10b981',
      dark: '#059669',
      text: '#065f46'
    },
    // Đang xử lý / Điều phối / Lộ trình
    info: {
      light: '#eff6ff',
      border: '#bfdbfe',
      badgeBg: '#dbeafe',
      main: '#3b82f6',
      dark: '#2563eb',
      text: '#1e40af'
    },
    // Tiến trình gán việc / Chờ xác nhận
    purple: {
      light: '#faf5ff',
      border: '#e9d5ff',
      badgeBg: '#f3e8ff',
      main: '#a855f7',
      dark: '#7c3aed',
      text: '#581c87'
    },
    // Ngoại tuyến / Tắt máy
    offline: {
      light: '#f8fafc',
      border: '#e2e8f0',
      badgeBg: '#f1f5f9',
      main: '#94a3b8',
      dark: '#64748b',
      text: '#475569'
    }
  },

  // 4. Backgrounds & Surfaces
  surface: {
    pageBg: '#f8fafc',
    cardBg: '#ffffff',
    cardSubtle: '#f8fafc',
    cardElevated: '#ffffff',
    inputBg: '#ffffff',
    border: '#e2e8f0',
    borderLight: '#f1f5f9',
    borderFocus: '#10b981',
    divider: '#f1f5f9'
  },

  // 5. Typography Text Colors
  text: {
    primary: '#111a4a',   // Tiêu đề lớn H1, H2
    body: '#1e293b',      // Nội dung chính
    secondary: '#475569', // Nhãn phụ, mô tả
    muted: '#64748b',     // Chú thích, thời gian
    disabled: '#94a3b8',  // Trạng thái vô hiệu
    inverse: '#ffffff'    // Chữ trắng trên nền màu
  }
};

// ==========================================================================
// TYPOGRAPHY, RADII, SHADOWS TOKENS
// ==========================================================================

export const RADII = {
  xs: 4,
  sm: 6,
  md: 8,
  lg: 12,
  xl: 16,
  xxl: 24,
  full: 9999
};

export const SHADOWS = {
  sm: '0 1px 3px rgba(0, 0, 0, 0.04)',
  card: '0 1px 4px rgba(0, 0, 0, 0.03), 0 4px 12px rgba(0, 0, 0, 0.02)',
  hover: '0 4px 14px rgba(0, 0, 0, 0.08), 0 10px 24px rgba(0, 0, 0, 0.04)',
  modal: '0 20px 40px rgba(17, 26, 74, 0.15)',
  dropdown: '0 10px 25px rgba(0, 0, 0, 0.10)'
};

// ==========================================================================
// MAP & GIS PIN STYLING TOKENS
// ==========================================================================

export const MAP_THEME = {
  tileUrl: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
  tileVoyager: 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png',
  routeColor: '#2563eb',
  routeGlow: 'rgba(37, 99, 235, 0.25)',
  routeDash: '#93c5fd',
  
  pins: {
    binOnline: '#10b981',
    binNearFull: '#f59e0b',
    binCritical: '#ef4444',
    binOffline: '#64748b',
    truckActive: '#059669',
    truckBusy: '#2563eb',
    truckPaused: '#f97316',
    truckOffline: '#94a3b8'
  }
};

// ==========================================================================
// HELPER FUNCTIONS CHO UI WEB & MOBILE
// ==========================================================================

/**
 * Lấy bộ màu và nhãn cho mức rác (%)
 */
export function getBinLevelTheme(levelPercent = 0, isOnline = true) {
  if (!isOnline) {
    return {
      status: 'OFFLINE',
      label: 'Ngoại tuyến',
      color: COLORS.status.offline.dark,
      bg: COLORS.status.offline.badgeBg,
      border: COLORS.status.offline.border,
      text: COLORS.status.offline.text,
      badgeText: 'TẮT',
      markerPin: '#64748b'
    };
  }

  const level = Number(levelPercent) || 0;

  if (level >= 85) {
    return {
      status: 'CRITICAL',
      label: 'Quá tải khẩn cấp',
      color: COLORS.status.danger.main,
      bg: COLORS.status.danger.badgeBg,
      border: COLORS.status.danger.border,
      text: COLORS.status.danger.text,
      badgeText: `${level}%`,
      markerPin: '#ef4444'
    };
  }

  if (level >= 70) {
    return {
      status: 'WARNING',
      label: 'Sắp đầy',
      color: COLORS.status.warning.main,
      bg: COLORS.status.warning.badgeBg,
      border: COLORS.status.warning.border,
      text: COLORS.status.warning.text,
      badgeText: `${level}%`,
      markerPin: '#f59e0b'
    };
  }

  return {
    status: 'NORMAL',
    label: 'Bình thường',
    color: COLORS.status.success.main,
    bg: COLORS.status.success.badgeBg,
    border: COLORS.status.success.border,
    text: COLORS.status.success.text,
    badgeText: `${level}%`,
    markerPin: '#10b981'
  };
}

/**
 * Lấy bộ màu và nhãn trạng thái nhiệm vụ thu gom (Collection Job)
 */
export function getJobStatusTheme(status) {
  switch (status) {
    case 'PENDING':
      return {
        text: 'Chờ nhận việc',
        bg: COLORS.status.warning.badgeBg,
        border: COLORS.status.warning.border,
        color: COLORS.status.warning.dark
      };
    case 'ASSIGNED':
      return {
        text: 'Chờ tài xế xác nhận',
        bg: COLORS.status.purple.badgeBg,
        border: COLORS.status.purple.border,
        color: COLORS.status.purple.dark
      };
    case 'ACCEPTED':
      return {
        text: 'Đã tiếp nhận',
        bg: COLORS.status.info.badgeBg,
        border: COLORS.status.info.border,
        color: COLORS.status.info.dark
      };
    case 'IN_PROGRESS':
      return {
        text: 'Đang thu gom',
        bg: COLORS.status.info.badgeBg,
        border: COLORS.status.info.border,
        color: COLORS.status.info.dark
      };
    case 'PAUSED':
      return {
        text: 'Tạm dừng',
        bg: '#fff7ed',
        border: '#fed7aa',
        color: '#ea580c'
      };
    case 'COMPLETED':
      return {
        text: 'Hoàn tất',
        bg: COLORS.status.success.badgeBg,
        border: COLORS.status.success.border,
        color: COLORS.status.success.dark
      };
    case 'CANCELLED':
      return {
        text: 'Đã hủy',
        bg: COLORS.status.offline.badgeBg,
        border: COLORS.status.offline.border,
        color: COLORS.status.offline.dark
      };
    case 'REJECTED':
      return {
        text: 'Từ chối',
        bg: COLORS.status.danger.badgeBg,
        border: COLORS.status.danger.border,
        color: COLORS.status.danger.dark
      };
    case 'EXPIRED':
      return {
        text: 'Hết hạn nhận việc',
        bg: COLORS.status.offline.badgeBg,
        border: COLORS.status.offline.border,
        color: COLORS.status.offline.dark
      };
    default:
      return {
        text: 'Chờ xử lý',
        bg: COLORS.status.offline.badgeBg,
        border: COLORS.status.offline.border,
        color: COLORS.status.offline.dark
      };
  }
}

export default {
  COLORS,
  RADII,
  SHADOWS,
  MAP_THEME,
  getBinLevelTheme,
  getJobStatusTheme
};
