/**
 * Universal Vietnam Time (UTC+7 / Asia/Ho_Chi_Minh) Formatting Utilities
 */

export const VIETNAM_TIMEZONE = 'Asia/Ho_Chi_Minh';
export const VIETNAM_LOCALE = 'vi-VN';

/**
 * Format full date & time: HH:mm:ss DD/MM/YYYY (or custom options)
 */
export function formatVietnamDateTime(dateInput, options = {}) {
  if (!dateInput) return '—';
  const d = new Date(dateInput);
  if (isNaN(d.getTime())) return '—';

  return d.toLocaleString(VIETNAM_LOCALE, {
    timeZone: VIETNAM_TIMEZONE,
    hour: '2-digit',
    minute: '2-digit',
    second: options.includeSeconds !== false ? '2-digit' : undefined,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour12: false,
    ...options
  });
}

/**
 * Format time only: HH:mm:ss or HH:mm
 */
export function formatVietnamTime(dateInput, includeSeconds = true) {
  if (!dateInput) return '—';
  const d = new Date(dateInput);
  if (isNaN(d.getTime())) return '—';

  return d.toLocaleTimeString(VIETNAM_LOCALE, {
    timeZone: VIETNAM_TIMEZONE,
    hour: '2-digit',
    minute: '2-digit',
    ...(includeSeconds ? { second: '2-digit' } : {}),
    hour12: false
  });
}

/**
 * Format date only: DD/MM/YYYY
 */
export function formatVietnamDate(dateInput) {
  if (!dateInput) return '—';
  const d = new Date(dateInput);
  if (isNaN(d.getTime())) return '—';

  return d.toLocaleDateString(VIETNAM_LOCALE, {
    timeZone: VIETNAM_TIMEZONE,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  });
}

/**
 * Calculate relative time in Vietnamese (UTC+7 aware)
 */
export function getVietnamRelativeTime(dateInput) {
  if (!dateInput) return { text: 'Chưa cập nhật', color: '#94a3b8' };
  const date = new Date(dateInput);
  if (isNaN(date.getTime())) return { text: 'Chưa cập nhật', color: '#94a3b8' };

  const now = new Date();
  const diffSec = Math.floor((now.getTime() - date.getTime()) / 1000);

  if (diffSec < 0) return { text: 'Vừa xong', color: '#10b981' };
  if (diffSec < 60) return { text: 'Vừa xong', color: '#10b981' };
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) {
    if (diffMin <= 5) return { text: `${diffMin} phút trước`, color: '#ef4444' };
    if (diffMin <= 15) return { text: `${diffMin} phút trước`, color: '#f59e0b' };
    return { text: `${diffMin} phút trước`, color: '#64748b' };
  }
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return { text: `${diffHour} giờ trước`, color: '#64748b' };
  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 30) return { text: `${diffDay} ngày trước`, color: '#94a3b8' };
  return { text: formatVietnamDate(dateInput), color: '#94a3b8' };
}
