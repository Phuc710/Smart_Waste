import React, { useEffect, useState, useRef } from 'react';
import { Clock, Menu, Navigation, Bell, AlertTriangle, Trash2, ArrowRight, CheckCircle2, X } from 'lucide-react';
import { formatVietnamTime } from '../utils/dateTime';

export default function Header({ activeView, onToggleSidebar, onOpenMap, bins = [], onNavigateTab, onSelectBinForMap }) {
  const [timeStr, setTimeStr] = useState('');
  const [bellOpen, setBellOpen] = useState(false);
  const bellRef = useRef(null);

  useEffect(() => {
    const updateTime = () => {
      setTimeStr(formatVietnamTime(new Date(), true));
    };
    updateTime();
    const interval = setInterval(updateTime, 1000);
    return () => clearInterval(interval);
  }, []);

  // Close bell dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (bellRef.current && !bellRef.current.contains(e.target)) {
        setBellOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const titles = {
    dashboard: 'Dashboard Tổng Quan',
    map: 'Bản Đồ & Định Vị Tuyến Gom',
    smart_bins: 'Quản Lý Thùng Rác',
    operations: 'Lịch Sử Hoạt Động & Thu Gom',
    employees: 'Quản Lý Nhân Sự & Sự Cố',
    firmware: 'Cập Nhật Firmware & Nạp OTA',
    settings: 'Cài Đặt & Cấu Hình Hệ Thống'
  };

  const title = titles[activeView] || 'SmartWaste Admin';

  // Filter overfull / critical bins in real-time (Strictly online devices)
  const overfullBins = (bins || []).filter(b => b.is_online === true && (b.level_percent || 0) >= 85);
  const nearFullBins = (bins || []).filter(b => b.is_online === true && (b.level_percent || 0) >= 70 && (b.level_percent || 0) < 85);
  const offlineBins = (bins || []).filter(b => b.is_online === false);
  const alertCount = overfullBins.length;

  return (
    <header style={{
      height: '60px',
      backgroundColor: '#ffffff',
      borderBottom: '1px solid #e2e8f0',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '0 28px',
      position: 'sticky',
      top: 0,
      zIndex: 100
    }}>
      {/* Left: Hamburger Toggle & Title */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
        <button
          onClick={onToggleSidebar}
          style={{
            background: 'none',
            border: 'none',
            padding: '6px',
            borderRadius: '8px',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#1e293b',
            transition: 'background-color 150ms ease'
          }}
          onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#f1f5f9'; }}
          onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'transparent'; }}
          title="Thu gọn / Mở rộng Menu"
        >
          <Menu size={20} />
        </button>

        <h1 style={{
          fontSize: '17px',
          fontWeight: 800,
          color: '#111a4a',
          letterSpacing: '-0.3px',
          margin: 0
        }}>
          {title}
        </h1>
      </div>

      {/* Right: Real-time Bell + Time & Quick Action */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
        
        {/* 🔔 REAL-TIME NOTIFICATION BELL WITH OVERFULL ALERT */}
        <div style={{ position: 'relative' }} ref={bellRef}>
          <button
            onClick={() => setBellOpen(prev => !prev)}
            title={alertCount > 0 ? `Có ${alertCount} thùng rác đang quá tải!` : 'Thông báo hệ thống'}
            style={{
              position: 'relative',
              width: '38px',
              height: '38px',
              borderRadius: '10px',
              backgroundColor: alertCount > 0 ? '#fef2f2' : '#f8fafc',
              border: `1px solid ${alertCount > 0 ? '#fecaca' : '#e2e8f0'}`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
              color: alertCount > 0 ? '#dc2626' : '#64748b',
              transition: 'all 150ms ease'
            }}
          >
            <Bell size={18} className={alertCount > 0 ? 'bell-shake-animation' : ''} />
            
            {/* Real-time Badge Count */}
            {alertCount > 0 ? (
              <span style={{
                position: 'absolute',
                top: '-4px',
                right: '-4px',
                minWidth: '18px',
                height: '18px',
                borderRadius: '99px',
                backgroundColor: '#dc2626',
                color: '#ffffff',
                fontSize: '10px',
                fontWeight: 800,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '0 4px',
                boxShadow: '0 2px 5px rgba(220, 38, 38, 0.4)',
                border: '1.5px solid #ffffff'
              }}>
                {alertCount}
              </span>
            ) : (
              <span style={{
                position: 'absolute',
                top: '7px',
                right: '7px',
                width: '7px',
                height: '7px',
                borderRadius: '50%',
                backgroundColor: '#10b981'
              }} />
            )}
          </button>

          {/* Bell Notification Popover Dropdown */}
          {bellOpen && (
            <div style={{
              position: 'absolute',
              top: 'calc(100% + 8px)',
              right: 0,
              width: '340px',
              backgroundColor: '#ffffff',
              borderRadius: '14px',
              border: '1px solid #e2e8f0',
              boxShadow: '0 10px 30px rgba(0,0,0,0.15)',
              zIndex: 300,
              overflow: 'hidden',
              animation: 'fadeIn 180ms ease'
            }}>
              {/* Header */}
              <div style={{
                padding: '12px 16px',
                backgroundColor: alertCount > 0 ? '#fef2f2' : '#f8fafc',
                borderBottom: '1px solid #e2e8f0',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <span style={{ fontSize: '13px', fontWeight: 800, color: alertCount > 0 ? '#991b1b' : '#111a4a' }}>
                    {alertCount > 0 ? `Cảnh báo quá tải (${alertCount})` : 'Thông báo hệ thống'}
                  </span>
                </div>
                <button
                  onClick={() => setBellOpen(false)}
                  style={{
                    background: 'none',
                    border: 'none',
                    padding: '2px',
                    color: '#64748b',
                    cursor: 'pointer',
                    display: 'flex'
                  }}
                >
                  <X size={15} />
                </button>
              </div>

              {/* List of Overfull / Attention Bins */}
              <div style={{ maxHeight: '280px', overflowY: 'auto', padding: '6px' }}>
                {alertCount > 0 ? (
                  overfullBins.map(bin => (
                    <div
                      key={bin.device_id}
                      onClick={() => {
                        setBellOpen(false);
                        if (onSelectBinForMap) {
                          onSelectBinForMap(bin);
                        } else if (onNavigateTab) {
                          onNavigateTab('map');
                        }
                      }}
                      style={{
                        padding: '10px 12px',
                        borderRadius: '8px',
                        backgroundColor: '#fff',
                        border: '1px solid #fee2e2',
                        marginBottom: '6px',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '6px',
                        cursor: 'pointer',
                        transition: 'background-color 150ms ease, border-color 150ms ease'
                      }}
                      onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#fef2f2'; }}
                      onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#ffffff'; }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <span style={{ fontSize: '12px', fontWeight: 800, color: '#111a4a', fontFamily: 'monospace' }}>
                          #{bin.device_id}
                        </span>
                        <span style={{
                          fontSize: '11px',
                          fontWeight: 800,
                          color: '#dc2626',
                          backgroundColor: '#fef2f2',
                          padding: '2px 8px',
                          borderRadius: '6px',
                          border: '1px solid #fecaca'
                        }}>
                          {bin.level_percent || 0}% Đầy
                        </span>
                      </div>

                      <div style={{ fontSize: '11.5px', color: '#475569', lineHeight: 1.3 }}>
                        📍 {bin.name || bin.location || 'Chưa cập nhật địa chỉ'}
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '2px' }}>
                        <span style={{ fontSize: '10px', color: '#94a3b8' }}>
                          Cập nhật: Vừa xong
                        </span>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setBellOpen(false);
                            if (onSelectBinForMap) {
                              onSelectBinForMap(bin);
                            } else if (onNavigateTab) {
                              onNavigateTab('map');
                            }
                          }}
                          style={{
                            padding: '4px 9px',
                            borderRadius: '6px',
                            backgroundColor: '#dc2626',
                            color: '#ffffff',
                            border: 'none',
                            fontSize: '10.5px',
                            fontWeight: 700,
                            cursor: 'pointer',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '4px',
                            boxShadow: '0 2px 4px rgba(220, 38, 38, 0.25)'
                          }}
                        >
                          <span>Điều phối xe</span>
                          <ArrowRight size={11} />
                        </button>
                      </div>
                    </div>
                  ))
                ) : (
                  <div style={{ padding: '24px 16px', textAlign: 'center' }}>
                    <CheckCircle2 size={32} color="#10b981" style={{ margin: '0 auto 8px auto' }} />
                    <div style={{ fontSize: '13px', fontWeight: 700, color: '#111a4a' }}>
                      Tất cả thùng rác an toàn
                    </div>
                    <div style={{ fontSize: '11.5px', color: '#64748b', marginTop: '3px' }}>
                      Không có thùng rác nào vượt mức 85%.
                    </div>
                  </div>
                )}
              </div>

              {/* Footer Action */}
              <div style={{
                padding: '10px 14px',
                borderTop: '1px solid #e2e8f0',
                backgroundColor: '#f8fafc',
                textAlign: 'center'
              }}>
                <button
                  onClick={() => {
                    setBellOpen(false);
                    if (onOpenMap) onOpenMap();
                    else if (onNavigateTab) onNavigateTab('map');
                  }}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#2563eb',
                    fontSize: '11.5px',
                    fontWeight: 700,
                    cursor: 'pointer',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '4px'
                  }}
                >
                  <span>Mở Bản đồ Giám sát Tuyến gom</span>
                  <ArrowRight size={12} />
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Real-time Clock - Bold, Black & Prominent */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          fontSize: '14.5px',
          fontWeight: 800,
          color: '#0f172a',
          fontFamily: 'var(--font-mono)',
          letterSpacing: '0.02em',
          backgroundColor: '#f8fafc',
          padding: '6px 12px',
          borderRadius: '8px',
          border: '1px solid #e2e8f0'
        }}>
          <Clock size={15} color="#0f172a" />
          <span>{timeStr}</span>
        </div>

        {activeView === 'dashboard' && (
          <button
            onClick={onOpenMap}
            className="btn-primary"
            style={{ padding: '7px 14px', fontSize: '12px', borderRadius: '8px' }}
          >
            <Navigation size={13} />
            <span>Bản đồ Tuyến gom</span>
          </button>
        )}
      </div>
    </header>
  );
}

