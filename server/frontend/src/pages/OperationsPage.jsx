import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { 
  Truck, 
  AlertTriangle, 
  Search, 
  RefreshCw, 
  Eye, 
  Download, 
  X, 
  ChevronLeft, 
  ChevronRight, 
  Users, 
  Bell, 
  Terminal, 
  CheckCircle2, 
  Clock, 
  MapPin, 
  Camera,
  Check
} from 'lucide-react';
import { api } from '../services/api';
import { getSocket } from '../services/socket';
import { formatVietnamTime, formatVietnamDate, formatVietnamDateTime } from '../utils/dateTime';

function fmtDur(s, e) {
  if (!s || !e) return '—';
  const d = Math.max(0, Math.floor((new Date(e) - new Date(s)) / 1000));
  const m = Math.floor(d / 60), sc = d % 60;
  return m === 0 ? d + ' giây' : m + ' phút' + (sc > 0 ? ' ' + sc + 's' : '');
}

function Bd({ children, c, bg, b }) {
  return (
    <span style={{ 
      padding: '3px 9px', 
      borderRadius: '999px', 
      fontSize: '11px', 
      fontWeight: 700, 
      backgroundColor: bg, 
      color: c, 
      border: '1px solid ' + b,
      display: 'inline-flex',
      alignItems: 'center',
      gap: '4px'
    }}>
      {children}
    </span>
  );
}

const TH = (a) => ({ 
  padding: '12px 18px', 
  textAlign: a || 'left', 
  backgroundColor: '#f8fafc', 
  borderBottom: '1px solid #e2e8f0', 
  color: '#64748b', 
  fontSize: '11px', 
  textTransform: 'uppercase', 
  letterSpacing: '.04em', 
  fontWeight: 700,
  whiteSpace: 'nowrap'
});

const TD = (a) => ({ 
  padding: '13px 18px', 
  textAlign: a || 'left', 
  borderBottom: '1px solid #f1f5f9', 
  verticalAlign: 'middle',
  whiteSpace: 'nowrap'
});

const ET = { 
  alert: { label: 'Alert', c: '#dc2626', bg: '#fef2f2', b: '#fecaca' }, 
  command: { label: 'Command', c: '#7c3aed', bg: '#f5f3ff', b: '#ddd6fe' }, 
  telemetry: { label: 'Telemetry', c: '#0284c7', bg: '#f0f9ff', b: '#bae6fd' } 
};

const etCfg = (t) => ET[t] || { label: t, c: '#475569', bg: '#f8fafc', b: '#e2e8f0' };

let operationsCache = {
  employees: [],
  binAlerts: [],
  incidents: [],
  jobs: [],
  sysLogs: [],
  lastFetched: 0
};

export default function OperationsPage({ bins = [], onNotify }) {
  const [tab, setTab]         = useState('collections');
  const [loading, setLoading] = useState(operationsCache.lastFetched === 0);
  const [employees, setEmployees] = useState(operationsCache.employees);
  const [binAlerts, setBinAlerts] = useState(operationsCache.binAlerts);
  const [incidents, setIncidents] = useState(operationsCache.incidents);
  const [jobs, setJobs]       = useState(operationsCache.jobs);
  const [sysLogs, setSysLogs] = useState(operationsCache.sysLogs);
  const [q, setQ]             = useState('');
  
  // Filter States
  const [roleF, setRoleF]     = useState('ALL');
  const [statF, setStatF]     = useState('ALL');
  const [aTypeF, setATypeF]   = useState('ALL');
  const [aStatF, setAStatF]   = useState('ALL');
  const [jStatF, setJStatF]   = useState('ALL');
  const [eTypeF, setETypeF]   = useState('ALL');
  
  const [page, setPage]       = useState(1);
  const [perPage, setPerPage] = useState(10);
  const [jobModal, setJobModal] = useState(null);
  const [alertModal, setAlertModal] = useState(null);
  const [logModal, setLogModal] = useState(null);

  const binMap = useMemo(() => {
    const map = new Map();
    (bins || []).forEach(b => {
      if (b.device_id) map.set(b.device_id, b);
    });
    return map;
  }, [bins]);

  const load = useCallback(async (isSilent = false) => {
    if (!isSilent && operationsCache.lastFetched === 0) setLoading(true);
    try {
      const [e, ev, i, j] = await Promise.all([
        api.getEmployees().catch(() => []),
        api.getEvents({ limit: 200 }).catch(() => []),
        api.getAllIncidents().catch(() => ({ reports: [] })),
        api.getDispatchHistory(100).catch(() => []),
      ]);
      const validEmp = Array.isArray(e) ? e : [];
      const validEv = Array.isArray(ev) ? ev : [];
      const validInc = (i && Array.isArray(i.reports)) ? i.reports : [];
      const validJobs = Array.isArray(j) ? j : [];
      const alerts = validEv.filter(x => x.event_type === 'alert');

      setEmployees(validEmp);
      setBinAlerts(alerts);
      setSysLogs(validEv);
      setIncidents(validInc);
      setJobs(validJobs);

      operationsCache = {
        employees: validEmp,
        binAlerts: alerts,
        incidents: validInc,
        jobs: validJobs,
        sysLogs: validEv,
        lastFetched: Date.now()
      };
    } catch (err) {
      if (onNotify) onNotify('Lỗi tải dữ liệu: ' + err.message, 'error');
    } finally { 
      setLoading(false); 
    }
  }, [onNotify]);

  useEffect(() => { 
    load(operationsCache.lastFetched > 0); 
  }, [load]);

  useEffect(() => {
    const sk = getSocket(); 
    if (!sk) return;
    const upd = (j) => { 
      if (!j || !j.id) return; 
      setJobs(prev => { 
        const idx = prev.findIndex(x => x.id === j.id); 
        if (idx >= 0) { 
          const n = [...prev]; 
          n[idx] = j; 
          return n; 
        } 
        return [j, ...prev]; 
      }); 
    };
    const done = (j) => { 
      upd(j); 
      if (onNotify) onNotify('Ca thu gom #' + (j && j.id ? String(j.id).slice(0, 8) : '') + ' đã hoàn tất!', 'success'); 
    };
    const binData = (d) => {
      if (!d || !d.device_id) return;
      const lvl = d.level_percent ?? d.levelPercent ?? 0;
      if (lvl < 80) return;
      setBinAlerts(prev => { 
        const last = prev.find(x => x.device_id === d.device_id); 
        if (last && Date.now() - new Date(last.created_at) < 30000) return prev; 
        return [{ id: 'rt_' + Date.now(), device_id: d.device_id, event_type: 'alert', payload: d, created_at: new Date().toISOString() }, ...prev.slice(0, 299)]; 
      });
    };
    const newEv = (ev) => { 
      if (!ev) return; 
      setSysLogs(prev => [ev, ...prev.slice(0, 499)]); 
      if (ev.event_type === 'alert') setBinAlerts(prev => [ev, ...prev.slice(0, 299)]); 
    };
    sk.on('jobUpdated', upd); 
    sk.on('jobCompleted', done); 
    sk.on('binData', binData); 
    sk.on('newEvent', newEv);
    return () => { 
      sk.off('jobUpdated', upd); 
      sk.off('jobCompleted', done); 
      sk.off('binData', binData); 
      sk.off('newEvent', newEv); 
    };
  }, [onNotify]);

  useEffect(() => { 
    setPage(1); 
  }, [tab, q, roleF, statF, aTypeF, aStatF, jStatF, eTypeF]);

  // Merge IoT alerts and Incident reports for the Alerts tab
  const merged = useMemo(() => [
    ...binAlerts.map(e => {
      const p = e.payload || {};
      const lvl = p.level_percent ?? p.levelPercent ?? (p.fill_level != null ? p.fill_level : (p.distLevel != null ? Math.max(0, Math.min(100, Math.round((40 - p.distLevel) / (40 - 5) * 100))) : 85));
      const binObj = binMap.get(e.device_id);
      return {
        id: 'iot_' + e.id,
        raw_id: e.id,
        type: 'iot',
        device_id: e.device_id,
        title: `Mức rác ${lvl}% — Cần thu gom`,
        reason: `Mức rác ${lvl}% (Vượt ngưỡng cảnh báo)`,
        detail: [p.state && ('Nắp: ' + p.state), p.controlMode && ('Chế độ: ' + p.controlMode)].filter(Boolean).join(' · '),
        description: `Cảm biến siêu âm ghi nhận mức rác đạt ${lvl}%. Cần sớm điều phối xe thu gom rác để tránh tràn thùng.`,
        location: binObj?.location || binObj?.name || (p.location || `Thùng rác #${e.device_id}`),
        bin_name: binObj?.name || p.name || e.device_id,
        lv: lvl,
        employee_name: 'Tự động (ESP32)',
        has_photo: false,
        proof_image_url: null,
        status: 'pending',
        created_at: e.created_at
      };
    }),
    ...incidents.map(i => {
      const binObj = binMap.get(i.device_id);
      const isRes = String(i.status || '').toUpperCase() === 'RESOLVED';
      return {
        id: 'inc_' + i.id,
        raw_id: i.id,
        type: 'incident',
        device_id: i.device_id,
        title: i.reason || 'Sự cố thiết bị',
        reason: i.reason || 'Sự cố kỹ thuật',
        detail: i.description || '',
        description: i.description || 'Không có mô tả chi tiết từ nhân viên.',
        location: binObj?.location || binObj?.name || (i.bin_name || `Thùng rác #${i.device_id}`),
        bin_name: binObj?.name || i.bin_name || i.device_id,
        lv: null,
        employee_name: i.employee_name || 'Nhân viên vận hành',
        has_photo: Boolean(i.has_photo && (i.image_url || i.proof_image_url)),
        proof_image_url: i.image_url || (i.proof_image_url && /^https?:\/\//i.test(i.proof_image_url) ? i.proof_image_url : null),
        status: isRes ? 'resolved' : 'pending',
        raw_status: i.status,
        created_at: i.created_at
      };
    })
  ].sort((a, b) => new Date(b.created_at) - new Date(a.created_at)), [binAlerts, incidents, binMap]);

  const m = useMemo(() => ({
    emp: employees.length,
    active: employees.filter(e => e.is_active).length,
    admin: employees.filter(e => e.role === 'admin').length,
    iot: binAlerts.length,
    pend: incidents.filter(i => String(i.status || '').toUpperCase() !== 'RESOLVED').length,
    res: incidents.filter(i => String(i.status || '').toUpperCase() === 'RESOLVED').length,
    jobs: jobs.length,
    done: jobs.filter(j => j.status === 'COMPLETED').length,
    live: jobs.filter(j => ['IN_PROGRESS', 'ASSIGNED', 'ACCEPTED', 'PAUSED'].includes(j.status)).length,
    cmd: sysLogs.filter(l => l.event_type === 'command').length,
    tele: sysLogs.filter(l => l.event_type === 'telemetry').length
  }), [employees, binAlerts, incidents, jobs, sysLogs]);

  // Toggle incident status from the detail modal
  const handleToggleIncidentStatusInModal = async (alertItem) => {
    if (!alertItem || !alertItem.raw_id || alertItem.type !== 'incident') return;
    const isCurrentlyResolved = alertItem.status === 'resolved';
    const nextStatus = isCurrentlyResolved ? 'NEW' : 'RESOLVED';
    try {
      await api.updateIncidentStatus(alertItem.raw_id, nextStatus);
      if (onNotify) {
        onNotify(nextStatus === 'RESOLVED' ? 'Đã đánh dấu xử lý xong sự cố!' : 'Đã mở lại báo cáo sự cố.', 'success');
      }
      setIncidents(prev => prev.map(i => i.id === alertItem.raw_id ? { 
        ...i, 
        status: nextStatus,
        resolved_at: nextStatus === 'RESOLVED' ? new Date().toISOString() : null 
      } : i));
      setAlertModal(prev => prev ? { ...prev, status: nextStatus === 'RESOLVED' ? 'resolved' : 'pending' } : null);
    } catch (err) {
      if (onNotify) onNotify(`Lỗi cập nhật: ${err.message}`, 'error');
    }
  };

  // Filtered lists
  const fUsers = useMemo(() => employees.filter(e => {
    const s = ((e.full_name || '') + ' ' + (e.username || '')).toLowerCase();
    if (q && !s.includes(q.toLowerCase())) return false;
    if (roleF !== 'ALL' && e.role !== roleF) return false;
    if (statF === 'active' && !e.is_active) return false;
    if (statF === 'inactive' && e.is_active) return false;
    return true;
  }), [employees, q, roleF, statF]);

  const fAlerts = useMemo(() => merged.filter(a => {
    const s = ((a.device_id || '') + ' ' + (a.title || '') + ' ' + (a.employee_name || '') + ' ' + (a.detail || '')).toLowerCase();
    if (q && !s.includes(q.toLowerCase())) return false;
    if (aTypeF !== 'ALL' && a.type !== aTypeF) return false;
    if (aStatF === 'pending' && a.status === 'resolved') return false;
    if (aStatF === 'resolved' && a.status !== 'resolved') return false;
    return true;
  }), [merged, q, aTypeF, aStatF]);

  const fJobs = useMemo(() => jobs.filter(j => {
    const s = ((j.id || '') + ' ' + (j.employee_name || '')).toLowerCase();
    if (q && !s.includes(q.toLowerCase())) return false;
    if (jStatF === 'COMPLETED') return j.status === 'COMPLETED';
    if (jStatF === 'IN_PROGRESS') return ['IN_PROGRESS', 'ASSIGNED', 'ACCEPTED', 'PAUSED'].includes(j.status);
    if (jStatF === 'EXPIRED') return j.status === 'EXPIRED';
    if (jStatF === 'CANCELLED') return j.status === 'CANCELLED';
    return true;
  }), [jobs, q, jStatF]);

  const fLogs = useMemo(() => sysLogs.filter(l => {
    const s = ((l.device_id || '') + ' ' + (l.event_type || '') + ' ' + JSON.stringify(l.payload || {})).toLowerCase();
    if (q && !s.includes(q.toLowerCase())) return false;
    if (eTypeF !== 'ALL' && l.event_type !== eTypeF) return false;
    return true;
  }), [sysLogs, q, eTypeF]);

  const list  = tab === 'users' ? fUsers : tab === 'alerts' ? fAlerts : tab === 'logs' ? fLogs : fJobs;
  const pages = Math.max(1, Math.ceil(list.length / perPage));
  const rows  = list.slice((page - 1) * perPage, page * perPage);

  const exportCSV = () => {
    if (!list.length) { 
      if (onNotify) onNotify('Không có dữ liệu để xuất CSV.', 'warning'); 
      return; 
    }
    let h, r;
    if (tab === 'users') { 
      h = ['Họ tên', 'Tên đăng nhập', 'Vai trò', 'Trạng thái', 'Đăng nhập gần nhất', 'Ngày tạo']; 
      r = list.map(e => ['"' + (e.full_name || '').replace(/"/g, '""') + '"', e.username || '', e.role === 'admin' ? 'Admin' : 'Staff', e.is_active ? 'Active' : 'Locked', e.last_login || '', e.created_at || '']); 
    } else if (tab === 'alerts') { 
      h = ['Thời gian', 'Mã thùng', 'Nguồn', 'Tiêu đề', 'Chi tiết', 'Người báo', 'Trạng thái']; 
      r = list.map(a => [a.created_at || '', a.device_id || '', a.type === 'iot' ? 'IoT' : 'Incident', '"' + (a.title || '').replace(/"/g, '""') + '"', '"' + (a.detail || '').replace(/"/g, '""') + '"', a.employee_name || '', a.status || '']); 
    } else if (tab === 'logs') { 
      h = ['Thời gian', 'Thiết bị', 'Loại sự kiện', 'Payload']; 
      r = list.map(l => [l.created_at || '', l.device_id || '', l.event_type || '', '"' + JSON.stringify(l.payload || {}).replace(/"/g, '""') + '"']); 
    } else { 
      h = ['Mã ca', 'Nhân viên', 'Số thùng', 'Tiến độ', 'Thời lượng', 'Thời gian', 'Trạng thái']; 
      r = list.map(j => [j.id || '', '"' + (j.employee_name || '').replace(/"/g, '""') + '"', (j.target_bin_ids || []).length, (j.completed_bin_ids || []).length + '/' + (j.target_bin_ids || []).length, fmtDur(j.started_at || j.created_at, j.completed_at), j.created_at || '', j.status || '']); 
    }
    const bom = String.fromCharCode(65279);
    const csv = 'data:text/csv;charset=utf-8,' + bom + [h.join(','), ...r.map(x => x.join(','))].join('\n');
    const a = document.createElement('a'); 
    a.href = encodeURI(csv); 
    a.download = 'smartwaste_' + tab + '_' + new Date().toISOString().slice(0, 10) + '.csv';
    document.body.appendChild(a); 
    a.click(); 
    document.body.removeChild(a);
    if (onNotify) onNotify('Xuất file CSV thành công!', 'success');
  };

  const TABS = [
    { id: 'collections', label: 'Lịch sử thu gom', Icon: Truck, count: jobs.length },
    { id: 'alerts',      label: 'Cảnh báo & Sự cố', Icon: Bell, count: merged.length },
    { id: 'users',       label: 'Tài khoản nhân sự', Icon: Users, count: employees.length },
    { id: 'logs',        label: 'Nhật ký hệ thống', Icon: Terminal, count: sysLogs.length },
  ];

  const KPIS = [
    { Icon: Truck,    c: '#10b981', bg: '#ecfdf5', label: 'Ca thu gom',   val: m.jobs,         sub: m.done + ' xong · ' + m.live + ' đang chạy' },
    { Icon: Bell,     c: '#dc2626', bg: '#fef2f2', label: 'Cảnh báo IoT', val: m.iot,          sub: 'Thùng rác ≥ 80%' },
    { Icon: Users,    c: '#2563eb', bg: '#eff6ff', label: 'Nhân viên',    val: m.emp,          sub: m.active + ' active · ' + m.admin + ' admin' },
    { Icon: Terminal, c: '#7c3aed', bg: '#f5f3ff', label: 'Nhật ký',      val: sysLogs.length, sub: m.cmd + ' cmd · ' + m.tele + ' tele' },
  ];

  const Pager = () => (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 20px', borderTop: '1px solid #f1f5f9', flexWrap: 'wrap', gap: 12 }}>
      <span style={{ fontSize: 12.5, color: '#475569' }}>
        Hiển thị {list.length ? ((page - 1) * perPage + 1) + '–' + Math.min(page * perPage, list.length) + ' / ' + list.length : '0'} bản ghi
      </span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <button onClick={() => setPage(p => Math.max(1, p - 1))} disabled={page === 1} style={{ width: 30, height: 30, borderRadius: 6, border: '1px solid #e2e8f0', backgroundColor: '#fff', cursor: page === 1 ? 'not-allowed' : 'pointer', color: page === 1 ? '#cbd5e1' : '#334155', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <ChevronLeft size={14} />
        </button>
        <span style={{ minWidth: 28, height: 28, padding: '0 8px', borderRadius: 6, backgroundColor: '#10b981', color: '#fff', fontWeight: 700, fontSize: 12, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {page}
        </span>
        <span style={{ color: '#94a3b8', fontSize: 12 }}>/ {pages}</span>
        <button onClick={() => setPage(p => Math.min(pages, p + 1))} disabled={page === pages} style={{ width: 30, height: 30, borderRadius: 6, border: '1px solid #e2e8f0', backgroundColor: '#fff', cursor: page === pages ? 'not-allowed' : 'pointer', color: page === pages ? '#cbd5e1' : '#334155', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <ChevronRight size={14} />
        </button>
        <select value={perPage} onChange={e => { setPerPage(+e.target.value); setPage(1); }} style={{ padding: '4px 8px', borderRadius: 6, border: '1px solid #cbd5e1', fontSize: 12, fontWeight: 600, color: '#334155', backgroundColor: '#fff', outline: 'none', cursor: 'pointer' }}>
          {[5, 10, 20, 50].map(n => <option key={n} value={n}>{n} / trang</option>)}
        </select>
      </div>
    </div>
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20, maxWidth: 1400, margin: '0 auto' }}>
      
      {/* 1. KPI Row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16 }}>
        {KPIS.map((k, i) => {
          const Icon = k.Icon;
          return (
            <div key={i} style={{ backgroundColor: '#fff', padding: '16px 20px', borderRadius: 12, border: '1px solid #e2e8f0', display: 'flex', alignItems: 'center', gap: 16, boxShadow: '0 1px 3px rgba(0,0,0,0.02)' }}>
              <div style={{ width: 44, height: 44, borderRadius: 10, backgroundColor: k.bg, color: k.c, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <Icon size={22} />
              </div>
              <div style={{ minWidth: 0 }}>
                <div style={{ fontSize: 12, color: '#64748b', fontWeight: 600 }}>{k.label}</div>
                <div style={{ fontSize: 22, fontWeight: 800, color: '#0f172a', lineHeight: 1.2 }}>{k.val}</div>
                <div style={{ fontSize: 11.5, color: '#94a3b8', marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{k.sub}</div>
              </div>
            </div>
          );
        })}
      </div>

      {/* 2. Main Table Card */}
      <div style={{ backgroundColor: '#fff', borderRadius: 14, border: '1px solid #e2e8f0', overflow: 'hidden', boxShadow: '0 2px 4px rgba(0,0,0,0.02)' }}>
        
        {/* Navigation Tabs Bar */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #e2e8f0', padding: '0 16px', backgroundColor: '#fafafa', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', gap: 6, overflowX: 'auto', padding: '10px 0' }}>
            {TABS.map(t => {
              const TabIcon = t.Icon;
              const isActive = tab === t.id;
              return (
                <button
                  key={t.id}
                  onClick={() => setTab(t.id)}
                  style={{
                    padding: '8px 14px',
                    borderRadius: 8,
                    fontSize: 13,
                    fontWeight: isActive ? 700 : 600,
                    border: 'none',
                    cursor: 'pointer',
                    backgroundColor: isActive ? '#fff' : 'transparent',
                    color: isActive ? '#0f172a' : '#64748b',
                    boxShadow: isActive ? '0 1px 3px rgba(0,0,0,0.08)' : 'none',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 7,
                    transition: 'all 150ms ease'
                  }}
                >
                  <TabIcon size={14} color={isActive ? '#10b981' : '#94a3b8'} />
                  <span>{t.label}</span>
                  <span style={{ fontSize: 11, padding: '1px 6px', borderRadius: 10, backgroundColor: isActive ? '#f1f5f9' : '#e2e8f0', color: '#475569' }}>
                    {t.count}
                  </span>
                </button>
              );
            })}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 0' }}>
            <button
              onClick={exportCSV}
              style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '7px 12px', borderRadius: 8, border: '1px solid #e2e8f0', backgroundColor: '#fff', color: '#334155', fontSize: 12.5, fontWeight: 600, cursor: 'pointer' }}
            >
              <Download size={15} />
              <span>Xuất CSV</span>
            </button>
            <button
              onClick={load}
              disabled={loading}
              style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '7px 12px', borderRadius: 8, border: '1px solid #e2e8f0', backgroundColor: '#fff', color: '#334155', fontSize: 12.5, fontWeight: 600, cursor: 'pointer' }}
            >
              <RefreshCw size={15} className={loading ? 'spin-animation' : ''} />
              <span>Làm mới</span>
            </button>
          </div>
        </div>

        {/* Filter & Search Bar */}
        <div style={{ padding: '12px 18px', borderBottom: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, backgroundColor: '#f8fafc', padding: '7px 12px', borderRadius: 8, border: '1px solid #e2e8f0', flex: '1 1 240px' }}>
            <Search size={15} color="#94a3b8" />
            <input
              type="text"
              placeholder="Tìm kiếm mã, tên, thiết bị..."
              value={q}
              onChange={e => setQ(e.target.value)}
              style={{ border: 'none', background: 'transparent', outline: 'none', fontSize: 13, width: '100%', color: '#0f172a' }}
            />
            {q && <X size={14} color="#94a3b8" style={{ cursor: 'pointer' }} onClick={() => setQ('')} />}
          </div>

          {tab === 'collections' && (
            <select value={jStatF} onChange={e => setJStatF(e.target.value)} style={{ padding: '7px 12px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 12.5, fontWeight: 600, color: '#334155', backgroundColor: '#fff', outline: 'none', cursor: 'pointer' }}>
              <option value="ALL">Tất cả trạng thái</option>
              <option value="IN_PROGRESS">Đang thực hiện</option>
              <option value="COMPLETED">Đã hoàn thành</option>
              <option value="EXPIRED">Hết hạn (EXPIRED)</option>
              <option value="CANCELLED">Đã hủy</option>
            </select>
          )}

          {tab === 'alerts' && (
            <>
              <select value={aTypeF} onChange={e => setATypeF(e.target.value)} style={{ padding: '7px 12px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 12.5, fontWeight: 600, color: '#334155', backgroundColor: '#fff', outline: 'none', cursor: 'pointer' }}>
                <option value="ALL">Tất cả nguồn</option>
                <option value="iot">⚡ Cảnh báo IoT</option>
                <option value="incident">⚠️ Sự cố nhân viên</option>
              </select>
              <select value={aStatF} onChange={e => setAStatF(e.target.value)} style={{ padding: '7px 12px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 12.5, fontWeight: 600, color: '#334155', backgroundColor: '#fff', outline: 'none', cursor: 'pointer' }}>
                <option value="ALL">Tất cả trạng thái</option>
                <option value="pending">Chờ xử lý</option>
                <option value="resolved">Đã giải quyết</option>
              </select>
            </>
          )}

          {tab === 'users' && (
            <>
              <select value={roleF} onChange={e => setRoleF(e.target.value)} style={{ padding: '7px 12px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 12.5, fontWeight: 600, color: '#334155', backgroundColor: '#fff', outline: 'none', cursor: 'pointer' }}>
                <option value="ALL">Tất cả vai trò</option>
                <option value="admin">Quản trị viên (Admin)</option>
                <option value="staff">Nhân viên thu gom (Staff)</option>
              </select>
              <select value={statF} onChange={e => setStatF(e.target.value)} style={{ padding: '7px 12px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 12.5, fontWeight: 600, color: '#334155', backgroundColor: '#fff', outline: 'none', cursor: 'pointer' }}>
                <option value="ALL">Tất cả trạng thái</option>
                <option value="active">Hoạt động</option>
                <option value="inactive">Đã khóa</option>
              </select>
            </>
          )}

          {tab === 'logs' && (
            <select value={eTypeF} onChange={e => setETypeF(e.target.value)} style={{ padding: '7px 12px', borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 12.5, fontWeight: 600, color: '#334155', backgroundColor: '#fff', outline: 'none', cursor: 'pointer' }}>
              <option value="ALL">Tất cả loại sự kiện</option>
              <option value="alert">Alerts (Cảnh báo)</option>
              <option value="command">Commands (Lệnh điều khiển)</option>
              <option value="telemetry">Telemetry (Dữ liệu cảm biến)</option>
            </select>
          )}
        </div>

        {/* Data Tables */}
        <div style={{ overflowX: 'auto', width: '100%', WebkitOverflowScrolling: 'touch' }}>
          <table style={{ width: '100%', minWidth: tab === 'logs' ? '1120px' : tab === 'alerts' ? '1060px' : '980px', borderCollapse: 'collapse', fontSize: 13 }}>
            
            {/* 1. Tab Ca Thu Gom */}
            {tab === 'collections' && (
              <>
                <thead>
                  <tr>
                    <th style={TH()}>Mã ca gom</th>
                    <th style={TH()}>Nhân viên thực hiện</th>
                    <th style={TH('center')}>Tiến độ thùng</th>
                    <th style={TH()}>Thời lượng</th>
                    <th style={TH()}>Thời gian bắt đầu</th>
                    <th style={TH('center')}>Trạng thái</th>
                    <th style={TH('center')}>Chi tiết</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <tr><td colSpan={7} style={{ padding: 32, textAlign: 'center', color: '#94a3b8' }}>Chưa có ca thu gom nào.</td></tr>
                  ) : rows.map(j => {
                    const targets = j.target_bin_ids || [];
                    const completed = j.completed_bin_ids || [];
                    const pct = targets.length > 0 ? Math.round((completed.length / targets.length) * 100) : 0;
                    return (
                      <tr key={j.id} style={{ transition: 'background 100ms' }}>
                        <td style={TD()}>
                          <div style={{ fontWeight: 700, color: '#0f172a', fontFamily: 'monospace' }}>{j.id.slice(0, 14)}</div>
                          <div style={{ fontSize: 11, color: '#94a3b8' }}>{j.source || 'ADMIN_ASSIGNED'}</div>
                        </td>
                        <td style={TD()}>
                          <div style={{ fontWeight: 600, color: '#1e293b' }}>
                            {j.employee_name || 'Tài xế'}
                          </div>
                        </td>
                        <td style={TD('center')}>
                          <div style={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
                            <span style={{ fontWeight: 700, color: pct === 100 ? '#10b981' : '#2563eb', fontSize: 12.5 }}>
                              {completed.length} / {targets.length} ({pct}%)
                            </span>
                            <div style={{ width: 80, height: 5, borderRadius: 99, backgroundColor: '#f1f5f9', overflow: 'hidden' }}>
                              <div style={{ width: `${pct}%`, height: '100%', backgroundColor: pct === 100 ? '#10b981' : '#2563eb' }}></div>
                            </div>
                          </div>
                        </td>
                        <td style={TD()}>
                          <div style={{ color: '#475569', fontWeight: 500 }}>{fmtDur(j.started_at || j.created_at, j.completed_at)}</div>
                        </td>
                        <td style={TD()}>
                          <div style={{ color: '#1e293b' }}>{formatVietnamDate(j.created_at)}</div>
                          <div style={{ fontSize: 11, color: '#94a3b8' }}>{formatVietnamTime(j.created_at)}</div>
                        </td>
                        <td style={TD('center')}>
                          {j.status === 'COMPLETED' ? <Bd c="#059669" bg="#ecfdf5" b="#a7f3d0">Hoàn thành</Bd> :
                           ['IN_PROGRESS', 'ASSIGNED', 'ACCEPTED'].includes(j.status) ? <Bd c="#2563eb" bg="#eff6ff" b="#bfdbfe">Đang gom</Bd> :
                           j.status === 'PAUSED' ? <Bd c="#d97706" bg="#fffbeb" b="#fde68a">Tạm dừng</Bd> :
                           j.status === 'EXPIRED' ? <Bd c="#7c3aed" bg="#f5f3ff" b="#ddd6fe">Hết hạn</Bd> :
                           j.status === 'CANCELLED' ? <Bd c="#dc2626" bg="#fef2f2" b="#fecaca">Đã hủy</Bd> :
                           <Bd c="#64748b" bg="#f8fafc" b="#e2e8f0">{j.status}</Bd>}
                        </td>
                        <td style={TD('center')}>
                          <button
                            onClick={() => setJobModal(j)}
                            style={{ padding: '5px 10px', borderRadius: 6, border: '1px solid #e2e8f0', backgroundColor: '#fff', color: '#2563eb', fontSize: 12, fontWeight: 600, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 4 }}
                          >
                            <Eye size={13} />
                            <span>Xem</span>
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </>
            )}

            {/* 2. Tab Cảnh Báo & Sự Cố (Clean format matching user's exact structure) */}
            {tab === 'alerts' && (
              <>
                <thead>
                  <tr>
                    <th style={TH()}>Thời gian</th>
                    <th style={TH()}>Người báo / Mã thùng</th>
                    <th style={TH('center')}>Trạng thái</th>
                    <th style={TH()}>Nguồn</th>
                    <th style={TH()}>Nội dung cảnh báo</th>
                    <th style={TH('center')}>Chi tiết</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <tr><td colSpan={6} style={{ padding: 32, textAlign: 'center', color: '#94a3b8' }}>Không có cảnh báo nào.</td></tr>
                  ) : rows.map(a => (
                    <tr key={a.id}>
                      <td style={TD()}>
                        <div style={{ color: '#1e293b', whiteSpace: 'nowrap' }}>{formatVietnamDate(a.created_at)}</div>
                        <div style={{ fontSize: 11, color: '#94a3b8' }}>{formatVietnamTime(a.created_at)}</div>
                      </td>
                      <td style={TD()}>
                        <div style={{ fontWeight: 600, color: '#1e293b' }}>
                          {a.employee_name || 'Tự động (ESP32)'}
                        </div>
                        <div style={{ fontSize: 11.5, fontWeight: 700, color: '#2563eb', fontFamily: 'monospace', marginTop: 1 }}>
                          #{a.device_id || '—'}
                        </div>
                      </td>
                      <td style={TD('center')}>
                        {a.status === 'resolved' ? (
                          <Bd c="#059669" bg="#ecfdf5" b="#a7f3d0">Đã giải quyết</Bd>
                        ) : (
                          <Bd c="#dc2626" bg="#fef2f2" b="#fecaca">Cần xử lý</Bd>
                        )}
                      </td>
                      <td style={TD()}>
                        {a.type === 'iot' ? (
                          <Bd c="#dc2626" bg="#fef2f2" b="#fecaca">Sensor IoT</Bd>
                        ) : (
                          <Bd c="#7c3aed" bg="#f5f3ff" b="#ddd6fe">Sự cố Mobile</Bd>
                        )}
                      </td>
                      <td style={{ ...TD(), whiteSpace: 'normal', minWidth: '320px' }}>
                        <div style={{ fontWeight: 700, color: '#0f172a' }}>{a.title}</div>
                        {a.detail && <div style={{ fontSize: 12, color: '#64748b', marginTop: 3, lineHeight: 1.4 }}>{a.detail}</div>}
                      </td>
                      <td style={TD('center')}>
                        <button
                          onClick={() => setAlertModal(a)}
                          style={{
                            padding: '5px 12px',
                            borderRadius: 6,
                            border: '1px solid #cbd5e1',
                            backgroundColor: '#ffffff',
                            color: '#2563eb',
                            fontSize: 12,
                            fontWeight: 700,
                            cursor: 'pointer',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4
                          }}
                        >
                          <Eye size={13} />
                          <span>Chi tiết</span>
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </>
            )}

            {/* 3. Tab Tài Khoản Nhân Sự */}
            {tab === 'users' && (
              <>
                <thead>
                  <tr>
                    <th style={TH()}>Họ và tên</th>
                    <th style={TH()}>Tên đăng nhập</th>
                    <th style={TH()}>Vai trò</th>
                    <th style={TH()}>Đăng nhập gần nhất</th>
                    <th style={TH()}>Ngày tạo</th>
                    <th style={TH('center')}>Trạng thái</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <tr><td colSpan={6} style={{ padding: 32, textAlign: 'center', color: '#94a3b8' }}>Không có tài khoản nào.</td></tr>
                  ) : rows.map(e => (
                    <tr key={e.id}>
                      <td style={TD()}>
                        <div style={{ fontWeight: 700, color: '#0f172a' }}>{e.full_name || 'Chưa đặt tên'}</div>
                      </td>
                      <td style={TD()}>
                        <div style={{ fontWeight: 600, color: '#334155' }}>{e.username}</div>
                      </td>
                      <td style={TD()}>
                        {e.role === 'admin' ? (
                          <Bd c="#7c3aed" bg="#f5f3ff" b="#ddd6fe">Quản trị viên</Bd>
                        ) : (
                          <Bd c="#2563eb" bg="#eff6ff" b="#bfdbfe">Nhân viên thu gom</Bd>
                        )}
                      </td>
                      <td style={TD()}>
                        {e.last_login ? (
                          <>
                            <div style={{ color: '#1e293b' }}>{formatVietnamDate(e.last_login)}</div>
                            <div style={{ fontSize: 11, color: '#94a3b8' }}>{formatVietnamTime(e.last_login)}</div>
                          </>
                        ) : <span style={{ color: '#94a3b8' }}>Chưa đăng nhập</span>}
                      </td>
                      <td style={TD()}>
                        <div style={{ color: '#1e293b' }}>{formatVietnamDate(e.created_at)}</div>
                      </td>
                      <td style={TD('center')}>
                        {e.is_active ? (
                          <Bd c="#059669" bg="#ecfdf5" b="#a7f3d0">Hoạt động</Bd>
                        ) : (
                          <Bd c="#dc2626" bg="#fef2f2" b="#fecaca">Đã khóa</Bd>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </>
            )}

            {/* 4. Tab Nhật Ký Hệ Thống */}
            {tab === 'logs' && (
              <>
                <thead>
                  <tr>
                    <th style={TH()}>Thời gian</th>
                    <th style={TH()}>Thiết bị</th>
                    <th style={TH()}>Loại sự kiện</th>
                    <th style={TH()}>Chi tiết Payload</th>
                    <th style={TH('center')}>JSON</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.length === 0 ? (
                    <tr><td colSpan={5} style={{ padding: 32, textAlign: 'center', color: '#94a3b8' }}>Chưa có nhật ký hệ thống.</td></tr>
                  ) : rows.map(l => {
                    const cfg = etCfg(l.event_type);
                    return (
                      <tr key={l.id}>
                        <td style={TD()}>
                          <div style={{ color: '#1e293b' }}>{formatVietnamDate(l.created_at)}</div>
                          <div style={{ fontSize: 11, color: '#94a3b8' }}>{formatVietnamTime(l.created_at)}</div>
                        </td>
                        <td style={TD()}><span style={{ fontFamily: 'monospace', fontWeight: 700, color: '#0f172a' }}>{l.device_id || 'SYSTEM'}</span></td>
                        <td style={TD()}>
                          <Bd c={cfg.c} bg={cfg.bg} b={cfg.b}>{cfg.label}</Bd>
                        </td>
                        <td style={{ ...TD(), whiteSpace: 'normal', minWidth: '400px' }}>
                          <div style={{ fontSize: 12, color: '#334155', fontFamily: 'monospace', backgroundColor: '#f8fafc', padding: '6px 10px', borderRadius: 6, border: '1px solid #e2e8f0', wordBreak: 'break-all', lineHeight: 1.4 }}>
                            {JSON.stringify(l.payload || {})}
                          </div>
                        </td>
                        <td style={TD('center')}>
                          <button
                            onClick={() => setLogModal(l)}
                            style={{ padding: '4px 8px', borderRadius: 6, border: '1px solid #cbd5e1', backgroundColor: '#f8fafc', color: '#334155', fontSize: 11.5, fontWeight: 600, cursor: 'pointer' }}
                          >
                            Xem
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </>
            )}

          </table>
        </div>

        {/* Pagination */}
        <Pager />
      </div>

      {/* Modal 1: Chi tiết Ca thu gom */}
      {jobModal && (
        <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(5, 15, 25, 0.75)', backdropFilter: 'blur(4px)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
          <div style={{ backgroundColor: '#fff', borderRadius: 16, maxWidth: 540, width: '100%', padding: 24, boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
              <div>
                <h3 style={{ fontSize: 17, fontWeight: 800, color: '#0f172a', margin: 0 }}>Chi tiết ca thu gom</h3>
                <div style={{ fontSize: 12, color: '#64748b', fontFamily: 'monospace' }}>Mã: #{jobModal.id}</div>
              </div>
              <button onClick={() => setJobModal(null)} style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: '#64748b' }}>
                <X size={20} />
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 14px', backgroundColor: '#f8fafc', borderRadius: 8 }}>
                <span style={{ fontSize: 13, color: '#64748b' }}>Nhân viên phụ trách:</span>
                <span style={{ fontSize: 13, fontWeight: 700, color: '#0f172a' }}>{jobModal.employee_name || 'Tài xế'}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 14px', backgroundColor: '#f8fafc', borderRadius: 8 }}>
                <span style={{ fontSize: 13, color: '#64748b' }}>Trạng thái:</span>
                <span style={{ fontSize: 13, fontWeight: 700, color: jobModal.status === 'COMPLETED' ? '#059669' : '#2563eb' }}>{jobModal.status}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 14px', backgroundColor: '#f8fafc', borderRadius: 8 }}>
                <span style={{ fontSize: 13, color: '#64748b' }}>Thời gian tạo:</span>
                <span style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>{formatVietnamDate(jobModal.created_at)} {formatVietnamTime(jobModal.created_at)}</span>
              </div>

              <div>
                <div style={{ fontSize: 13, fontWeight: 700, color: '#1e293b', marginBottom: 8, marginTop: 6 }}>
                  Danh sách thùng rác lộ trình ({jobModal.target_bin_ids ? jobModal.target_bin_ids.length : 0} điểm):
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxHeight: 180, overflowY: 'auto' }}>
                  {(jobModal.target_bin_ids || []).map((bId, idx) => {
                    const isDone = (jobModal.completed_bin_ids || []).includes(bId);
                    return (
                      <div key={bId} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 12px', borderRadius: 8, backgroundColor: isDone ? '#ecfdf5' : '#f8fafc', border: isDone ? '1px solid #a7f3d0' : '1px solid #e2e8f0' }}>
                        <span style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>{idx + 1}. {bId}</span>
                        {isDone ? (
                          <span style={{ fontSize: 12, fontWeight: 700, color: '#059669', display: 'flex', alignItems: 'center', gap: 4 }}>
                            <Check size={14} /> Đã thu gom
                          </span>
                        ) : (
                          <span style={{ fontSize: 12, color: '#94a3b8' }}>Chưa tới</span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>

            <button
              onClick={() => setJobModal(null)}
              style={{ width: '100%', marginTop: 20, padding: '10px 0', borderRadius: 8, backgroundColor: '#0f172a', color: '#fff', border: 'none', fontWeight: 700, fontSize: 13.5, cursor: 'pointer' }}
            >
              Đóng
            </button>
          </div>
        </div>
      )}

      {/* Modal 2: Chi tiết Cảnh báo / Sự cố */}
      {alertModal && (
        <div style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(5, 15, 25, 0.75)',
          backdropFilter: 'blur(6px)',
          zIndex: 1100,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 20
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: 18,
            maxWidth: alertModal.type === 'incident' ? 840 : 540,
            width: '100%',
            overflow: 'hidden',
            boxShadow: '0 25px 50px -12px rgba(0,0,0,0.3)',
            display: 'flex',
            flexDirection: 'column'
          }}>
            {/* Modal Header */}
            <div style={{
              padding: '16px 22px',
              borderBottom: '1px solid #f1f5f9',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              backgroundColor: '#fafbfc'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ fontSize: 16, fontWeight: 800, color: '#0f172a' }}>
                  {alertModal.type === 'incident' ? 'Chi tiết sự cố hiện trường' : 'Cảnh báo cảm biến IoT'}
                </span>
                <span style={{
                  fontSize: 11.5,
                  fontWeight: 700,
                  padding: '2px 8px',
                  borderRadius: 6,
                  fontFamily: 'monospace',
                  backgroundColor: '#eff6ff',
                  color: '#2563eb'
                }}>
                  #{alertModal.device_id}
                </span>
              </div>
              <button
                onClick={() => setAlertModal(null)}
                style={{
                  border: 'none',
                  background: 'transparent',
                  cursor: 'pointer',
                  color: '#64748b',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                <X size={20} />
              </button>
            </div>

            {/* Modal Body: SỰ CỐ APP (CHIA 2 CỘT: ẢNH TRÁI, INFO PHẢI) */}
            {alertModal.type === 'incident' ? (
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'minmax(0, 1.1fr) minmax(0, 1.3fr)',
                gap: 22,
                padding: 22,
                alignItems: 'stretch'
              }}>
                {/* BÊN TRÁI: HÌNH ẢNH CHỤP TỪ MOBILE */}
                <div style={{
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'center',
                  alignItems: 'center',
                  backgroundColor: '#0f172a',
                  borderRadius: 14,
                  overflow: 'hidden',
                  minHeight: 280,
                  position: 'relative'
                }}>
                  {alertModal.proof_image_url ? (
                    <img
                      src={alertModal.proof_image_url}
                      alt="Ảnh sự cố thực tế từ tài xế"
                      style={{
                        width: '100%',
                        height: '100%',
                        maxHeight: 340,
                        objectFit: 'cover',
                        display: 'block'
                      }}
                    />
                  ) : (
                    <div style={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: 10,
                      padding: 30,
                      color: '#94a3b8',
                      backgroundColor: '#f8fafc',
                      width: '100%',
                      height: '100%',
                      borderRadius: 14,
                      border: '1px dashed #cbd5e1'
                    }}>
                      <Camera size={36} color="#94a3b8" />
                      <span style={{ fontSize: 13, fontWeight: 500 }}>Không có hình ảnh đính kèm</span>
                    </div>
                  )}
                </div>

                {/* BÊN PHẢI: THÔNG TIN CHI TIẾT */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 14, justifyContent: 'space-between' }}>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                    {/* Top Badges */}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                      <span style={{
                        padding: '4px 10px',
                        borderRadius: 99,
                        fontSize: 11.5,
                        fontWeight: 700,
                        backgroundColor: alertModal.status === 'resolved' ? '#ecfdf5' : '#fef2f2',
                        color: alertModal.status === 'resolved' ? '#059669' : '#dc2626',
                        border: alertModal.status === 'resolved' ? '1px solid #a7f3d0' : '1px solid #fecaca',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 5
                      }}>
                        {alertModal.status === 'resolved' ? <CheckCircle2 size={13} /> : <AlertTriangle size={13} />}
                        <span>{alertModal.status === 'resolved' ? 'Đã giải quyết' : 'Cần xử lý'}</span>
                      </span>

                      <span style={{
                        fontSize: 12,
                        fontWeight: 800,
                        fontFamily: 'monospace',
                        color: '#111a4a',
                        backgroundColor: '#f1f5f9',
                        padding: '3px 9px',
                        borderRadius: 6
                      }}>
                        #{alertModal.device_id}
                      </span>
                    </div>

                    {/* Title */}
                    <div>
                      <h4 style={{ fontSize: 17, fontWeight: 800, color: '#0f172a', margin: 0 }}>
                        {alertModal.title}
                      </h4>
                    </div>

                    {/* Description Box */}
                    <div style={{
                      backgroundColor: '#f8fafc',
                      border: '1px solid #e2e8f0',
                      borderRadius: 10,
                      padding: '12px 14px',
                      fontSize: 13,
                      color: '#334155',
                      lineHeight: 1.5
                    }}>
                      {alertModal.description || alertModal.detail || 'Không có mô tả chi tiết.'}
                    </div>

                    {/* Metadata Rows */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 13 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#475569' }}>
                        <MapPin size={15} color="#64748b" style={{ flexShrink: 0 }} />
                        <span style={{ fontWeight: 600, color: '#1e293b' }}>
                          {alertModal.location || 'TP. Hồ Chí Minh'}
                        </span>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#475569' }}>
                        <Users size={15} color="#64748b" style={{ flexShrink: 0 }} />
                        <span>
                          Người báo: <strong style={{ color: '#0f172a' }}>{alertModal.employee_name}</strong>
                        </span>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#475569' }}>
                        <Clock size={15} color="#64748b" style={{ flexShrink: 0 }} />
                        <span>
                          {formatVietnamTime(alertModal.created_at)} {formatVietnamDate(alertModal.created_at)}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Action Footer */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, paddingTop: 10, borderTop: '1px solid #f1f5f9' }}>
                    <button
                      onClick={() => handleToggleIncidentStatusInModal(alertModal)}
                      style={{
                        flex: 1,
                        padding: '9px 14px',
                        borderRadius: 8,
                        border: 'none',
                        backgroundColor: alertModal.status === 'resolved' ? '#f1f5f9' : '#10b981',
                        color: alertModal.status === 'resolved' ? '#334155' : '#ffffff',
                        fontSize: 13,
                        fontWeight: 700,
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: 6
                      }}
                    >
                      {alertModal.status === 'resolved' ? (
                        <>
                          <RefreshCw size={14} />
                          <span>Mở lại báo cáo</span>
                        </>
                      ) : (
                        <>
                          <Check size={15} />
                          <span>Đánh dấu đã xử lý xong</span>
                        </>
                      )}
                    </button>

                    <button
                      onClick={() => setAlertModal(null)}
                      style={{
                        padding: '9px 18px',
                        borderRadius: 8,
                        border: '1px solid #e2e8f0',
                        backgroundColor: '#ffffff',
                        color: '#475569',
                        fontSize: 13,
                        fontWeight: 600,
                        cursor: 'pointer'
                      }}
                    >
                      Đóng
                    </button>
                  </div>
                </div>
              </div>
            ) : (
              /* Modal Body: CẢNH BÁO IOT CẢM BIẾN ESP32 (ĐƠN CỘT THÔNG SỐ, KHÔNG CÓ ẢNH) */
              <div style={{ padding: 22, display: 'flex', flexDirection: 'column', gap: 16 }}>
                {/* Level Gauge Box */}
                <div style={{
                  padding: '16px 18px',
                  borderRadius: 12,
                  backgroundColor: '#fef2f2',
                  border: '1px solid #fecaca',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between'
                }}>
                  <div>
                    <span style={{ fontSize: 12, fontWeight: 700, color: '#dc2626', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                      Mức rác cảnh báo đầy
                    </span>
                    <div style={{ fontSize: 26, fontWeight: 800, color: '#b91c1c', marginTop: 2 }}>
                      {alertModal.lv}%
                    </div>
                  </div>
                  <span style={{
                    padding: '5px 12px',
                    borderRadius: 99,
                    fontSize: 12,
                    fontWeight: 700,
                    backgroundColor: '#fee2e2',
                    color: '#dc2626',
                    border: '1px solid #fca5a5'
                  }}>
                    Vượt ngưỡng ≥ 80%
                  </span>
                </div>

                {/* Info List */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 13 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 14px', backgroundColor: '#f8fafc', borderRadius: 8 }}>
                    <span style={{ color: '#64748b' }}>Thùng rác:</span>
                    <span style={{ fontWeight: 700, color: '#0f172a' }}>#{alertModal.device_id} ({alertModal.bin_name})</span>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 14px', backgroundColor: '#f8fafc', borderRadius: 8 }}>
                    <span style={{ color: '#64748b' }}>Vị trí lắp đặt:</span>
                    <span style={{ fontWeight: 600, color: '#1e293b' }}>{alertModal.location}</span>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 14px', backgroundColor: '#f8fafc', borderRadius: 8 }}>
                    <span style={{ color: '#64748b' }}>Nguồn ghi nhận:</span>
                    <span style={{ fontWeight: 600, color: '#2563eb' }}>Cảm biến siêu âm ESP32 (Tự động)</span>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 14px', backgroundColor: '#f8fafc', borderRadius: 8 }}>
                    <span style={{ color: '#64748b' }}>Thời gian:</span>
                    <span style={{ fontWeight: 600, color: '#334155' }}>
                      {formatVietnamTime(alertModal.created_at)} {formatVietnamDate(alertModal.created_at)}
                    </span>
                  </div>

                  <div style={{ padding: '10px 14px', backgroundColor: '#f8fafc', borderRadius: 8, color: '#475569', lineHeight: 1.5 }}>
                    {alertModal.description}
                  </div>
                </div>

                {/* Close Button */}
                <button
                  onClick={() => setAlertModal(null)}
                  style={{
                    width: '100%',
                    padding: '10px 0',
                    borderRadius: 8,
                    backgroundColor: '#0f172a',
                    color: '#ffffff',
                    border: 'none',
                    fontWeight: 700,
                    fontSize: 13.5,
                    cursor: 'pointer'
                  }}
                >
                  Đóng
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Modal 3: Chi tiết JSON Log */}
      {logModal && (
        <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(5, 15, 25, 0.75)', backdropFilter: 'blur(4px)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
          <div style={{ backgroundColor: '#fff', borderRadius: 16, maxWidth: 560, width: '100%', padding: 24, boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
              <span style={{ fontWeight: 700, fontSize: 15, color: '#0f172a' }}>Chi tiết sự kiện [{logModal.event_type}]</span>
              <button onClick={() => setLogModal(null)} style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: '#64748b' }}>
                <X size={20} />
              </button>
            </div>
            <pre style={{ backgroundColor: '#0f172a', color: '#38bdf8', padding: 16, borderRadius: 8, fontSize: 12, overflowX: 'auto', maxHeight: 320, lineHeight: 1.5 }}>
              {JSON.stringify(logModal, null, 2)}
            </pre>
            <button
              onClick={() => setLogModal(null)}
              style={{ width: '100%', marginTop: 16, padding: '9px 0', borderRadius: 8, backgroundColor: '#f1f5f9', color: '#334155', border: 'none', fontWeight: 700, fontSize: 13, cursor: 'pointer' }}
            >
              Đóng
            </button>
          </div>
        </div>
      )}

    </div>
  );
}