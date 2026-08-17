import React, { useState } from 'react';
import Sidebar from '../components/Sidebar';
import Header from '../components/Header';

export default function DashboardLayout({
  activeView,
  setActiveView,
  user,
  onLogout,
  socketConnected,
  databaseConnected,
  bins,
  onOpenMap,
  onSelectBinForMap,
  children
}) {
  const [collapsed, setCollapsed] = useState(false);

  const toggleSidebar = () => {
    setCollapsed(prev => !prev);
  };

  const sidebarWidth = collapsed ? 72 : 260;

  return (
    <div style={{ minHeight: '100vh', display: 'flex', backgroundColor: 'var(--color-bg-subtle)' }}>
      {/* Fixed Left Sidebar Navigation */}
      <Sidebar
        activeView={activeView}
        setActiveView={setActiveView}
        user={user}
        onLogout={onLogout}
        socketConnected={socketConnected}
        bins={bins}
        collapsed={collapsed}
        onToggleCollapse={toggleSidebar}
      />

      {/* Main Content Area Layout Container (Shared Across All Pages) */}
      <div style={{
        marginLeft: `${sidebarWidth}px`,
        flex: 1,
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        width: `calc(100% - ${sidebarWidth}px)`,
        transition: 'margin-left 220ms cubic-bezier(0.4, 0, 0.2, 1), width 220ms cubic-bezier(0.4, 0, 0.2, 1)'
      }}>
        {/* Top Header Bar */}
        <Header
          activeView={activeView}
          onToggleSidebar={toggleSidebar}
          onOpenMap={onOpenMap}
          bins={bins}
          onNavigateTab={setActiveView}
          onSelectBinForMap={onSelectBinForMap}
        />

        {/* Dynamic Full-Width Page Content Viewport */}
        <main style={{
          flex: 1,
          padding: '24px 28px',
          width: '100%',
          boxSizing: 'border-box'
        }}>
          {children}
        </main>

        {/* Single Global Dashboard Footer */}
        <footer style={{
          padding: '14px 28px',
          borderTop: '1px solid #e2e8f0',
          backgroundColor: '#ffffff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          fontSize: '12px',
          color: '#94a3b8'
        }}>
          <span>© 2026 Smart Waste Management. All rights reserved.</span>
          <span>Phiên bản 1.0.0</span>
        </footer>
      </div>
    </div>
  );
}
