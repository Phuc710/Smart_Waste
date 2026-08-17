import React, { useState } from 'react';
import { ArrowRight, AlertCircle, ShieldCheck, Radio, Navigation } from 'lucide-react';
import { api } from '../services/api';
import heroImage from '../assets/login-hero.jpg';
import logoImg from '../assets/logo.png';

export default function LoginPage({ onLoginSuccess, onNotify }) {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('SmartWaste@2026');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMsg('');
    setLoading(true);

    try {
      const data = await api.login(username, password);
      if (data.user) {
        onNotify(`Chào mừng trở lại, ${data.user.full_name || data.user.username}!`, 'success');
        onLoginSuccess(data.user);
      } else {
        throw new Error(data.error || 'Đăng nhập không thành công.');
      }
    } catch (err) {
      setErrorMsg(err.message || 'Sai tên đăng nhập hoặc mật khẩu.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'grid',
      gridTemplateColumns: 'minmax(460px, 32%) 1fr',
      backgroundColor: '#ffffff',
      fontFamily: 'var(--font-sans)',
      overflow: 'hidden'
    }} className="login-split-container">
      
      {/* LEFT COLUMN: Enterprise Login Form */}
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        padding: 'clamp(32px, 4.5vw, 64px)',
        backgroundColor: '#ffffff',
        zIndex: 2,
        overflowY: 'auto'
      }}>
        
        {/* Top Brand Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <img
              src={logoImg}
              alt="SmartWaste"
              style={{
                width: '40px',
                height: '40px',
                objectFit: 'contain',
                borderRadius: '8px'
              }}
            />
            <div>
              <div style={{
                fontSize: '20px',
                fontWeight: 800,
                letterSpacing: '-0.6px',
                color: '#111a4a',
                lineHeight: 1.1
              }}>
                Smart<span>Waste</span>
              </div>
              <small style={{ fontSize: '10px', fontWeight: 600, color: '#167e6c', letterSpacing: '0.05em', textTransform: 'uppercase' }}>
                Admin
              </small>
            </div>
          </div>

          <div style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            padding: '6px 12px',
            borderRadius: '9999px',
            backgroundColor: '#edf8f3',
            border: '1px solid rgba(22, 126, 108, 0.25)',
            fontSize: '11px',
            fontWeight: 700,
            color: '#167e6c',
            whiteSpace: 'nowrap'
          }}>
            <Radio size={13} color="#167e6c" />
            <span>Hạ tầng IoT</span>
          </div>
        </div>

        {/* Center Main Form Area */}
        <div style={{ maxWidth: '420px', width: '100%', margin: '40px auto' }}>
          <div style={{ marginBottom: '32px' }}>
            <h1 style={{
              fontSize: 'clamp(24px, 2.2vw, 30px)',
              fontWeight: 800,
              color: '#111a4a',
              letterSpacing: '-0.8px',
              marginBottom: '8px',
              lineHeight: 1.2
            }}>
              Đăng nhập Quản trị
            </h1>
          </div>

          {/* Error message banner */}
          {errorMsg && (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              padding: '12px 14px',
              borderRadius: '10px',
              backgroundColor: '#fff4ed',
              border: '1px solid rgba(236, 101, 43, 0.3)',
              color: '#ec652b',
              fontSize: '13px',
              fontWeight: 500,
              marginBottom: '20px',
              animation: 'fadeIn 200ms ease-out'
            }}>
              <AlertCircle size={17} style={{ flexShrink: 0 }} />
              <span>{errorMsg}</span>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit} style={{ display: 'grid', gap: '18px' }}>
            <div>
              <label style={{
                display: 'block',
                fontSize: '12px',
                fontWeight: 700,
                color: '#3b3e47',
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
                marginBottom: '6px'
              }}>
                Tên đăng nhập
              </label>
              <input
                type="text"
                required
                autoComplete="username"
                placeholder="Nhập tên đăng nhập"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                style={{
                  width: '100%',
                  padding: '13px 15px',
                  borderRadius: '10px',
                  border: '1px solid #e3e4e8',
                  backgroundColor: '#f6f6f8',
                  color: '#011821',
                  fontSize: '14px',
                  fontWeight: 500,
                  outline: 'none',
                  transition: 'all 150ms ease'
                }}
                onFocus={(e) => {
                  e.target.style.borderColor = '#111a4a';
                  e.target.style.backgroundColor = '#ffffff';
                  e.target.style.boxShadow = '0 0 0 3px rgba(17, 26, 74, 0.08)';
                }}
                onBlur={(e) => {
                  e.target.style.borderColor = '#e3e4e8';
                  e.target.style.backgroundColor = '#f6f6f8';
                  e.target.style.boxShadow = 'none';
                }}
              />
            </div>

            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                <label style={{
                  fontSize: '12px',
                  fontWeight: 700,
                  color: '#3b3e47',
                  textTransform: 'uppercase',
                  letterSpacing: '0.04em'
                }}>
                  Mật khẩu
                </label>
               
              </div>
              <input
                type="password"
                required
                autoComplete="current-password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                style={{
                  width: '100%',
                  padding: '13px 15px',
                  borderRadius: '10px',
                  border: '1px solid #e3e4e8',
                  backgroundColor: '#f6f6f8',
                  color: '#011821',
                  fontSize: '14px',
                  fontWeight: 500,
                  outline: 'none',
                  transition: 'all 150ms ease'
                }}
                onFocus={(e) => {
                  e.target.style.borderColor = '#111a4a';
                  e.target.style.backgroundColor = '#ffffff';
                  e.target.style.boxShadow = '0 0 0 3px rgba(17, 26, 74, 0.08)';
                }}
                onBlur={(e) => {
                  e.target.style.borderColor = '#e3e4e8';
                  e.target.style.backgroundColor = '#f6f6f8';
                  e.target.style.boxShadow = 'none';
                }}
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              style={{
                width: '100%',
                padding: '14px 20px',
                borderRadius: '10px',
                backgroundColor: '#111a4a',
                color: '#ffffff',
                fontSize: '14px',
                fontWeight: 700,
                border: 'none',
                cursor: loading ? 'wait' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                boxShadow: '0 4px 14px rgba(17, 26, 74, 0.18)',
                transition: 'all 150ms ease',
                marginTop: '6px'
              }}
              onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#011821'; }}
              onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#111a4a'; }}
            >
              <span>{loading ? 'Đang xác thực thông tin...' : 'Đăng nhập'}</span>
              <ArrowRight size={16} />
            </button>
          </form>
        </div>

        {/* Bottom Trust & Security Badges */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingTop: '16px',
          borderTop: '1px solid #f0f0f3',
          fontSize: '11px',
          color: '#7c7f88'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <ShieldCheck size={14} color="#167e6c" />
            <span>Admin</span>
          </div>
          <div>Smart Waste v1.0</div>
        </div>

      </div>

      {/* RIGHT COLUMN: Modern Card Showcase with Padding & Rounded Corners */}
      <div style={{
        padding: '16px',
        backgroundColor: '#ffffff',
        display: 'flex'
      }} className="login-hero-panel">
        
        <div style={{
          position: 'relative',
          flex: 1,
          borderRadius: '24px',
          overflow: 'hidden',
          backgroundColor: '#0a191f',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          padding: 'clamp(24px, 3.5vw, 48px)',
          boxShadow: '0 12px 32px rgba(5, 19, 22, 0.08)'
        }}>
          {/* Background Image Layer */}
          <img
            src={heroImage}
            alt="Hệ thống Quản trị & Điều phối Thu gom Rác Thông minh"
            style={{
              position: 'absolute',
              inset: 0,
              width: '100%',
              height: '100%',
              objectFit: 'cover',
              objectPosition: 'center 45%',
              opacity: 0.95
            }}
          />

          {/* Subtle Vignette Gradient Overlay */}
          <div style={{
            position: 'absolute',
            inset: 0,
            background: 'linear-gradient(135deg, rgba(17, 26, 74, 0.4) 0%, rgba(1, 24, 33, 0.2) 50%, rgba(1, 24, 33, 0.75) 100%)',
            pointerEvents: 'none'
          }} />

          <div style={{ flex: 1 }} />

          {/* Bottom Feature Card */}
          <div style={{ position: 'relative', zIndex: 2, maxWidth: '480px', alignSelf: 'flex-end', marginLeft: 'auto' }}>
            <div style={{
              padding: '16px 20px',
              borderRadius: '16px',
              backgroundColor: 'rgba(255, 255, 255, 0.92)',
              backdropFilter: 'blur(16px)',
              border: '1px solid rgba(255, 255, 255, 0.8)',
              boxShadow: '0 12px 30px rgba(0, 0, 0, 0.15)'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                <Navigation size={15} color="#ec652b" />
                <strong style={{ fontSize: '14px', color: '#111a4a', fontWeight: 800 }}>
                  Giám sát Thời gian thực & Tối ưu Tuyến gom
                </strong>
              </div>
              <p style={{ margin: 0, fontSize: '12.5px', color: '#3b3e47', lineHeight: 1.4 }}>
                Thu thập dữ liệu cảm biến, tự động cảnh báo ngưỡng đầy và phân bổ xe thu gom thông minh.
              </p>
            </div>
          </div>
        </div>

      </div>

      {/* Responsive Styles for Mobile / Tablet Breakpoints */}
      <style>{`
        @media (max-width: 1024px) {
          .login-split-container {
            grid-template-columns: 1fr !important;
          }
          .login-hero-panel {
            display: none !important;
          }
        }
      `}</style>

    </div>
  );
}
