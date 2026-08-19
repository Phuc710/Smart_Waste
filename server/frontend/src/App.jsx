import React, { useEffect, useState, useCallback } from 'react';
import DashboardLayout from './layouts/DashboardLayout';
import DashboardPage from './pages/DashboardPage';
import MapPage from './pages/MapPage';
import SmartBinsPage from './pages/SmartBinsPage';
import OperationsPage from './pages/OperationsPage';
import EmployeesPage from './pages/EmployeesPage';
import SettingsPage from './pages/SettingsPage';
import FirmwarePage from './pages/FirmwarePage';
import LoginPage from './pages/LoginPage';
import Toast from './components/Toast';
import { api } from './services/api';
import { getSocket, connectSocket, disconnectSocket } from './services/socket';
import { ShieldCheck } from 'lucide-react';
import logoImg from './assets/logo.png';

export default function App() {
  const [user, setUser] = useState(null);
  const [loadingAuth, setLoadingAuth] = useState(true);
  const [loggingIn, setLoggingIn] = useState(false);
  const [loginProgress, setLoginProgress] = useState(20);
  const [loginStatusText, setLoginStatusText] = useState('Đang kết nối trung tâm máy chủ IoT...');
  const [loginFadeOut, setLoginFadeOut] = useState(false);
  const [activeView, setActiveView] = useState('dashboard');
  const [bins, setBins] = useState([]);
  const [selectedBinForMap, setSelectedBinForMap] = useState(null);
  const [socketConnected, setSocketConnected] = useState(false);
  const [databaseConnected, setDatabaseConnected] = useState(true);
  const [toasts, setToasts] = useState([]);

  // Toast Notifier
  const notify = useCallback((message, type = 'info') => {
    const id = Date.now() + Math.random().toString(36).substring(2, 5);
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 4000);
  }, []);

  const dismissToast = (id) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  };

  // Fetch Bins from REST
  const fetchBins = useCallback(async () => {
    try {
      const data = await api.getBins();
      if (Array.isArray(data)) {
        setBins(data);
      }
    } catch (_) { }
  }, []);

  // Check Auth Session on initial load (Nhanh, gọn, nhẹ)
  useEffect(() => {
    async function checkAuth() {
      try {
        const data = await api.getMe();
        if (data.user) {
          setUser(data.user);
        } else {
          setUser(null);
        }
      } catch (_) {
        setUser(null);
      } finally {
        setLoadingAuth(false);
      }
    }
    checkAuth();
  }, []);

  // Handle Login Sequence (Chỉ kích hoạt khi bấm Đăng nhập & nạp dữ liệu thật)
  const handleLoginSuccess = useCallback(async (loggedInUser) => {
    setLoggingIn(true);
    setLoginFadeOut(false);
    setLoginProgress(20);
    setLoginStatusText('Đang kết nối trung tâm máy chủ IoT...');

    // 20% -> 55%: Nạp bản đồ GIS & quyền hạn
    await new Promise(r => setTimeout(r, 180));
    setLoginProgress(55);
    setLoginStatusText('Đang nạp bản đồ GIS & quyền hạn quản trị...');

    try {
      await fetchBins();
    } catch (_) { }

    // 55% -> 85%: Đồng bộ cảm biến IoT & đội xe
    await new Promise(r => setTimeout(r, 200));
    setLoginProgress(85);
    setLoginStatusText('Đang đồng bộ cảm biến IoT & đội xe thu gom...');

    // 85% -> 100%: Sẵn sàng
    await new Promise(r => setTimeout(r, 180));
    setLoginProgress(100);
    const userName = loggedInUser?.full_name || loggedInUser?.username || 'Quản trị viên';
    setLoginStatusText(`Sẵn sàng! Chào mừng ${userName}`);

    // Chuyển cảnh mượt mà
    await new Promise(r => setTimeout(r, 260));
    setLoginFadeOut(true);
    await new Promise(r => setTimeout(r, 220));
    setUser(loggedInUser);
    setLoggingIn(false);
    setLoginFadeOut(false);
  }, [fetchBins]);

  // Realtime Socket.IO Connection
  useEffect(() => {
    if (!user) {
      disconnectSocket();
      setSocketConnected(false);
      return;
    }

    fetchBins();
    const socket = connectSocket();

    const onConnect = () => setSocketConnected(true);
    const onDisconnect = () => setSocketConnected(false);

    const onInitialBins = (initialList) => {
      if (Array.isArray(initialList) && initialList.length > 0) {
        setBins(prev => {
          const map = new Map(prev.map(b => [b.device_id, b]));
          for (const b of initialList) map.set(b.device_id, { ...(map.get(b.device_id) || {}), ...b });
          return [...map.values()];
        });
      }
    };

    const onBinsSnapshot = (snapshotList) => {
      if (Array.isArray(snapshotList)) {
        setBins(prev => {
          const map = new Map(prev.map(b => [b.device_id, b]));
          for (const b of snapshotList) map.set(b.device_id, { ...(map.get(b.device_id) || {}), ...b });
          return [...map.values()];
        });
      }
    };

    const onBinData = (payload) => {
      if (!payload) return;
      const binId = payload.binId || payload.device_id || payload.id;
      const data = payload.data || payload;
      if (!binId || !data) return;

      setBins(prev => {
        const map = new Map(prev.map(b => [b.device_id, b]));
        const existing = map.get(binId) || { device_id: binId };
        map.set(binId, {
          ...existing,
          ...data,
          device_id: binId,
          level_percent: Number(data.level_percent ?? data.levelPercent ?? existing.level_percent ?? 0),
          is_online: data.is_online !== undefined ? Boolean(data.is_online) : true,
          last_seen: data.last_seen || data.lastSeen || new Date().toISOString()
        });
        return [...map.values()];
      });
    };

    const onDbStatus = (status) => {
      setDatabaseConnected(status?.connected ?? true);
      if (!status?.connected && status?.message) {
        notify(`Lỗi kết nối cơ sở dữ liệu: ${status.message}`, 'error');
      }
    };

    socket.on('connect', onConnect);
    socket.on('disconnect', onDisconnect);
    socket.on('initialBins', onInitialBins);
    socket.on('binsSnapshot', onBinsSnapshot);
    socket.on('binData', onBinData);
    socket.on('databaseStatus', onDbStatus);

    return () => {
      socket.off('connect', onConnect);
      socket.off('disconnect', onDisconnect);
      socket.off('initialBins', onInitialBins);
      socket.off('binsSnapshot', onBinsSnapshot);
      socket.off('binData', onBinData);
      socket.off('databaseStatus', onDbStatus);
    };
  }, [user, fetchBins, notify]);

  // Command Action (2-way Handshake with ESP32)
  const handleSendCommand = async (binId, action) => {
    const socket = getSocket();
    const actionLabel = {
      OPEN: 'Mở nắp',
      CLOSE: 'Đóng nắp',
      AUTO: 'Chế độ Tự động',
      MANUAL: 'Chế độ Thủ công',
      PAUSE: 'Tạm dừng thu gom',
      RESUME: 'Tiếp tục thu gom'
    }[action] || action;

    // Optimistic UI
    setBins(prev => prev.map(b => {
      if (b.device_id === binId) {
        return {
          ...b,
          last_command: action,
          command_status: 'sent',
          ...(action === 'OPEN' ? { state: 'OPEN', servo_angle: 90, control_mode: 'MANUAL' } : {}),
          ...(action === 'CLOSE' ? { state: 'CLOSED', servo_angle: 0, control_mode: 'MANUAL' } : {}),
          ...(action === 'AUTO' ? { control_mode: 'AUTO', collection_paused: false } : {}),
          ...(action === 'MANUAL' ? { control_mode: 'MANUAL' } : {}),
          ...(action === 'PAUSE' ? { collection_paused: true } : {}),
          ...(action === 'RESUME' ? { collection_paused: false, control_mode: 'AUTO' } : {})
        };
      }
      return b;
    }));

    if (socket && socket.connected) {
      return new Promise((resolve) => {
        socket.emit('lidCommand', { binId, action }, (ack) => {
          if (ack?.ok) {
            notify(ack?.message || `Thiết bị #${binId} đã thực thi "${actionLabel}" thành công!`, 'success');
          } else {
            notify(ack?.message || `Thiết bị #${binId} không phản hồi lệnh "${actionLabel}".`, 'error');
            // Rollback optimistic changes on failure
            fetchBins();
          }
          resolve(ack);
        });
      });
    } else {
      try {
        const res = await api.sendCommand(binId, action);
        notify(res?.message || `Thiết bị #${binId} đã thực thi "${actionLabel}" thành công!`, 'success');
      } catch (err) {
        notify(`Lỗi gửi lệnh tới #${binId}: ${err.message}`, 'error');
        fetchBins();
      }
    }
  };

  const handleLogout = async () => {
    try {
      await api.logout();
    } catch (_) { }
    setUser(null);
    disconnectSocket();
    notify('Đã đăng xuất khỏi hệ thống.', 'info');
  };

  const handleSelectBinForMap = (bin) => {
    if (!bin) return;
    setSelectedBinForMap({ ...bin, _focusTs: Date.now() });
    setActiveView('map');
  };

  if (loadingAuth) {
    return (
      <div style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'radial-gradient(ellipse at 50% 40%, #f0fdf4 0%, #f8fafc 60%, #f1f5f9 100%)',
        fontFamily: 'var(--font-sans)',
        position: 'relative',
        overflow: 'hidden'
      }}>
        {/* Ambient Glow */}
        <div style={{
          position: 'absolute',
          width: '320px',
          height: '320px',
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(16, 185, 129, 0.12) 0%, transparent 70%)',
          animation: 'pulseGlow 2.5s ease-in-out infinite',
          pointerEvents: 'none'
        }} />

        {/* Center Content */}
        <div style={{
          position: 'relative',
          zIndex: 2,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center'
        }}>
          {/* Brand Logo Box */}
          <div style={{
            width: '68px',
            height: '68px',
            borderRadius: '20px',
            backgroundColor: '#ecfdf5',
            border: '1px solid #bbf7d0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 8px 20px -4px rgba(16, 185, 129, 0.2)',
            marginBottom: '14px'
          }}>
            <img src={logoImg} alt="SmartWaste Logo" style={{ width: '42px', height: '42px', objectFit: 'contain' }} />
          </div>

          {/* Brand Title */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '3px', marginBottom: '16px' }}>
            <h1 style={{ fontSize: '24px', fontWeight: 900, color: '#0f172a', margin: 0, letterSpacing: '-0.03em' }}>
              Smart<span style={{ color: '#10b981' }}>Waste</span>
            </h1>
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#ef4444', marginBottom: '-6px' }} />
          </div>

          {/* Shimmer Progress Line */}
          <div style={{
            width: '80px',
            height: '4px',
            borderRadius: '9999px',
            backgroundColor: '#e2e8f0',
            overflow: 'hidden',
            position: 'relative'
          }}>
            <div style={{
              position: 'absolute',
              top: 0,
              bottom: 0,
              borderRadius: '9999px',
              background: 'linear-gradient(90deg, #10b981, #3b82f6)',
              animation: 'indeterminateBar 1.2s infinite'
            }} />
          </div>
        </div>
      </div>
    );
  }

  if (loggingIn) {
    return (
      <div style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'radial-gradient(ellipse at 50% 35%, #f0fdf4 0%, #f8fafc 55%, #f1f5f9 100%)',
        fontFamily: 'var(--font-sans)',
        position: 'relative',
        overflow: 'hidden',
        opacity: loginFadeOut ? 0 : 1,
        transform: loginFadeOut ? 'scale(1.02)' : 'scale(1)',
        transition: 'opacity 250ms cubic-bezier(0.4, 0, 0.2, 1), transform 250ms cubic-bezier(0.4, 0, 0.2, 1)'
      }}>
        {/* Background ambient light glow */}
        <div style={{
          position: 'absolute',
          width: '420px',
          height: '420px',
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(16, 185, 129, 0.15) 0%, rgba(59, 130, 246, 0.05) 70%, transparent 100%)',
          animation: 'pulseGlow 3s ease-in-out infinite',
          pointerEvents: 'none'
        }} />

        {/* Center Glass Card Container */}
        <div style={{
          position: 'relative',
          zIndex: 2,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          padding: '38px 42px',
          borderRadius: '24px',
          backgroundColor: 'rgba(255, 255, 255, 0.92)',
          backdropFilter: 'blur(16px)',
          border: '1px solid rgba(226, 232, 240, 0.9)',
          boxShadow: '0 20px 45px -10px rgba(16, 185, 129, 0.1), 0 8px 20px -5px rgba(0, 0, 0, 0.04)',
          maxWidth: '380px',
          width: '90%'
        }}>
          {/* Brand Logo with Glow */}
          <div style={{ position: 'relative', marginBottom: '16px' }}>
            <div style={{
              width: '68px',
              height: '68px',
              borderRadius: '20px',
              backgroundColor: '#ecfdf5',
              border: '1px solid #bbf7d0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 4px 16px rgba(16, 185, 129, 0.16)'
            }}>
              <img src={logoImg} alt="SmartWaste Logo" style={{ width: '42px', height: '42px', objectFit: 'contain' }} />
            </div>
          </div>

          {/* Brand Name */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '3px', marginBottom: '4px' }}>
            <h1 style={{ fontSize: '24px', fontWeight: 900, color: '#0f172a', margin: 0, letterSpacing: '-0.03em' }}>
              Smart<span style={{ color: '#10b981' }}>Waste</span>
            </h1>
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#ef4444', marginBottom: '-6px' }} />
          </div>

          <p style={{
            fontSize: '11px',
            fontWeight: 700,
            color: '#64748b',
            textTransform: 'uppercase',
            letterSpacing: '0.08em',
            margin: '0 0 22px 0',
            textAlign: 'center'
          }}>
            Hệ thống Quản trị & Điều phối GIS
          </p>

          {/* Progress Header with Percentage */}
          <div style={{ width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px', fontSize: '11.5px' }}>
            <span style={{ color: '#64748b', fontWeight: 600 }}>Khởi tạo hệ thống</span>
            <strong style={{ color: '#10b981', fontWeight: 800 }}>{Math.round(loginProgress)}%</strong>
          </div>

          {/* Dynamic Smooth Progress Bar */}
          <div style={{
            width: '100%',
            height: '6px',
            borderRadius: '9999px',
            backgroundColor: '#e2e8f0',
            position: 'relative',
            overflow: 'hidden',
            marginBottom: '16px'
          }}>
            <div style={{
              position: 'absolute',
              left: 0,
              top: 0,
              bottom: 0,
              width: `${loginProgress}%`,
              borderRadius: '9999px',
              background: 'linear-gradient(90deg, #10b981, #059669, #3b82f6)',
              transition: 'width 240ms cubic-bezier(0.4, 0, 0.2, 1)',
              boxShadow: '0 0 8px rgba(16, 185, 129, 0.4)'
            }} />
          </div>

          {/* Status Message */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '7px',
            fontSize: '12px',
            fontWeight: 600,
            color: '#475569',
            minHeight: '20px'
          }}>
            <span style={{
              width: '6px',
              height: '6px',
              borderRadius: '50%',
              backgroundColor: loginProgress >= 100 ? '#10b981' : '#3b82f6',
              animation: 'pulseGlow 1.2s ease-in-out infinite',
              flexShrink: 0
            }} />
            <span style={{ transition: 'all 200ms ease' }}>{loginStatusText}</span>
          </div>
        </div>

        {/* Bottom System Badge */}
        <div style={{
          position: 'absolute',
          bottom: '24px',
          display: 'flex',
          alignItems: 'center',
          gap: '6px',
          fontSize: '11px',
          fontWeight: 600,
          color: '#94a3b8'
        }}>
          <ShieldCheck size={13} color="#10b981" />
          <span>Smart City IoT Management • Phiên bản 1.0.0</span>
        </div>
      </div>
    );
  }

  if (!user) {
    return (
      <>
        <LoginPage onLoginSuccess={handleLoginSuccess} onNotify={notify} />
        <Toast toasts={toasts} onDismiss={dismissToast} />
      </>
    );
  }

  return (
    <>
      {/* Standard Shared Layout Container */}
      <DashboardLayout
        activeView={activeView}
        setActiveView={setActiveView}
        user={user}
        onLogout={handleLogout}
        socketConnected={socketConnected}
        databaseConnected={databaseConnected}
        bins={bins}
        onOpenMap={() => setActiveView('map')}
        onSelectBinForMap={handleSelectBinForMap}
      >
        {activeView === 'dashboard' && (
          <DashboardPage
            bins={bins}
            user={user}
            onSendCommand={handleSendCommand}
            onSelectBinForMap={handleSelectBinForMap}
            onNavigateTab={(tab) => setActiveView(tab)}
          />
        )}

        {activeView === 'map' && (
          <MapPage
            bins={bins}
            selectedBin={selectedBinForMap}
            onNotify={notify}
            onSendCommand={handleSendCommand}
          />
        )}

        {activeView === 'smart_bins' && (
          <SmartBinsPage
            bins={bins}
            onSendCommand={handleSendCommand}
            onSelectBinForMap={handleSelectBinForMap}
            onNotify={notify}
          />
        )}

        {activeView === 'operations' && (
          <OperationsPage
            bins={bins}
            onNotify={notify}
            onNavigateTab={(tab) => setActiveView(tab)}
            onSelectBinForMap={handleSelectBinForMap}
          />
        )}

        {activeView === 'employees' && (
          <EmployeesPage
            currentUser={user}
            onNotify={notify}
            onUpdateCurrentUser={(updated) => setUser(prev => ({ ...prev, ...updated }))}
          />
        )}

        {activeView === 'firmware' && (
          <FirmwarePage
            notify={notify}
            bins={bins}
            onOpenMap={() => setActiveView('map')}
          />
        )}

        {activeView === 'settings' && (
          <SettingsPage
            onNotify={notify}
          />
        )}
      </DashboardLayout>

      {/* Global Toast Container */}
      <Toast toasts={toasts} onDismiss={dismissToast} />
    </>
  );
}
