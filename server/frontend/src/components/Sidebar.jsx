import React from 'react';
import { 
  LayoutDashboard, 
  MapPin, 
  Trash2,
  Truck, 
  Users, 
  Settings,
  LogOut, 
  ChevronLeft, 
  ChevronRight,
  Radio,
  History,
  Cpu
} from 'lucide-react';
import logoImg from '../assets/logo.png';

export default function Sidebar({ 
  activeView, 
  setActiveView, 
  user, 
  onLogout, 
  socketConnected, 
  bins, 
  collapsed, 
  onToggleCollapse 
}) {
  const isAdmin = user?.role === 'admin';

  const navItems = [
    { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { id: 'map', label: 'Bản đồ & Định vị', icon: MapPin },
    { id: 'smart_bins', label: 'Smart Bins', icon: Trash2 },
    { id: 'operations', label: 'Lịch sử & Vận hành', icon: History },
    ...(isAdmin ? [
      { id: 'employees', label: 'Quản lý nhân sự', icon: Users },
      { id: 'firmware', label: 'Cập nhật Firmware', icon: Cpu }
    ] : []),
    { id: 'settings', label: 'Cài đặt hệ thống', icon: Settings }
  ];

  return (
    <aside style={{
      width: collapsed ? '72px' : '260px',
      height: '100vh',
      position: 'fixed',
      top: 0,
      left: 0,
      backgroundColor: '#ffffff',
      borderRight: '1px solid #e2e8f0',
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'space-between',
      zIndex: 200,
      userSelect: 'none',
      transition: 'width 220ms cubic-bezier(0.4, 0, 0.2, 1)',
      overflow: 'hidden'
    }}>
      {/* Top Section: Brand & Nav Links */}
      <div>
        {/* Brand Header */}
        <div style={{
          height: '60px',
          padding: collapsed ? '0 18px' : '0 20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: collapsed ? 'center' : 'space-between',
          borderBottom: '1px solid #e2e8f0',
          transition: 'padding 220ms ease'
        }}>
          <div 
            onClick={() => setActiveView('dashboard')}
            style={{ 
              display: 'flex', 
              alignItems: 'center', 
              gap: '10px',
              cursor: 'pointer'
            }}
            title="SmartWaste Admin"
          >
            <img 
              src={logoImg} 
              alt="Logo" 
              style={{ 
                width: '32px', 
                height: '32px', 
                objectFit: 'contain', 
                borderRadius: '8px',
                flexShrink: 0
              }} 
            />
            {!collapsed && (
              <div style={{
                fontSize: '18px',
                fontWeight: 800,
                color: '#111a4a',
                letterSpacing: '-0.5px',
                whiteSpace: 'nowrap',
                lineHeight: 1
              }}>
                <span>Smart</span>
                <span style={{ color: 'var(--color-signal-red)' }}>Waste</span>
              </div>
            )}
          </div>

          {!collapsed && (
            <button
              onClick={onToggleCollapse}
              style={{
                background: 'none',
                border: 'none',
                color: '#94a3b8',
                cursor: 'pointer',
                padding: '4px',
                borderRadius: '6px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
              title="Thu gọn menu"
            >
              <ChevronLeft size={16} />
            </button>
          )}
        </div>

        {/* Navigation Menu */}
        <nav style={{ padding: collapsed ? '14px 10px' : '14px 12px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeView === item.id;

            return (
              <button
                key={item.id}
                onClick={() => setActiveView(item.id)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  width: '100%',
                  padding: collapsed ? '12px 0' : '10px 14px',
                  justifyContent: collapsed ? 'center' : 'flex-start',
                  borderRadius: '10px',
                  border: 'none',
                  backgroundColor: isActive ? '#ecfdf5' : 'transparent',
                  color: isActive ? '#10b981' : '#475569',
                  fontWeight: isActive ? 700 : 500,
                  fontSize: '13.5px',
                  cursor: 'pointer',
                  transition: 'all 150ms ease',
                  position: 'relative'
                }}
                onMouseEnter={(e) => {
                  if (!isActive) e.currentTarget.style.backgroundColor = '#f8fafc';
                }}
                onMouseLeave={(e) => {
                  if (!isActive) e.currentTarget.style.backgroundColor = 'transparent';
                }}
                title={collapsed ? item.label : undefined}
              >
                <Icon size={19} color={isActive ? '#10b981' : '#64748b'} style={{ flexShrink: 0 }} />
                {!collapsed && (
                  <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {item.label}
                  </span>
                )}
              </button>
            );
          })}
        </nav>
      </div>

      {/* Bottom Section: Profile & Logout */}
      <div style={{
        padding: collapsed ? '14px 10px' : '14px 16px',
        borderTop: '1px solid #e2e8f0',
        backgroundColor: '#ffffff'
      }}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: collapsed ? 'center' : 'space-between',
          gap: '10px'
        }}>
          {/* User Avatar & Info */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            overflow: 'hidden'
          }}>
            <div style={{
              width: '36px',
              height: '36px',
              borderRadius: '50%',
              backgroundColor: isAdmin ? '#111a4a' : '#10b981',
              color: '#ffffff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 800,
              fontSize: '14px',
              flexShrink: 0
            }}>
              {(user?.full_name || user?.username || 'A').charAt(0).toUpperCase()}
            </div>

            {!collapsed && (
              <div style={{ overflow: 'hidden' }}>
                <div style={{
                  fontSize: '13px',
                  fontWeight: 700,
                  color: '#111a4a',
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis'
                }}>
                  {user?.full_name || user?.username || 'Quản trị viên'}
                </div>
                <div style={{
                  fontSize: '11px',
                  color: '#64748b',
                  whiteSpace: 'nowrap',
                  textTransform: 'uppercase',
                  fontWeight: 600
                }}>
                  {isAdmin ? 'Quản trị viên' : 'Nhân viên'}
                </div>
              </div>
            )}
          </div>

          {/* Logout Button */}
          <button
            onClick={onLogout}
            style={{
              background: 'none',
              border: 'none',
              color: '#94a3b8',
              cursor: 'pointer',
              padding: '6px',
              borderRadius: '6px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              transition: 'all 150ms ease',
              flexShrink: 0
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.color = '#ef4444';
              e.currentTarget.style.backgroundColor = '#fef2f2';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.color = '#94a3b8';
              e.currentTarget.style.backgroundColor = 'transparent';
            }}
            title="Đăng xuất"
          >
            <LogOut size={16} />
          </button>
        </div>
      </div>
    </aside>
  );
}
