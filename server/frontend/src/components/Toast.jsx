import React from 'react';
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';

export default function Toast({ toasts, onDismiss }) {
  if (!toasts || toasts.length === 0) return null;

  return (
    <div style={{
      position: 'fixed',
      bottom: '24px',
      right: '24px',
      display: 'flex',
      flexDirection: 'column',
      gap: '10px',
      zIndex: 9999,
      maxWidth: '380px',
      width: 'calc(100vw - 48px)'
    }}>
      {toasts.slice(-3).map((toast) => {
        const isError = toast.type === 'error';
        const isSuccess = toast.type === 'success';

        return (
          <div
            key={toast.id}
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: '12px',
              padding: '14px 16px',
              backgroundColor: 'var(--color-paper-white)',
              color: 'var(--color-wellfound-ink)',
              borderRadius: 'var(--radius-buttons)',
              border: `1px solid ${isError ? 'rgba(236, 46, 58, 0.3)' : isSuccess ? 'rgba(13, 130, 73, 0.3)' : 'var(--color-ash-gray)'}`,
              boxShadow: '0 8px 24px rgba(5, 19, 22, 0.12)',
              animation: 'toastSlideIn 0.2s cubic-bezier(0.215, 0.61, 0.355, 1)'
            }}
          >
            <div style={{ marginTop: '2px' }}>
              {isError && <AlertCircle size={18} color="var(--color-signal-red)" />}
              {isSuccess && <CheckCircle2 size={18} color="var(--color-success)" />}
              {!isError && !isSuccess && <Info size={18} color="var(--color-wellfound-ink)" />}
            </div>
            
            <div style={{ flex: 1, fontSize: '14px', lineHeight: '1.4' }}>
              {toast.message}
            </div>

            <button
              onClick={() => onDismiss(toast.id)}
              style={{
                background: 'none',
                border: 'none',
                padding: '2px',
                color: '#888',
                cursor: 'pointer'
              }}
              title="Đóng"
            >
              <X size={16} />
            </button>
          </div>
        );
      })}
    </div>
  );
}
