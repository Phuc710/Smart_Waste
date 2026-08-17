import React, { useState, useEffect } from 'react';
import {
  Settings,
  Sliders,
  Server,
  Database,
  Cpu,
  Shield,
  Key,
  Clock,
  Radio,
  CheckCircle2,
  AlertTriangle,
  Copy,
  Check,
  Save,
  RotateCcw,
  BookOpen,
  MapPin,
  Truck,
  Wifi,
  Sparkles,
  Layers,
  Globe
} from 'lucide-react';
import { api } from '../services/api';

export default function SettingsPage({ onNotify }) {
  const [activeTab, setActiveTab] = useState('thresholds'); // thresholds, network, retention, esp32
  const [copiedKey, setCopiedKey] = useState('');
  const [healthData, setHealthData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  // Settings State (Nạp từ Backend / CSDL Supabase / .env)
  const [settings, setSettings] = useState({
    fill_threshold_warning: 70,
    fill_threshold_critical: 85,
    bin_offline_timeout_seconds: 15,
    employee_offline_timeout_seconds: 120,
    assign_timeout_minutes: 5,
    paused_timeout_minutes: 30,
    offline_timeout_seconds: 300,
    gps_throttle_min_distance: 10,
    auto_assign: false,
    map_provider: 'leaflet',
    routes_provider: 'osrm'
  });

  useEffect(() => {
    // 1. Tải trạng thái kết nối
    api.getHealth().then(data => setHealthData(data)).catch(() => { });

    // 2. Tải toàn bộ cấu hình từ Backend & CSDL
    api.getSettings()
      .then(res => {
        if (res?.ok && res?.settings) {
          setSettings(res.settings);
        }
      })
      .catch(err => {
        onNotify?.(`Lỗi nạp cấu hình: ${err.message}`, 'error');
      })
      .finally(() => setLoading(false));
  }, [onNotify]);

  const handleCopy = (text, id) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(id);
    setTimeout(() => setCopiedKey(''), 2000);
    onNotify?.('Đã sao chép vào bộ nhớ tạm.', 'info');
  };

  const handleSaveSettings = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      const res = await api.updateSettings(settings);
      if (res?.ok) {
        setSettings(res.settings);
        onNotify?.('Đã lưu cấu hình vào CSDL Supabase & đồng bộ Realtime thành công!', 'success');
      } else {
        onNotify?.(res?.error || 'Lỗi lưu cấu hình', 'error');
      }
    } catch (err) {
      onNotify?.(`Lỗi lưu cấu hình: ${err.message}`, 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleResetDefaults = async () => {
    if (!window.confirm('Bạn có chắc chắn muốn khôi phục toàn bộ thông số về mặc định ban đầu (.env)?')) return;
    setSaving(true);
    try {
      const res = await api.resetSettings();
      if (res?.ok) {
        setSettings(res.settings);
        onNotify?.('Đã khôi phục toàn bộ thông số về mặc định ban đầu (.env)!', 'info');
      }
    } catch (err) {
      onNotify?.(`Lỗi khôi phục cấu hình: ${err.message}`, 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Top Header Card */}
      <div style={{
        backgroundColor: '#ffffff',
        borderRadius: '16px',
        border: '1px solid #e2e8f0',
        padding: '24px 28px',
        boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '16px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{
            width: '52px',
            height: '52px',
            borderRadius: '14px',
            backgroundColor: '#f1f5f9',
            color: '#111a4a',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <Settings size={26} />
          </div>
          <div>
            <h2 style={{ fontSize: '18px', fontWeight: 800, color: '#111a4a', margin: 0 }}>
              Cài đặt & Cấu hình Hệ thống Động
            </h2>
            <div style={{ fontSize: '13px', color: '#64748b', marginTop: '4px' }}>
              Quản lý toàn bộ thông câu hình hệ thống
            </div>
          </div>
        </div>

        {/* Health status badge */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          backgroundColor: '#ecfdf5',
          border: '1px solid #a7f3d0',
          padding: '8px 14px',
          borderRadius: '10px',
          fontSize: '12.5px',
          fontWeight: 700,
          color: '#065f46'
        }}>
          <CheckCircle2 size={16} color="#10b981" />
          <span>Hệ thống hoạt động (Cổng MQTT: {healthData?.mqttPort || 1883} & {healthData?.devices || 0} Thiết bị)</span>
        </div>
      </div>

      {/* Tabs Navigation */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
        borderBottom: '1px solid #e2e8f0',
        paddingBottom: '2px'
      }}>
        {[
          { id: 'thresholds', label: 'Ngưỡng Vận Hành & Điều Phối', icon: Sliders },
          { id: 'network', label: 'Bản Đồ GIS & Dịch Vụ Mạng', icon: Server },
          { id: 'retention', label: 'Cơ Chế Lưu Trữ CSDL', icon: Database },
          { id: 'esp32', label: 'Hướng Dẫn Cài Đặt ESP32', icon: Cpu }
        ].map(tab => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '10px 18px',
                borderRadius: '10px 10px 0 0',
                border: 'none',
                backgroundColor: isActive ? '#ffffff' : 'transparent',
                borderBottom: isActive ? '2px solid #10b981' : '2px solid transparent',
                color: isActive ? '#10b981' : '#64748b',
                fontSize: '13.5px',
                fontWeight: isActive ? 700 : 500,
                cursor: 'pointer',
                transition: 'all 150ms ease'
              }}
            >
              <Icon size={16} />
              <span>{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* Tab 1: Ngưỡng Vận hành & Cảm biến */}
      {activeTab === 'thresholds' && (
        <form onSubmit={handleSaveSettings} style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          border: '1px solid #e2e8f0',
          padding: '28px',
          boxShadow: '0 1px 4px rgba(0,0,0,0.02)',
          display: 'flex',
          flexDirection: 'column',
          gap: '24px'
        }}>
          {/* Section 1: Ngưỡng rác & Thời gian Offline */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
              <Wifi size={18} color="#10b981" />
              <div style={{ fontSize: '15px', fontWeight: 800, color: '#111a4a' }}>
                1. Ngưỡng Cảnh Báo Mức Rác & Thời Gian Ngoại Tuyến
              </div>
            </div>
            <div style={{ fontSize: '12.5px', color: '#64748b' }}>
              Quyết định trạng thái Trực tuyến/Ngoại tuyến thực tế và khi nào hệ thống kích hoạt cảnh báo đỏ/vàng trên Dashboard.
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '13px', fontWeight: 700, color: '#334155', marginBottom: '6px' }}>
                Ngưỡng Cảnh Báo Sắp Đầy (%)
              </label>
              <input
                type="number"
                min="40"
                max="90"
                value={settings.fill_threshold_warning}
                onChange={(e) => setSettings({ ...settings, fill_threshold_warning: Number(e.target.value) })}
                style={{ width: '100%', padding: '10px 14px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '13.5px', boxSizing: 'border-box' }}
              />
              <div style={{ fontSize: '11.5px', color: '#94a3b8', marginTop: '4px' }}>
                Mặc định: <strong>70%</strong>. Khi mức rác 70%, thẻ chuyển sang màu vàng (Sắp cần thu gom).
              </div>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '13px', fontWeight: 700, color: '#334155', marginBottom: '6px' }}>
                Ngưỡng Quá Tải Khẩn Cấp (%)
              </label>
              <input
                type="number"
                min="70"
                max="99"
                value={settings.fill_threshold_critical}
                onChange={(e) => setSettings({ ...settings, fill_threshold_critical: Number(e.target.value) })}
                style={{ width: '100%', padding: '10px 14px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '13.5px', boxSizing: 'border-box' }}
              />
              <div style={{ fontSize: '11.5px', color: '#94a3b8', marginTop: '4px' }}>
                Mặc định: <strong>85%</strong>. Kích hoạt chuông đỏ, tự động tìm xe thu gom gần nhất.
              </div>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '13px', fontWeight: 700, color: '#334155', marginBottom: '6px' }}>
                Timeout Thùng Rác Ngoại Tuyến (Giây)
              </label>
              <input
                type="number"
                min="5"
                max="300"
                value={settings.bin_offline_timeout_seconds}
                onChange={(e) => setSettings({ ...settings, bin_offline_timeout_seconds: Number(e.target.value) })}
                style={{ width: '100%', padding: '10px 14px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '13.5px', boxSizing: 'border-box' }}
              />
              <div style={{ fontSize: '11.5px', color: '#94a3b8', marginTop: '4px' }}>
                Mặc định: <strong>15 giây</strong>. Sau 15s không nhận gói MQTT từ ESP32. Tự động chuyển Offline và chặn lệnh mở nắp.
              </div>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '13px', fontWeight: 700, color: '#334155', marginBottom: '6px' }}>
                Timeout Nhân Viên Ngoại Tuyến (Giây) 
              </label>
              <input
                type="number"
                min="30"
                max="600"
                value={settings.employee_offline_timeout_seconds}
                onChange={(e) => setSettings({ ...settings, employee_offline_timeout_seconds: Number(e.target.value) })}
                style={{ width: '100%', padding: '10px 14px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '13.5px', boxSizing: 'border-box' }}
              />
              <div style={{ fontSize: '11.5px', color: '#94a3b8', marginTop: '4px' }}>
                Mặc định: <strong>120 giây</strong>. Nếu sau 2 phút tài xế không cập nhật vị trí GPS. Chuyển trạng thái xe sang Ngoại tuyến.
              </div>
            </div>
          </div>

          <hr style={{ border: 'none', borderTop: '1px solid #f1f5f9', margin: '8px 0' }} />

          {/* Section 2: Quy trình Điều phối Thu gom */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
              <Truck size={18} color="#3b82f6" />
              <div style={{ fontSize: '15px', fontWeight: 800, color: '#111a4a' }}>
                2. Quy Trình Điều Phối Nhiệm Vụ & Lọc GPS
              </div>
            </div>
            <div style={{ fontSize: '12.5px', color: '#64748b' }}>
              Cấu hình vòng đời nhiệm vụ thu gom, thời hạn phản hồi và thuật toán tối ưu tuyến đường.
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '13px', fontWeight: 700, color: '#334155', marginBottom: '6px' }}>
                Thời Hạn Chấp Nhận Job (Phút)
              </label>
              <input
                type="number"
                min="1"
                max="30"
                value={settings.assign_timeout_minutes}
                onChange={(e) => setSettings({ ...settings, assign_timeout_minutes: Number(e.target.value) })}
                style={{ width: '100%', padding: '10px 14px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '13.5px', boxSizing: 'border-box' }}
              />
              <div style={{ fontSize: '11.5px', color: '#94a3b8', marginTop: '4px' }}>
                Mặc định: <strong>5 phút</strong>. Quá 5 phút tài xế không bấm "Chấp nhận", Cron sẽ tự động hủy Job và giải phóng thùng rác.
              </div>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '13px', fontWeight: 700, color: '#334155', marginBottom: '6px' }}>
                Cảnh Báo Tạm Dừng Quá Lâu (Phút)
              </label>
              <input
                type="number"
                min="5"
                max="120"
                value={settings.paused_timeout_minutes}
                onChange={(e) => setSettings({ ...settings, paused_timeout_minutes: Number(e.target.value) })}
                style={{ width: '100%', padding: '10px 14px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '13.5px', boxSizing: 'border-box' }}
              />
              <div style={{ fontSize: '11.5px', color: '#94a3b8', marginTop: '4px' }}>
                Mặc định: <strong>30 phút</strong>. Khi tài xế tạm dừng quá 30 phút, server tự động gửi thông báo đỏ tới Admin.
              </div>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '13px', fontWeight: 700, color: '#334155', marginBottom: '6px' }}>
                Khoảng Cách Lọc GPS Tối Thiểu (Mét)
              </label>
              <input
                type="number"
                min="2"
                max="50"
                value={settings.gps_throttle_min_distance}
                onChange={(e) => setSettings({ ...settings, gps_throttle_min_distance: Number(e.target.value) })}
                style={{ width: '100%', padding: '10px 14px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '13.5px', boxSizing: 'border-box' }}
              />
              <div style={{ fontSize: '11.5px', color: '#94a3b8', marginTop: '4px' }}>
                Mặc định: <strong>10 mét</strong>. Bỏ qua các rung động GPS nhỏ khi tài xế đứng yên một chỗ.
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '10px', fontSize: '13.5px', fontWeight: 700, color: '#1e293b', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={settings.auto_assign}
                  onChange={(e) => setSettings({ ...settings, auto_assign: e.target.checked })}
                  style={{ width: '18px', height: '18px', accentColor: '#10b981', cursor: 'pointer' }}
                />
                <span>Tự động gán việc (Auto-Assign Engine)</span>
              </label>
              <div style={{ fontSize: '11.5px', color: '#94a3b8', marginTop: '6px', paddingLeft: '28px' }}>
                Khi bật, hệ thống tự động gán tuyến thu gom cho tài xế rảnh gần nhất khi thùng đạt 85%.
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '16px' }}>
            <button
              type="button"
              onClick={handleResetDefaults}
              disabled={saving}
              style={{
                padding: '10px 18px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                backgroundColor: '#ffffff',
                color: '#475569',
                fontWeight: 600,
                fontSize: '13px',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
              }}
            >
              <RotateCcw size={14} />
              <span>Khôi phục mặc định (.env)</span>
            </button>

            <button
              type="submit"
              disabled={saving}
              className="btn-primary"
              style={{
                padding: '10px 24px',
                borderRadius: '8px',
                fontSize: '13.5px',
                fontWeight: 700,
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                cursor: saving ? 'wait' : 'pointer'
              }}
            >
              <Save size={15} />
              <span>{saving ? 'Đang lưu vào CSDL...' : 'Lưu Cấu Hình Vào CSDL'}</span>
            </button>
          </div>
        </form>
      )}

      {/* Tab 2: Bản Đồ GIS & Dịch Vụ Mạng */}
      {activeTab === 'network' && (
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          border: '1px solid #e2e8f0',
          padding: '28px',
          boxShadow: '0 1px 4px rgba(0,0,0,0.02)',
          display: 'flex',
          flexDirection: 'column',
          gap: '24px'
        }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
              <Globe size={18} color="#10b981" />
              <div style={{ fontSize: '15px', fontWeight: 800, color: '#111a4a' }}>
                Hệ Thống Bản Đồ GIS & Định Tuyến
              </div>
            </div>
            <div style={{ fontSize: '12.5px', color: '#64748b' }}>
              Hệ thống sử dụng nền tảng bản đồ mở OpenStreetMap và bộ định tuyến OSRM.
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px' }}>
            <div style={{ padding: '18px', borderRadius: '12px', border: '1px solid #e2e8f0', backgroundColor: '#f8fafc' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Layers size={18} color="#3b82f6" />
                <div style={{ fontSize: '13px', fontWeight: 700, color: '#1e293b' }}>Nền Tảng Bản Đồ</div>
              </div>
              <div style={{ fontSize: '18px', fontWeight: 800, color: '#0f172a', marginTop: '6px' }}>Leaflet.js + OpenStreetMap</div>
              <div style={{ fontSize: '12px', color: '#10b981', fontWeight: 600, marginTop: '4px' }}>
                ✓ Tốc độ tải siêu nhanh, Hỗ trợ đầy đủ Marker tương tác
              </div>
            </div>

            <div style={{ padding: '18px', borderRadius: '12px', border: '1px solid #e2e8f0', backgroundColor: '#f8fafc' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Truck size={18} color="#10b981" />
                <div style={{ fontSize: '13px', fontWeight: 700, color: '#1e293b' }}>Bộ Định Tuyến Thu Gom</div>
              </div>
              <div style={{ fontSize: '18px', fontWeight: 800, color: '#0f172a', marginTop: '6px' }}>OSRM (Open Source Routing)</div>
              <div style={{ fontSize: '12px', color: '#10b981', fontWeight: 600, marginTop: '4px' }}>
                ✓ TTính toán khoảng cách & thời gian di chuyển thực tế
              </div>
            </div>
          </div>

          <hr style={{ border: 'none', borderTop: '1px solid #f1f5f9' }} />

          <div>
            <div style={{ fontSize: '15px', fontWeight: 800, color: '#111a4a', marginBottom: '4px' }}>
              Thông Số Cổng Dịch Vụ Mạng & CSDL
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '16px' }}>
            <div style={{ padding: '16px', borderRadius: '12px', border: '1px solid #e2e8f0', backgroundColor: '#f8fafc' }}>
              <div style={{ fontSize: '12px', fontWeight: 700, color: '#64748b' }}>CỔNG MQTT BROKER (TCP)</div>
              <div style={{ fontSize: '20px', fontWeight: 800, color: '#0f172a', fontFamily: 'var(--font-mono)', marginTop: '4px' }}>1883</div>
              <div style={{ fontSize: '11.5px', color: '#10b981', fontWeight: 600, marginTop: '4px' }}>● Đang lắng nghe ESP32</div>
            </div>

            <div style={{ padding: '16px', borderRadius: '12px', border: '1px solid #e2e8f0', backgroundColor: '#f8fafc' }}>
              <div style={{ fontSize: '12px', fontWeight: 700, color: '#64748b' }}>CỔNG HTTP REST & SOCKET.IO</div>
              <div style={{ fontSize: '20px', fontWeight: 800, color: '#0f172a', fontFamily: 'var(--font-mono)', marginTop: '4px' }}>3000</div>
              <div style={{ fontSize: '11.5px', color: '#10b981', fontWeight: 600, marginTop: '4px' }}>● Đang phục vụ Web Admin</div>
            </div>
          </div>
        </div>
      )}

      {/* Tab 3: Chu kỳ & Lưu trữ CSDL */}
      {activeTab === 'retention' && (
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          border: '1px solid #e2e8f0',
          padding: '28px',
          boxShadow: '0 1px 4px rgba(0,0,0,0.02)',
          display: 'flex',
          flexDirection: 'column',
          gap: '20px'
        }}>
          <div>
            <div style={{ fontSize: '15px', fontWeight: 800, color: '#111a4a', marginBottom: '4px' }}>
              Cơ Chế Điều Tốc & Tối Ưu Hóa Ghi CSDL
            </div>
            <div style={{ fontSize: '12.5px', color: '#64748b' }}>
              Hệ thống kết hợp phân tầng lưu trữ để đạt tốc độ real-time cao mà không làm bùng nổ dung lượng database.
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '16px' }}>
            <div style={{ padding: '18px', borderRadius: '12px', border: '1px solid #e2e8f0', backgroundColor: '#f8fafc' }}>
              <div style={{ fontSize: '13px', fontWeight: 700, color: '#1e293b' }}>Chu kỳ Telemetry ESP32</div>
              <div style={{ fontSize: '24px', fontWeight: 800, color: '#3b82f6', fontFamily: 'var(--font-mono)', marginTop: '4px' }}>1 giây</div>
              <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
                Phát trực tiếp qua WebSocket/Socket.IO để cập nhật biểu đồ và chuyển động nắp mượt mà.
              </div>
            </div>

            <div style={{ padding: '18px', borderRadius: '12px', border: '1px solid #e2e8f0', backgroundColor: '#f8fafc' }}>
              <div style={{ fontSize: '13px', fontWeight: 700, color: '#10b981', fontFamily: 'var(--font-mono)', marginTop: '4px' }}>30 giây</div>
              <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
                Bộ điều tốc <code style={{ backgroundColor: '#e2e8f0', padding: '1px 4px', borderRadius: '3px' }}>historyTimers</code> giảm <strong>96.7%</strong> tải I/O ghi vào bảng <code style={{ backgroundColor: '#e2e8f0', padding: '1px 4px', borderRadius: '3px' }}>bin_events</code>.
              </div>
            </div>

            <div style={{ padding: '18px', borderRadius: '12px', border: '1px solid #e2e8f0', backgroundColor: '#f8fafc' }}>
              <div style={{ fontSize: '13px', fontWeight: 700, color: '#1e293b' }}>Chu kỳ Poller Lệnh Supabase</div>
              <div style={{ fontSize: '24px', fontWeight: 800, color: '#8b5cf6', fontFamily: 'var(--font-mono)', marginTop: '4px' }}>400 ms</div>
              <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
                Quét các lệnh mở/đóng từ App di động để chuyển tiếp thành gói tin MQTT xuống phần cứng.
              </div>
            </div>

            <div style={{ padding: '18px', borderRadius: '12px', border: '1px solid #e2e8f0', backgroundColor: '#f8fafc' }}>
              <div style={{ fontSize: '13px', fontWeight: 700, color: '#1e293b' }}>Thời Hạn Signed URL Ảnh</div>
              <div style={{ fontSize: '24px', fontWeight: 800, color: '#d97706', fontFamily: 'var(--font-mono)', marginTop: '4px' }}>60 phút</div>
              <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
                URL xem ảnh sự cố tự động hết hạn sau 3600s, bảo vệ bucket Private khỏi rò rỉ dữ liệu.
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Tab 4: Hướng dẫn Cài đặt ESP32 */}
      {activeTab === 'esp32' && (
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          border: '1px solid #e2e8f0',
          padding: '28px',
          boxShadow: '0 1px 4px rgba(0,0,0,0.02)',
          display: 'flex',
          flexDirection: 'column',
          gap: '24px'
        }}>
          <div>
            <div style={{ fontSize: '15px', fontWeight: 800, color: '#111a4a', marginBottom: '4px' }}>
              Hướng Dẫn Thao Tác Cài Đặt Wi-Fi & MQTT Cho Thùng Rác ESP32
            </div>
            <div style={{ fontSize: '12.5px', color: '#64748b' }}>
              Quy trình thiết lập thiết bị mới hoặc chuyển sang mạng Wi-Fi khác.
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div style={{ display: 'flex', gap: '14px', alignItems: 'flex-start' }}>
              <div style={{ width: '28px', height: '28px', borderRadius: '50%', backgroundColor: '#10b981', color: '#ffffff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: '13px', flexShrink: 0 }}>
                1
              </div>
              <div>
                <div style={{ fontWeight: 700, color: '#1e293b', fontSize: '13.5px' }}>Bật nguồn hoặc Khởi động lại chế độ Cài đặt</div>
                <div style={{ fontSize: '12.5px', color: '#475569', marginTop: '2px', lineHeight: 1.5 }}>
                  Nếu thiết bị chưa có Wi-Fi hoặc bạn muốn đổi mạng: <strong>Giữ nút BOOT (GPIO 0) trong 3 giây</strong> khi đang chạy. ESP32 sẽ tự động xóa bộ nhớ Flash NVRAM và khởi động lại vào chế độ Access Point.
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '14px', alignItems: 'flex-start' }}>
              <div style={{ width: '28px', height: '28px', borderRadius: '50%', backgroundColor: '#10b981', color: '#ffffff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: '13px', flexShrink: 0 }}>
                2
              </div>
              <div>
                <div style={{ fontWeight: 700, color: '#1e293b', fontSize: '13.5px' }}>Kết nối vào mạng Access Point của Thùng rác</div>
                <div style={{ fontSize: '12.5px', color: '#475569', marginTop: '2px', lineHeight: 1.5 }}>
                  Trên điện thoại hoặc laptop, mở danh sách Wi-Fi và chọn mạng có tên dạng: <code style={{ backgroundColor: '#f1f5f9', padding: '2px 6px', borderRadius: '4px', fontWeight: 700, color: '#0f172a' }}>SmartBin_XXXX</code> (trong đó XXXX là 4 ký tự cuối của địa chỉ MAC).
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '14px', alignItems: 'flex-start' }}>
              <div style={{ width: '28px', height: '28px', borderRadius: '50%', backgroundColor: '#10b981', color: '#ffffff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: '13px', flexShrink: 0 }}>
                3
              </div>
              <div>
                <div style={{ fontWeight: 700, color: '#1e293b', fontSize: '13.5px' }}>Điền thông tin Cấu hình trên trang Portal</div>
                <div style={{ fontSize: '12.5px', color: '#475569', marginTop: '2px', lineHeight: 1.5 }}>
                  Trình duyệt sẽ tự động mở trang cài đặt (hoặc truy cập <code style={{ backgroundColor: '#f1f5f9', padding: '2px 6px', borderRadius: '4px' }}>192.168.4.1</code>). Nhập các thông tin:
                  <ul style={{ margin: '6px 0 0 16px', padding: 0 }}>
                    <li><strong>Wi-Fi SSID & Password</strong>: Mạng Wi-Fi tại vị trí lắp đặt.</li>
                    <li><strong>MQTT Broker IP</strong>: Địa chỉ IP của máy tính/server đang chạy Node.js (ví dụ: <code style={{ backgroundColor: '#f1f5f9', padding: '1px 4px', borderRadius: '3px' }}>192.168.1.15</code>).</li>
                    <li><strong>Tên thùng rác</strong>: Ví dụ: <em>Thùng Rác Sảnh A</em>.</li>
                    <li><strong>Vị trí lắp đặt</strong>: Ví dụ: <em>Tầng 1 Tòa Nhà Trung Tâm</em>.</li>
                  </ul>
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '14px', alignItems: 'flex-start' }}>
              <div style={{ width: '28px', height: '28px', borderRadius: '50%', backgroundColor: '#10b981', color: '#ffffff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: '13px', flexShrink: 0 }}>
                4
              </div>
              <div>
                <div style={{ fontWeight: 700, color: '#1e293b', fontSize: '13.5px' }}>Lưu và Hoàn tất</div>
                <div style={{ fontSize: '12.5px', color: '#475569', marginTop: '2px', lineHeight: 1.5 }}>
                  Bấm <strong>Save</strong>. ESP32 sẽ lưu cấu hình vào Flash NVRAM (Preferences), tự động kết nối Wi-Fi, kết nối MQTT Broker và xuất hiện ngay lập tức trên Dashboard Web Admin!
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
