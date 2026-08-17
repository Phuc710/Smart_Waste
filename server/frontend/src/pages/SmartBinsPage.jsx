import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  Trash2,
  AlertTriangle,
  CheckCircle2,
  WifiOff,
  Search,
  RefreshCw,
  Eye,
  MapPin,
  ChevronLeft,
  ChevronRight,
  Truck,
  Sliders,
  Clock,
  Radio,
  Power,
  Cpu,
  Copy,
  Check,
  Edit3,
  X,
  Navigation,
  Sparkles,
  Download,
  RotateCcw
} from 'lucide-react';
import { api } from '../services/api';
import { getSocket } from '../services/socket';
import { getVietnamRelativeTime } from '../utils/dateTime';

// Helper màu sắc mức rác
const getFillColor = (level, isOnline) => {
  if (!isOnline) return '#64748b';
  if (level > 85) return '#dc2626';
  if (level >= 70) return '#ea580c';
  if (level >= 30) return '#f59e0b';
  return '#10b981';
};

const getFillBadge = (level, isOnline) => {
  if (!isOnline) return { bg: '#f1f5f9', color: '#64748b', border: '#e2e8f0', label: 'Offline' };
  if (level > 85) return { bg: '#fef2f2', color: '#dc2626', border: '#fecaca', label: 'Quá tải' };
  if (level >= 70) return { bg: '#fff7ed', color: '#ea580c', border: '#fed7aa', label: 'Cảnh báo' };
  if (level >= 30) return { bg: '#fefce8', color: '#ca8a04', border: '#fef08a', label: 'Mức vừa' };
  return { bg: '#ecfdf5', color: '#059669', border: '#a7f3d0', label: 'Trống' };
};

export default function SmartBinsPage({ bins = [], onSendCommand, onSelectBinForMap, onNotify }) {
  const [filterTab, setFilterTab] = useState('ALL'); // ALL | CRITICAL | WARNING | NORMAL | OFFLINE | OPEN
  const [searchTerm, setSearchTerm] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Command & Modal states
  const [commandLoading, setCommandLoading] = useState({});
  const [editingBin, setEditingBin] = useState(null);
  const [editLat, setEditLat] = useState('');
  const [editLng, setEditLng] = useState('');
  const [savingCoords, setSavingCoords] = useState(false);
  const [copiedId, setCopiedId] = useState(null);
  const [selectedDetailBin, setSelectedDetailBin] = useState(null);

  // Reset page when filters change
  useEffect(() => {
    setCurrentPage(1);
  }, [searchTerm, filterTab]);

  // Real KPI Metrics calculated directly from bins state
  const metrics = useMemo(() => {
    let critical = 0; // > 85%
    let warning = 0;  // 70% - 85%
    let normal = 0;   // < 70%
    let offline = 0;
    let openCount = 0;
    let autoCount = 0;

    (bins || []).forEach(b => {
      if (!b.is_online) {
        offline++;
      } else {
        const lvl = Number(b.level_percent || 0);
        if (lvl > 85) critical++;
        else if (lvl >= 70) warning++;
        else normal++;
      }

      if (b.state === 'OPEN') openCount++;
      if (b.control_mode === 'AUTO') autoCount++;
    });

    return {
      total: bins?.length || 0,
      online: (bins?.length || 0) - offline,
      offline,
      critical,
      warning,
      normal,
      openCount,
      autoCount
    };
  }, [bins]);

  // Filter and Search Bins
  const filteredBins = useMemo(() => {
    return (bins || []).filter(bin => {
      // Search
      if (searchTerm) {
        const term = searchTerm.toLowerCase().trim();
        const match = (bin.name || '').toLowerCase().includes(term) ||
                      (bin.device_id || '').toLowerCase().includes(term) ||
                      (bin.location || '').toLowerCase().includes(term);
        if (!match) return false;
      }

      // Filter Tab
      const lvl = Number(bin.level_percent || 0);
      if (filterTab === 'CRITICAL') return bin.is_online && lvl > 85;
      if (filterTab === 'WARNING') return bin.is_online && lvl >= 70 && lvl <= 85;
      if (filterTab === 'NORMAL') return bin.is_online && lvl < 70;
      if (filterTab === 'OFFLINE') return !bin.is_online;
      if (filterTab === 'OPEN') return bin.state === 'OPEN';

      return true;
    }).sort((a, b) => {
      // Sắp xếp: Thùng quá tải lên đầu, offline xuống cuối
      if (a.is_online && !b.is_online) return -1;
      if (!a.is_online && b.is_online) return 1;
      return (Number(b.level_percent) || 0) - (Number(a.level_percent) || 0);
    });
  }, [bins, filterTab, searchTerm]);

  // Pagination Slice
  const totalPages = Math.max(1, Math.ceil(filteredBins.length / pageSize));
  const paginatedBins = useMemo(() => {
    return filteredBins.slice((currentPage - 1) * pageSize, currentPage * pageSize);
  }, [filteredBins, currentPage, pageSize]);

  // Handle Command Execution (Mở/Đóng nắp, AUTO/MANUAL)
  const handleExecuteCommand = async (binId, action) => {
    if (!onSendCommand) {
      if (onNotify) onNotify('Chưa cấu hình hàm gửi lệnh từ xa.', 'warning');
      return;
    }

    setCommandLoading(prev => ({ ...prev, [`${binId}_${action}`]: true }));
    try {
      await onSendCommand(binId, action);
    } catch (err) {
      if (onNotify) onNotify(`Gửi lệnh thất bại: ${err.message}`, 'error');
    } finally {
      setCommandLoading(prev => ({ ...prev, [`${binId}_${action}`]: false }));
    }
  };

  // Copy GPS Coordinates
  const handleCopyCoords = (binId, lat, lng) => {
    if (!lat || !lng) return;
    navigator.clipboard.writeText(`${lat}, ${lng}`);
    setCopiedId(binId);
    setTimeout(() => setCopiedId(null), 2000);
    if (onNotify) onNotify(`Đã sao chép tọa độ #${binId} vào clipboard!`, 'info');
  };

  // Save Edited Coordinates
  const handleSaveCoordinates = async (e) => {
    e.preventDefault();
    if (!editingBin) return;

    const lat = Number(editLat);
    const lng = Number(editLng);

    if (!Number.isFinite(lat) || !Number.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
      if (onNotify) onNotify('Tọa độ GPS không hợp lệ (Vĩ độ -90 đến 90, Kinh độ -180 đến 180).', 'error');
      return;
    }

    setSavingCoords(true);
    try {
      await api.updateCoordinates(editingBin.device_id, lat, lng);
      if (onNotify) onNotify(`Đã cập nhật tọa độ mới cho ${editingBin.name || editingBin.device_id}!`, 'success');
      setEditingBin(null);
    } catch (err) {
      if (onNotify) onNotify(`Lỗi cập nhật tọa độ: ${err.message}`, 'error');
    } finally {
      setSavingCoords(false);
    }
  };

  // Export CSV
  const handleExportCSV = () => {
    if (!bins || bins.length === 0) {
      if (onNotify) onNotify('Không có dữ liệu thùng rác để xuất file.', 'warning');
      return;
    }

    const headers = ['Mã Thiết Bị', 'Tên Thùng', 'Vị Trí', 'Vĩ Độ (Lat)', 'Kinh Độ (Lng)', 'Mức Rác (%)', 'Trạng Thái Nắp', 'Chế Độ', 'Trực Tuyến'];
    const rows = bins.map(b => [
      b.device_id || '',
      `"${(b.name || '').replace(/"/g, '""')}"`,
      `"${(b.location || '').replace(/"/g, '""')}"`,
      b.latitude || '',
      b.longitude || '',
      b.level_percent || 0,
      b.state === 'OPEN' ? 'Đang mở' : 'Đã đóng',
      b.control_mode === 'AUTO' ? 'Tự động' : 'Thủ công',
      b.is_online ? 'Trực tuyến' : 'Ngoại tuyến'
    ]);

    const csvContent = 'data:text/csv;charset=utf-8,\uFEFF' + [headers.join(','), ...rows.map(e => e.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `danh_sach_thung_rac_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    if (onNotify) onNotify('Đã xuất file CSV danh sách thùng rác thành công!', 'success');
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px', paddingBottom: '36px' }}>

      {/* 1. Page Header & Summary Banner */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '14px'
      }}>
        <div>
          <h1 style={{
            fontSize: '22px',
            fontWeight: 800,
            color: '#111a4a',
            margin: '0 0 4px 0',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            <span>Quản Lý Smart Bins</span>
          </h1>
          <p style={{ fontSize: '13px', color: '#64748b', margin: 0 }}>
            Giám sát dung lượng mức rác thời gian thực, điều khiển nắp từ xa và quản lý vị trí GPS cảm biến.
          </p>
        </div>

        {/* Action Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button
            onClick={handleExportCSV}
            style={{
              padding: '9px 14px',
              borderRadius: '10px',
              border: '1px solid #cbd5e1',
              backgroundColor: '#ffffff',
              color: '#334155',
              fontSize: '13px',
              fontWeight: 600,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
              transition: 'all 150ms ease'
            }}
          >
            <Download size={14} color="#64748b" />
            <span>Xuất CSV</span>
          </button>
        </div>
      </div>

      {/* 2. KPI Top Stats Cards */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
        gap: '14px'
      }}>
        {/* Card 1: Tổng số thùng */}
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '14px',
          padding: '16px 20px',
          border: '1px solid #e2e8f0',
          boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
          display: 'flex',
          alignItems: 'center',
          gap: '14px'
        }}>
          <div style={{
            width: '42px',
            height: '42px',
            borderRadius: '12px',
            backgroundColor: '#eff6ff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0
          }}>
            <Trash2 size={20} color="#2563eb" />
          </div>
          <div>
            <span style={{ fontSize: '11.5px', fontWeight: 600, color: '#64748b' }}>Tổng số thùng rác</span>
            <div style={{ fontSize: '22px', fontWeight: 800, color: '#111a4a', lineHeight: 1.1 }}>
              {metrics.total}
            </div>
            <div style={{ fontSize: '11px', color: '#10b981', fontWeight: 600, marginTop: '2px' }}>
              {metrics.online} đang trực tuyến
            </div>
          </div>
        </div>

        {/* Card 2: Quá tải (>85%) */}
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '14px',
          padding: '16px 20px',
          border: `1px solid ${metrics.critical > 0 ? '#fecaca' : '#e2e8f0'}`,
          boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
          display: 'flex',
          alignItems: 'center',
          gap: '14px'
        }}>
          <div style={{
            width: '42px',
            height: '42px',
            borderRadius: '12px',
            backgroundColor: metrics.critical > 0 ? '#fef2f2' : '#f8fafc',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0
          }}>
            <AlertTriangle size={20} color={metrics.critical > 0 ? '#dc2626' : '#64748b'} />
          </div>
          <div>
            <span style={{ fontSize: '11.5px', fontWeight: 600, color: '#64748b' }}>Quá tải (&gt; 85%)</span>
            <div style={{ fontSize: '22px', fontWeight: 800, color: metrics.critical > 0 ? '#dc2626' : '#111a4a', lineHeight: 1.1 }}>
              {metrics.critical}
            </div>
            <div style={{ fontSize: '11px', color: metrics.critical > 0 ? '#dc2626' : '#64748b', fontWeight: 700, marginTop: '2px' }}>
              {metrics.critical > 0 ? 'Cần thu gom ngay' : 'Mức an toàn'}
            </div>
          </div>
        </div>

        {/* Card 3: Cảnh báo đầy (70-85%) */}
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '14px',
          padding: '16px 20px',
          border: `1px solid ${metrics.warning > 0 ? '#fed7aa' : '#e2e8f0'}`,
          boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
          display: 'flex',
          alignItems: 'center',
          gap: '14px'
        }}>
          <div style={{
            width: '42px',
            height: '42px',
            borderRadius: '12px',
            backgroundColor: metrics.warning > 0 ? '#fff7ed' : '#f8fafc',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0
          }}>
            <Radio size={20} color={metrics.warning > 0 ? '#ea580c' : '#64748b'} />
          </div>
          <div>
            <span style={{ fontSize: '11.5px', fontWeight: 600, color: '#64748b' }}>Cảnh báo đầy (70-85%)</span>
            <div style={{ fontSize: '22px', fontWeight: 800, color: metrics.warning > 0 ? '#ea580c' : '#111a4a', lineHeight: 1.1 }}>
              {metrics.warning}
            </div>
            <div style={{ fontSize: '11px', color: '#ea580c', fontWeight: 600, marginTop: '2px' }}>
              Sắp cần thu gom
            </div>
          </div>
        </div>

        {/* Card 4: Nắp đang mở & Chế độ */}
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '14px',
          padding: '16px 20px',
          border: '1px solid #e2e8f0',
          boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
          display: 'flex',
          alignItems: 'center',
          gap: '14px'
        }}>
          <div style={{
            width: '42px',
            height: '42px',
            borderRadius: '12px',
            backgroundColor: '#ecfdf5',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0
          }}>
            <Cpu size={20} color="#10b981" />
          </div>
          <div>
            <span style={{ fontSize: '11.5px', fontWeight: 600, color: '#64748b' }}>Trạng thái Nắp & Chế độ</span>
            <div style={{ fontSize: '22px', fontWeight: 800, color: '#111a4a', lineHeight: 1.1 }}>
              {metrics.openCount} mở
            </div>
            <div style={{ fontSize: '11px', color: '#059669', fontWeight: 600, marginTop: '2px' }}>
              {metrics.autoCount} chế độ Tự động
            </div>
          </div>
        </div>
      </div>

      {/* 3. Toolbar & Filter Pills */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '12px'
      }}>
        {/* Filter Pills */}
        <div style={{ display: 'inline-flex', padding: '3px', borderRadius: '10px', backgroundColor: '#f1f5f9', flexWrap: 'wrap', gap: '2px' }}>
          {[
            { id: 'ALL', label: `Tất cả (${metrics.total})` },
            { id: 'CRITICAL', label: `🚨 Quá tải (${metrics.critical})` },
            { id: 'WARNING', label: `⚠️ Cảnh báo (${metrics.warning})` },
            { id: 'NORMAL', label: `🟢 Mức vừa (${metrics.normal})` },
            { id: 'OPEN', label: `🔓 Đang mở (${metrics.openCount})` },
            { id: 'OFFLINE', label: `⚪ Ngoại tuyến (${metrics.offline})` }
          ].map(pill => (
            <button
              key={pill.id}
              onClick={() => setFilterTab(pill.id)}
              style={{
                padding: '6px 12px',
                borderRadius: '8px',
                fontSize: '12px',
                fontWeight: 600,
                border: 'none',
                cursor: 'pointer',
                backgroundColor: filterTab === pill.id ? '#ffffff' : 'transparent',
                color: filterTab === pill.id ? '#111a4a' : '#64748b',
                boxShadow: filterTab === pill.id ? '0 1px 3px rgba(0,0,0,0.08)' : 'none',
                transition: 'all 120ms ease'
              }}
            >
              {pill.label}
            </button>
          ))}
        </div>

        {/* Global Search Input */}
        <div style={{ position: 'relative', minWidth: '240px' }}>
          <Search size={14} color="#94a3b8" style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)' }} />
          <input
            type="text"
            placeholder="Tìm mã thùng, tên vị trí, địa chỉ..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{
              width: '100%',
              padding: '8px 12px 8px 34px',
              borderRadius: '10px',
              border: '1px solid #cbd5e1',
              fontSize: '12.5px',
              backgroundColor: '#ffffff',
              outline: 'none',
              transition: 'border-color 150ms ease'
            }}
          />
        </div>
      </div>

      {/* 4. Smart Bins Table Container (ALL CENTERED, BALANCED, MODERN) */}
      <div style={{
        backgroundColor: '#ffffff',
        borderRadius: '16px',
        border: '1px solid #e2e8f0',
        boxShadow: '0 1px 4px rgba(0,0,0,0.03)',
        overflow: 'hidden'
      }}>
        <div style={{ overflowX: 'auto', width: '100%', WebkitOverflowScrolling: 'touch' }}>
          <table style={{ width: '100%', minWidth: '1080px', borderCollapse: 'collapse', textAlign: 'center', fontSize: '12.5px' }}>
            <thead>
              <tr style={{
                backgroundColor: '#f8fafc',
                borderBottom: '1px solid #e2e8f0',
                color: '#64748b',
                fontSize: '11px',
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
                whiteSpace: 'nowrap'
              }}>
                <th style={{ padding: '12px 16px', textAlign: 'left', whiteSpace: 'nowrap' }}>Thùng rác</th>
                <th style={{ padding: '12px 16px', textAlign: 'left', whiteSpace: 'nowrap' }}>Vị trí & GPS</th>
                <th style={{ padding: '12px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>Mức rác (%)</th>
                <th style={{ padding: '12px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>Nắp thùng</th>
                <th style={{ padding: '12px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>Chế độ</th>
                <th style={{ padding: '12px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>Thu gom</th>
                <th style={{ padding: '12px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>Cập nhật</th>
                <th style={{ padding: '12px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {paginatedBins.length > 0 ? (
                paginatedBins.map((bin) => {
                  const level = Math.min(100, Math.max(0, Number(bin.level_percent || 0)));
                  const isOnline = Boolean(bin.is_online);
                  const isLidOpen = bin.state === 'OPEN';
                  const isAuto = bin.control_mode === 'AUTO';
                  const fillBadge = getFillBadge(level, isOnline);
                  const fillColor = getFillColor(level, isOnline);
                  const relTime = getVietnamRelativeTime(bin.last_seen || bin.updated_at);
                  const isCopied = copiedId === bin.device_id;

                  const isOpening = commandLoading[`${bin.device_id}_OPEN`];
                  const isClosing = commandLoading[`${bin.device_id}_CLOSE`];
                  const isModeChanging = commandLoading[`${bin.device_id}_AUTO`] || commandLoading[`${bin.device_id}_MANUAL`];

                  return (
                    <tr
                      key={bin.device_id}
                      style={{
                        borderBottom: '1px solid #f1f5f9',
                        transition: 'background-color 120ms ease'
                      }}
                      onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#fafafa'; }}
                      onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'transparent'; }}
                    >
                      {/* 1. Device Info (Left Aligned) */}
                      <td style={{ padding: '14px 16px', textAlign: 'left', whiteSpace: 'nowrap' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                          <div style={{
                            width: '34px',
                            height: '34px',
                            borderRadius: '10px',
                            backgroundColor: `${fillColor}15`,
                            color: fillColor,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            flexShrink: 0
                          }}>
                            <Trash2 size={16} />
                          </div>
                          <div>
                            <div style={{ fontWeight: 800, color: '#111a4a', fontSize: '13px' }}>
                              {bin.name || bin.device_id}
                            </div>
                            <div style={{ fontSize: '11px', color: '#64748b', fontFamily: 'monospace' }}>
                              #{bin.device_id}
                            </div>
                          </div>
                        </div>
                      </td>

                      {/* 2. Location & GPS (Left Aligned) */}
                      <td style={{ padding: '14px 16px', textAlign: 'left', minWidth: '240px' }}>
                        <div style={{ fontSize: '12.5px', color: '#334155', fontWeight: 600, lineHeight: 1.4 }}>
                          📍 {bin.location || 'Chưa cập nhật'}
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '2px' }}>
                          <span style={{ fontSize: '11px', color: '#64748b', fontFamily: 'monospace', whiteSpace: 'nowrap' }}>
                            {bin.latitude ? `${Number(bin.latitude).toFixed(4)}, ${Number(bin.longitude).toFixed(4)}` : 'Chưa có GPS'}
                          </span>
                          {bin.latitude && (
                            <button
                              onClick={() => handleCopyCoords(bin.device_id, bin.latitude, bin.longitude)}
                              style={{ background: 'none', border: 'none', color: isCopied ? '#16a34a' : '#94a3b8', cursor: 'pointer', padding: '1px' }}
                              title="Sao chép tọa độ"
                            >
                              {isCopied ? <Check size={12} /> : <Copy size={12} />}
                            </button>
                          )}
                        </div>
                      </td>

                      {/* 3. Level Percent & Visual Bar (Center) */}
                      <td style={{ padding: '14px 16px', textAlign: 'center', minWidth: '130px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
                          <span style={{ fontSize: '13px', fontWeight: 800, color: fillColor, minWidth: '38px' }}>
                            {isOnline ? `${level}%` : 'OFF'}
                          </span>
                          <div style={{ width: '60px', height: '6px', borderRadius: '99px', backgroundColor: '#e2e8f0', overflow: 'hidden' }}>
                            <div style={{
                              width: `${isOnline ? level : 0}%`,
                              height: '100%',
                              borderRadius: '99px',
                              backgroundColor: fillColor,
                              transition: 'width 300ms ease'
                            }} />
                          </div>
                        </div>
                        <span style={{
                          display: 'inline-block',
                          marginTop: '3px',
                          fontSize: '10px',
                          fontWeight: 700,
                          padding: '1px 6px',
                          borderRadius: '99px',
                          backgroundColor: fillBadge.bg,
                          color: fillBadge.color,
                          border: `1px solid ${fillBadge.border}`
                        }}>
                          {fillBadge.label}
                        </span>
                      </td>

                      {/* 4. Lid Status (Badge Only - Clean) */}
                      <td style={{ padding: '14px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>
                        <span style={{
                          padding: '4px 10px',
                          borderRadius: '999px',
                          fontSize: '11px',
                          fontWeight: 700,
                          backgroundColor: isLidOpen ? '#ecfdf5' : '#f1f5f9',
                          color: isLidOpen ? '#059669' : '#64748b',
                          border: isLidOpen ? '1px solid #a7f3d0' : '1px solid #e2e8f0'
                        }}>
                          {isLidOpen ? 'Đang mở' : 'Đã đóng'}
                        </span>
                      </td>

                      {/* 5. Control Mode (Interactive Toggle Badge) */}
                      <td style={{ padding: '14px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>
                        <button
                          onClick={() => handleExecuteCommand(bin.device_id, isAuto ? 'MANUAL' : 'AUTO')}
                          disabled={isModeChanging}
                          style={{
                            padding: '4px 10px',
                            borderRadius: '999px',
                            fontSize: '11px',
                            fontWeight: 700,
                            backgroundColor: isAuto ? '#eff6ff' : '#fff7ed',
                            color: isAuto ? '#2563eb' : '#ea580c',
                            border: isAuto ? '1px solid #bfdbfe' : '1px solid #fed7aa',
                            cursor: isModeChanging ? 'wait' : 'pointer',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '5px',
                            transition: 'all 120ms ease'
                          }}
                          title={isAuto ? 'Đang ở chế độ Tự động. Bấm để chuyển sang Thủ công' : 'Đang ở chế độ Thủ công. Bấm để khôi phục Tự động (AUTO)'}
                        >
                          {isModeChanging ? (
                            <RefreshCw size={11} className="spin-animation" />
                          ) : isAuto ? (
                            <span style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#2563eb' }} />
                          ) : (
                            <RotateCcw size={11} />
                          )}
                          <span>{isAuto ? 'Tự động' : 'Thủ công'}</span>
                        </button>
                      </td>

                      {/* 6. Collection Status (Center) */}
                      <td style={{ padding: '14px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>
                        <span style={{
                          padding: '4px 10px',
                          borderRadius: '999px',
                          fontSize: '11px',
                          fontWeight: 700,
                          backgroundColor: bin.collection_status === 'RESERVED' ? '#eff6ff' : '#f1f5f9',
                          color: bin.collection_status === 'RESERVED' ? '#2563eb' : '#64748b',
                          border: bin.collection_status === 'RESERVED' ? '1px solid #bfdbfe' : '1px solid #e2e8f0'
                        }}>
                          {bin.collection_status === 'RESERVED' ? 'Đang trong tuyến' : 'Chờ thu gom'}
                        </span>
                      </td>

                      {/* 7. Last Seen Time (Center) */}
                      <td style={{ padding: '14px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>
                        <span style={{ fontSize: '11.5px', color: relTime.color, fontWeight: 600 }}>
                          {relTime.text}
                        </span>
                      </td>

                      {/* 8. Actions (Unified Modern Icon Buttons) */}
                      <td style={{ padding: '14px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                          
                          {/* 1. Toggle Lid Button */}
                          <button
                            onClick={() => handleExecuteCommand(bin.device_id, isLidOpen ? 'CLOSE' : 'OPEN')}
                            disabled={isOpening || isClosing}
                            style={{
                              width: '30px',
                              height: '30px',
                              borderRadius: '8px',
                              backgroundColor: isLidOpen ? '#fef2f2' : '#ecfdf5',
                              color: isLidOpen ? '#dc2626' : '#059669',
                              border: isLidOpen ? '1px solid #fecaca' : '1px solid #a7f3d0',
                              cursor: isOpening || isClosing ? 'wait' : 'pointer',
                              display: 'inline-flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              transition: 'all 120ms ease'
                            }}
                            title={isOpening || isClosing ? 'Đang gửi lệnh...' : isLidOpen ? 'Đóng nắp thùng từ xa' : 'Mở nắp thùng từ xa'}
                          >
                            {isOpening || isClosing ? (
                              <RefreshCw size={13} className="spin-animation" />
                            ) : (
                              <Power size={13} />
                            )}
                          </button>

                          {/* 2. Switch/Reset to Auto Button */}
                          <button
                            onClick={() => handleExecuteCommand(bin.device_id, 'AUTO')}
                            disabled={isModeChanging}
                            style={{
                              width: '30px',
                              height: '30px',
                              borderRadius: '8px',
                              backgroundColor: isAuto ? '#f8fafc' : '#ecfdf5',
                              color: isAuto ? '#94a3b8' : '#059669',
                              border: isAuto ? '1px solid #e2e8f0' : '1px solid #a7f3d0',
                              cursor: isModeChanging ? 'wait' : 'pointer',
                              display: 'inline-flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              transition: 'all 120ms ease'
                            }}
                            title={isModeChanging ? 'Đang kích hoạt...' : isAuto ? 'Thùng rác đang ở chế độ Tự động (AUTO)' : 'Bấm để khôi phục về chế độ Tự động (AUTO)'}
                          >
                            {isModeChanging ? (
                              <RefreshCw size={13} className="spin-animation" />
                            ) : (
                              <RotateCcw size={13} />
                            )}
                          </button>

                          {/* 3. Map Fly-to Button */}
                          <button
                            onClick={() => {
                              if (onSelectBinForMap) onSelectBinForMap(bin);
                            }}
                            style={{
                              width: '30px',
                              height: '30px',
                              borderRadius: '8px',
                              backgroundColor: '#eff6ff',
                              color: '#2563eb',
                              border: '1px solid #bfdbfe',
                              cursor: 'pointer',
                              display: 'inline-flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              transition: 'all 120ms ease'
                            }}
                            title="Định vị vị trí trên Bản đồ GIS"
                          >
                            <Navigation size={13} />
                          </button>

                          {/* 4. Edit GPS Button */}
                          <button
                            onClick={() => {
                              setEditingBin(bin);
                              setEditLat(bin.latitude || '');
                              setEditLng(bin.longitude || '');
                            }}
                            style={{
                              width: '30px',
                              height: '30px',
                              borderRadius: '8px',
                              backgroundColor: '#f8fafc',
                              color: '#64748b',
                              border: '1px solid #e2e8f0',
                              cursor: 'pointer',
                              display: 'inline-flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              transition: 'all 120ms ease'
                            }}
                            title="Chỉnh sửa tọa độ GPS"
                          >
                            <Edit3 size={13} />
                          </button>

                        </div>
                      </td>
                    </tr>
                  );
                })
              ) : (
                <tr>
                  <td colSpan={8} style={{ padding: '36px', textAlign: 'center', color: '#94a3b8' }}>
                    Không tìm thấy thùng rác nào phù hợp với điều kiện tìm kiếm.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* 5. Pagination Bar Footer (Matching Universal Design) */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '14px 20px',
          borderTop: '1px solid #f1f5f9',
          flexWrap: 'wrap',
          gap: '12px'
        }}>
          {/* Left: Records info */}
          <div style={{ fontSize: '13px', color: '#475569', fontWeight: 500 }}>
            Hiển thị {filteredBins.length === 0 ? 0 : (currentPage - 1) * pageSize + 1} - {Math.min(currentPage * pageSize, filteredBins.length)} / {filteredBins.length} thùng rác
          </div>

          {/* Center/Right: Numbered pagination & Page size selector */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              {/* Prev Button */}
              <button
                onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                disabled={currentPage === 1}
                style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '8px',
                  border: '1px solid #e2e8f0',
                  backgroundColor: '#ffffff',
                  color: currentPage === 1 ? '#cbd5e1' : '#334155',
                  cursor: currentPage === 1 ? 'not-allowed' : 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                <ChevronLeft size={16} />
              </button>

              {/* Single Active Page Indicator Button */}
              <div
                style={{
                  minWidth: '32px',
                  height: '32px',
                  padding: '0 8px',
                  borderRadius: '8px',
                  backgroundColor: '#059669',
                  color: '#ffffff',
                  fontWeight: 700,
                  fontSize: '13px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  userSelect: 'none'
                }}
              >
                {currentPage}
              </div>

              {/* Next Button */}
              <button
                onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                disabled={currentPage === totalPages}
                style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '8px',
                  border: '1px solid #e2e8f0',
                  backgroundColor: '#ffffff',
                  color: currentPage === totalPages ? '#cbd5e1' : '#334155',
                  cursor: currentPage === totalPages ? 'not-allowed' : 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                <ChevronRight size={16} />
              </button>
            </div>

            {/* Page Size Dropdown */}
            <select
              value={pageSize}
              onChange={(e) => { setPageSize(Number(e.target.value)); setCurrentPage(1); }}
              style={{
                padding: '6px 10px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                fontSize: '12.5px',
                fontWeight: 600,
                color: '#334155',
                backgroundColor: '#ffffff',
                outline: 'none',
                cursor: 'pointer'
              }}
            >
              <option value={5}>5 / trang</option>
              <option value={10}>10 / trang</option>
              <option value={20}>20 / trang</option>
              <option value={50}>50 / trang</option>
            </select>
          </div>
        </div>
      </div>

      {/* 6. MODAL: CHỈNH SỬA TỌA ĐỘ GPS THÙNG RÁC */}
      {editingBin && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.55)',
          backdropFilter: 'blur(5px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1100,
          padding: '20px'
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '18px',
            padding: '24px',
            width: '440px',
            maxWidth: '100%',
            boxShadow: '0 20px 40px rgba(0,0,0,0.15)',
            border: '1px solid #e2e8f0'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <div style={{ width: '32px', height: '32px', borderRadius: '8px', backgroundColor: '#eff6ff', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#2563eb' }}>
                  <MapPin size={18} />
                </div>
                <h3 style={{ fontSize: '16px', fontWeight: 800, color: '#111a4a', margin: 0 }}>
                  Chỉnh sửa Vị trí GPS
                </h3>
              </div>
              <button
                onClick={() => setEditingBin(null)}
                style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer', padding: '4px' }}
              >
                <X size={18} />
              </button>
            </div>

            <p style={{ fontSize: '12.5px', color: '#64748b', marginBottom: '16px', lineHeight: 1.4 }}>
              Cập nhật tọa độ thực địa cho <strong>{editingBin.name || editingBin.device_id}</strong> (#{editingBin.device_id}).
            </p>

            <form onSubmit={handleSaveCoordinates}>
              <div style={{ marginBottom: '12px' }}>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, color: '#334155', marginBottom: '4px' }}>
                  Vĩ độ (Latitude)
                </label>
                <input
                  type="number"
                  step="any"
                  required
                  placeholder="VD: 10.7769"
                  value={editLat}
                  onChange={(e) => setEditLat(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '10px 12px',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    fontSize: '13px',
                    outline: 'none'
                  }}
                />
              </div>

              <div style={{ marginBottom: '20px' }}>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, color: '#334155', marginBottom: '4px' }}>
                  Kinh độ (Longitude)
                </label>
                <input
                  type="number"
                  step="any"
                  required
                  placeholder="VD: 106.7009"
                  value={editLng}
                  onChange={(e) => setEditLng(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '10px 12px',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    fontSize: '13px',
                    outline: 'none'
                  }}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                <button
                  type="button"
                  onClick={() => setEditingBin(null)}
                  style={{
                    padding: '8px 14px',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    backgroundColor: '#ffffff',
                    fontSize: '12px',
                    fontWeight: 600,
                    color: '#64748b',
                    cursor: 'pointer'
                  }}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={savingCoords}
                  style={{
                    padding: '8px 16px',
                    fontSize: '12px',
                    fontWeight: 700,
                    borderRadius: '8px',
                    backgroundColor: '#2563eb',
                    color: '#ffffff',
                    border: 'none',
                    cursor: 'pointer'
                  }}
                >
                  {savingCoords ? 'Đang lưu...' : 'Lưu tọa độ'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

    </div>
  );
}
