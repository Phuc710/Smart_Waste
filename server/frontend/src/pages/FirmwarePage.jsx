import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
  Cpu,
  UploadCloud,
  Rocket,
  Activity,
  FileCode,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  RotateCcw,
  ShieldCheck,
  Copy,
  Check,
  RefreshCw,
  Search,
  ChevronLeft,
  ChevronRight,
  Filter,
  CheckSquare,
  Square,
  Sparkles,
  ArrowRight,
  Clock,
  Download,
  Trash2,
  Info,
  Server,
  Layers,
  Terminal,
  FileText,
  X,
  ExternalLink,
  Calendar,
  Edit3,
  HardDrive
} from 'lucide-react';
import { api } from '../services/api';
import { getSocket } from '../services/socket';

// Format Vietnam Date Time clean: DD/MM/YYYY HH:mm:ss
function formatVietnamDateTimeClean(dateInput) {
  if (!dateInput) return '—';
  const d = new Date(dateInput);
  if (isNaN(d.getTime())) return '—';
  const pad = (n) => String(n).padStart(2, '0');
  const day = pad(d.getDate());
  const month = pad(d.getMonth() + 1);
  const year = d.getFullYear();
  const hour = pad(d.getHours());
  const min = pad(d.getMinutes());
  const sec = pad(d.getSeconds());
  return `${day}/${month}/${year} ${hour}:${min}:${sec}`;
}

// Format Vietnam Time only: HH:mm:ss
function formatVietnamTimeClean(dateInput) {
  if (!dateInput) return '—';
  const d = new Date(dateInput);
  if (isNaN(d.getTime())) return '—';
  const pad = (n) => String(n).padStart(2, '0');
  const hour = pad(d.getHours());
  const min = pad(d.getMinutes());
  const sec = pad(d.getSeconds());
  return `${hour}:${min}:${sec}`;
}

export default function FirmwarePage({ notify, bins = [], onOpenMap }) {
  // 4 Tabs: 'releases' | 'deploy' | 'processing' | 'logs'
  const [activeTab, setActiveTab] = useState('releases');

  // Releases State
  const [releases, setReleases] = useState([]);
  const [loadingReleases, setLoadingReleases] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadSuccess, setUploadSuccess] = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const [fileDetails, setFileDetails] = useState({
    version: '',
    deviceModel: 'ESP32-CAM',
    releaseNotes: '- Tối ưu hiệu suất kết nối\n- Cải thiện thuật toán đo mức đầy\n- Sửa lỗi cảm biến'
  });
  const [copiedSha, setCopiedSha] = useState(null);

  // Selected Release for Deploy & Popup Modal
  const [selectedRelease, setSelectedRelease] = useState(null);
  const [modalRelease, setModalRelease] = useState(null);

  // Deployments State (Tab 2)
  const [deployments, setDeployments] = useState([]);
  const [loadingDeployments, setLoadingDeployments] = useState(false);
  const [selectedDeviceIds, setSelectedDeviceIds] = useState(new Set());
  const [searchQuery, setSearchQuery] = useState('');
  const [filterMode, setFilterMode] = useState('ONLINE'); // 'ALL' | 'ONLINE' | 'OFFLINE'
  const [page, setPage] = useState(1);
  const [perPage, setPerPage] = useState(10);
  const [confirmModalOpen, setConfirmModalOpen] = useState(false);
  const [deploying, setDeploying] = useState(false);

  // Live Selected Deployment for Monitoring (Tab 3)
  const [selectedDeploymentId, setSelectedDeploymentId] = useState(null);
  const [deploymentDetails, setDeploymentDetails] = useState(null);
  const [retryingJobId, setRetryingJobId] = useState(null);
  const [cancellingDepId, setCancellingDepId] = useState(null);

  // Realtime Live Event Logs (Tab 4)
  const [liveLogs, setLiveLogs] = useState([]);
  const [logFilterDevice, setLogFilterDevice] = useState('ALL');
  const [logFilterType, setLogFilterType] = useState('ALL'); // 'ALL' | 'INFO' | 'SUCCESS' | 'ERROR'

  const fileInputRef = useRef(null);

  // Helper to append log item
  const addLogEvent = useCallback((text, type = 'info', deviceId = null) => {
    const newLog = {
      id: `${Date.now()}-${Math.random().toString(36).substr(2, 6)}`,
      timestamp: new Date().toISOString(),
      text,
      type, // 'info' | 'success' | 'warn' | 'error'
      deviceId
    };
    setLiveLogs(prev => [newLog, ...prev].slice(0, 300));
  }, []);

  // 1. Fetch Releases
  const fetchReleases = useCallback(async () => {
    setLoadingReleases(true);
    try {
      const data = await api.getFirmwareReleases();
      if (Array.isArray(data)) {
        setReleases(data);
        if (data.length > 0) {
          setSelectedRelease(prev => {
            if (prev) {
              const stillExists = data.find(r => r.id === prev.id);
              return stillExists || data[0];
            }
            return data[0];
          });
        }
      }
    } catch (err) {
      notify?.(err.message || 'Không tải được danh sách firmware releases', 'error');
    } finally {
      setLoadingReleases(false);
    }
  }, [notify]);

  // 2. Fetch Deployments
  const fetchDeployments = useCallback(async () => {
    setLoadingDeployments(true);
    try {
      const data = await api.getOtaDeployments();
      if (Array.isArray(data)) {
        setDeployments(data);
        if (data.length > 0 && !selectedDeploymentId) {
          setSelectedDeploymentId(data[0].id);
        }
      }
    } catch (err) {
      notify?.(err.message || 'Không tải được lịch sử triển khai OTA', 'error');
    } finally {
      setLoadingDeployments(false);
    }
  }, [notify, selectedDeploymentId]);

  // 3. Fetch Deployment Details
  const fetchDeploymentDetails = useCallback(async (depId) => {
    if (!depId) return;
    try {
      const data = await api.getOtaDeployment(depId);
      if (data) {
        setDeploymentDetails(data);
      }
    } catch (_) {}
  }, []);

  useEffect(() => {
    fetchReleases();
    fetchDeployments();
  }, [fetchReleases, fetchDeployments]);

  useEffect(() => {
    if (selectedDeploymentId) {
      fetchDeploymentDetails(selectedDeploymentId);
    }
  }, [selectedDeploymentId, fetchDeploymentDetails]);

  // 4. Realtime Socket.IO Listeners
  useEffect(() => {
    const socket = getSocket();
    if (!socket) return;

    const handleJobUpdated = (payload) => {
      // Update deployment details in place
      setDeploymentDetails(prev => {
        if (!prev || prev.id !== payload.deploymentId) return prev;
        const updatedJobs = (prev.jobs || []).map(j => {
          if (j.id === payload.deviceJobId || j.device_id === payload.deviceId) {
            return {
              ...j,
              status: payload.status,
              progress_percent: payload.progressPercent,
              downloaded_bytes: payload.downloadedBytes,
              total_bytes: payload.totalBytes,
              error_code: payload.errorCode,
              error_message: payload.errorMessage,
              updated_at: payload.updatedAt
            };
          }
          return j;
        });
        return { ...prev, jobs: updatedJobs };
      });

      // Update deployments list status
      setDeployments(prev => prev.map(d => {
        if (d.id === payload.deploymentId) {
          const isSuccess = payload.status === 'SUCCESS';
          return {
            ...d,
            success_count: isSuccess ? (d.success_count || 0) + 1 : d.success_count
          };
        }
        return d;
      }));

      // Add to live event logs
      let logMsg = `Đã gửi lệnh cập nhật đến ${payload.deviceId}`;
      let logType = 'info';
      if (payload.status === 'SUCCESS') {
        logMsg = `${payload.deviceId} cập nhật thành công firmware ${payload.targetVersion || ''}`;
        logType = 'success';
      } else if (payload.status === 'DOWNLOADING') {
        const mb = ((payload.downloadedBytes || 0) / (1024 * 1024)).toFixed(2);
        logMsg = `${payload.deviceId} đang tải firmware (${mb} MB)`;
        logType = 'info';
      } else if (payload.status === 'VERIFYING') {
        logMsg = `${payload.deviceId} xác thực firmware thành công`;
        logType = 'info';
      } else if (payload.status === 'INSTALLING') {
        logMsg = `${payload.deviceId} đang cài đặt firmware`;
        logType = 'info';
      } else if (payload.status === 'REBOOTING') {
        logMsg = `${payload.deviceId} đang khởi động lại...`;
        logType = 'warn';
      } else if (payload.status === 'FAILED' || payload.status === 'TIMED_OUT') {
        logMsg = `${payload.deviceId} lỗi nạp firmware: ${payload.errorMessage || payload.errorCode || 'Thất bại'}`;
        logType = 'error';
      } else if (payload.status === 'ROLLBACK_SUCCESS') {
        logMsg = `${payload.deviceId} đã Rollback về phiên bản an toàn`;
        logType = 'warn';
      }
      addLogEvent(logMsg, logType, payload.deviceId);
    };

    const handleDepCreated = (payload) => {
      if (payload && payload.deployment) {
        setDeployments(prev => [payload.deployment, ...prev]);
        setSelectedDeploymentId(payload.deployment.id);
        (payload.jobs || []).forEach(j => {
          addLogEvent(`Đã gửi lệnh cập nhật đến ${j.device_id}`, 'info', j.device_id);
        });
      }
    };

    socket.on('otaJobUpdated', handleJobUpdated);
    socket.on('otaDeploymentCreated', handleDepCreated);

    return () => {
      socket.off('otaJobUpdated', handleJobUpdated);
      socket.off('otaDeploymentCreated', handleDepCreated);
    };
  }, [addLogEvent]);

  // Handle Copy SHA-256
  const handleCopySha = (sha, id) => {
    if (!sha) return;
    navigator.clipboard.writeText(sha);
    setCopiedSha(id || sha);
    notify?.('Đã sao chép SHA-256', 'info');
    setTimeout(() => setCopiedSha(null), 2000);
  };

  // Handle File Selection
  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.name.toLowerCase().endsWith('.bin')) {
      notify?.('Vui lòng chỉ chọn file định dạng binary compiled (.bin)', 'error');
      return;
    }

    if (file.size > 3.5 * 1024 * 1024) {
      notify?.('Kích thước file vượt quá giới hạn 3.5 MB của phân vùng OTA 4MB Flash', 'error');
      return;
    }

    setSelectedFile(file);
    setUploadSuccess(false);

    // Auto extract version from file name (e.g. smart-waste-v1.2.0.bin -> v1.2.0)
    const match = file.name.match(/v?(\d+\.\d+\.\d+(?:-[a-zA-Z0-9.]+)?)/i);
    if (match) {
      setFileDetails(prev => ({ ...prev, version: `v${match[1]}` }));
    }
  };

  // Handle Upload Release
  const handleUploadRelease = async (e) => {
    e.preventDefault();
    if (!selectedFile) {
      notify?.('Vui lòng chọn file firmware .bin', 'error');
      return;
    }

    if (!fileDetails.version || !fileDetails.version.match(/^v?\d+\.\d+\.\d+/)) {
      notify?.('Vui lòng nhập phiên bản SemVer hợp lệ (ví dụ: v1.2.0)', 'error');
      return;
    }

    setUploading(true);
    try {
      const buffer = await selectedFile.arrayBuffer();
      const headers = {
        'x-filename': encodeURIComponent(selectedFile.name),
        'x-version': encodeURIComponent(fileDetails.version),
        'x-device-model': encodeURIComponent(fileDetails.deviceModel),
        'x-release-notes': encodeURIComponent(fileDetails.releaseNotes)
      };

      const result = await api.uploadFirmwareRelease(buffer, headers);
      notify?.(result.message || 'Tạo bản phát hành firmware thành công!', 'success');
      setUploadSuccess(true);
      addLogEvent(`Đã xuất bản firmware mới ${fileDetails.version} (${selectedFile.name})`, 'success');

      await fetchReleases();
      if (result.release) {
        setSelectedRelease(result.release);
      }
    } catch (err) {
      notify?.(err.message || 'Lỗi tải lên firmware', 'error');
    } finally {
      setUploading(false);
    }
  };

  // Filter Target Devices for OTA Deploy (Tab 2)
  const filteredBins = useMemo(() => {
    return (bins || []).filter(b => {
      // Filter Mode
      if (filterMode === 'ONLINE' && !b.is_online) return false;
      if (filterMode === 'OFFLINE' && b.is_online) return false;

      // Search Query
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const idMatch = String(b.device_id || '').toLowerCase().includes(q);
        const nameMatch = String(b.name || '').toLowerCase().includes(q);
        const locMatch = String(b.location || '').toLowerCase().includes(q);
        if (!idMatch && !nameMatch && !locMatch) return false;
      }

      return true;
    });
  }, [bins, filterMode, searchQuery]);

  // Paginated Bins
  const totalPages = Math.max(1, Math.ceil(filteredBins.length / perPage));
  const paginatedBins = useMemo(() => {
    const start = (page - 1) * perPage;
    return filteredBins.slice(start, start + perPage);
  }, [filteredBins, page, perPage]);

  // Toggle Device Selection
  const toggleDeviceSelection = (deviceId) => {
    setSelectedDeviceIds(prev => {
      const next = new Set(prev);
      if (next.has(deviceId)) next.delete(deviceId);
      else next.add(deviceId);
      return next;
    });
  };

  const selectAllFiltered = () => {
    if (selectedDeviceIds.size === filteredBins.length && filteredBins.length > 0) {
      setSelectedDeviceIds(new Set());
    } else {
      setSelectedDeviceIds(new Set(filteredBins.map(b => b.device_id)));
    }
  };

  // Launch Deployment
  const handleStartDeployment = async () => {
    if (!selectedRelease) {
      notify?.('Vui lòng chọn bản release để triển khai', 'error');
      return;
    }

    if (selectedDeviceIds.size === 0) {
      notify?.('Vui lòng chọn ít nhất 1 thiết bị mục tiêu', 'error');
      return;
    }

    setDeploying(true);
    try {
      const result = await api.createOtaDeployment(selectedRelease.id, Array.from(selectedDeviceIds));
      notify?.(result.message || 'Đã phát động chiến dịch OTA thành công!', 'success');
      setConfirmModalOpen(false);
      
      const targetCount = selectedDeviceIds.size;
      setSelectedDeviceIds(new Set());
      
      await fetchDeployments();
      if (result.deployment) {
        setSelectedDeploymentId(result.deployment.id);
        fetchDeploymentDetails(result.deployment.id);
      }
      
      addLogEvent(`Bắt đầu triển khai OTA bản ${selectedRelease.version} cho ${targetCount} thiết bị`, 'info');
      setActiveTab('processing');
    } catch (err) {
      notify?.(err.message || 'Lỗi kích hoạt triển khai OTA', 'error');
    } finally {
      setDeploying(false);
    }
  };

  // Cancel Deployment
  const handleCancelDeployment = async (depId) => {
    if (!window.confirm('Bạn có chắc chắn muốn huỷ các tiến trình chưa thực hiện flash trong chiến dịch này không?')) return;
    setCancellingDepId(depId);
    try {
      const result = await api.cancelOtaDeployment(depId);
      notify?.(result.message || 'Đã huỷ chiến dịch an toàn', 'info');
      addLogEvent(`Đã huỷ chiến dịch OTA`, 'warn');
      fetchDeployments();
      fetchDeploymentDetails(depId);
    } catch (err) {
      notify?.(err.message || 'Lỗi huỷ chiến dịch', 'error');
    } finally {
      setCancellingDepId(null);
    }
  };

  // Retry Device Job
  const handleRetryJob = async (jobId, deviceId) => {
    setRetryingJobId(jobId);
    try {
      await api.retryOtaDeviceJob(jobId);
      notify?.(`Đã gửi lại lệnh OTA cho thiết bị #${deviceId}`, 'success');
      addLogEvent(`[#${deviceId}] Đã gửi lại yêu cầu nạp OTA`, 'info', deviceId);
      if (selectedDeploymentId) {
        fetchDeploymentDetails(selectedDeploymentId);
      }
    } catch (err) {
      notify?.(err.message || 'Lỗi thử lại OTA', 'error');
    } finally {
      setRetryingJobId(null);
    }
  };

  // Helper for ETA estimation
  const formatEta = (job) => {
    if (job.status === 'SUCCESS') return '00:00:00';
    if (job.status === 'FAILED' || job.status === 'ROLLBACK_SUCCESS') return '—';
    if (!job.started_at || !job.downloaded_bytes || !job.total_bytes) return '~00:00:18';
    
    const elapsed = Math.max(1, (Date.now() - new Date(job.started_at).getTime()) / 1000);
    const speed = job.downloaded_bytes / elapsed;
    if (speed <= 0) return '~00:00:20';
    
    const remainingSec = Math.max(1, Math.round((job.total_bytes - job.downloaded_bytes) / speed));
    const mins = Math.floor(remainingSec / 60);
    const secs = remainingSec % 60;
    return `~${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  };

  // Filtered Live Logs (Tab 4)
  const filteredLogs = useMemo(() => {
    return liveLogs.filter(l => {
      if (logFilterDevice !== 'ALL' && l.deviceId !== logFilterDevice) return false;
      if (logFilterType !== 'ALL' && l.type !== logFilterType) return false;
      return true;
    });
  }, [liveLogs, logFilterDevice, logFilterType]);

  // Overall Live Deployment Stats
  const liveJobs = deploymentDetails?.jobs || [];
  const totalTargetCount = deploymentDetails?.target_count || liveJobs.length || 0;
  const totalSuccessCount = liveJobs.filter(j => j.status === 'SUCCESS').length;
  const overallProgress = totalTargetCount > 0
    ? Math.round(liveJobs.reduce((acc, j) => acc + (j.status === 'SUCCESS' ? 100 : (j.progress_percent || 0)), 0) / totalTargetCount)
    : 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px', paddingBottom: '36px' }}>
      
      {/* 1. Header Card Banner */}
      <div style={{
        backgroundColor: '#ffffff',
        borderRadius: '16px',
        padding: '20px 24px',
        border: '1px solid #e2e8f0',
        boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '16px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
          <div style={{
            width: '46px',
            height: '46px',
            borderRadius: '12px',
            backgroundColor: '#ecfdf5',
            color: '#10b981',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0
          }}>
            <Cpu size={24} />
          </div>
          <div>
            <h1 style={{ margin: 0, fontSize: '18px', fontWeight: 800, color: '#111a4a' }}>
              Quản lý Firmware & Nạp OTA
            </h1>
            <p style={{ margin: '3px 0 0', fontSize: '12.5px', color: '#64748b' }}>
              Phát hành bản build ESP32, kiểm soát SHA-256 và triển khai nạp firmware từ xa an toàn cho toàn bộ thùng rác thông minh.
            </p>
          </div>
        </div>

        {/* Quick Action Buttons */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button
            onClick={() => {
              fetchReleases();
              fetchDeployments();
              if (selectedDeploymentId) fetchDeploymentDetails(selectedDeploymentId);
              notify?.('Đã làm mới dữ liệu firmware', 'info');
            }}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '8px 14px',
              borderRadius: '10px',
              backgroundColor: '#ffffff',
              border: '1px solid #e2e8f0',
              color: '#334155',
              fontSize: '12.5px',
              fontWeight: 600,
              cursor: 'pointer',
              boxShadow: '0 1px 3px rgba(0,0,0,0.02)'
            }}
          >
            <RefreshCw size={13} color="#64748b" />
            <span>Làm mới</span>
          </button>
          <button
            onClick={() => setActiveTab('deploy')}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              padding: '8px 16px',
              borderRadius: '10px',
              backgroundColor: '#10b981',
              border: 'none',
              color: '#ffffff',
              fontSize: '13px',
              fontWeight: 700,
              cursor: 'pointer',
              boxShadow: '0 2px 6px rgba(16, 185, 129, 0.25)',
              transition: 'background-color 150ms ease'
            }}
            onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#059669'; }}
            onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#10b981'; }}
          >
            <Rocket size={14} />
            <span>Tạo đợt nạp OTA mới</span>
          </button>
        </div>
      </div>

      {/* 2. Main Unified Content Card with Navigation Tabs */}
      <div style={{ backgroundColor: '#ffffff', borderRadius: '16px', border: '1px solid #e2e8f0', overflow: 'hidden', boxShadow: '0 1px 3px rgba(0,0,0,0.02)' }}>
        
        {/* Navigation Tabs Header */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: '1px solid #e2e8f0',
          backgroundColor: '#ffffff',
          padding: '0 20px',
          overflowX: 'auto',
          flexWrap: 'wrap'
        }}>
          <div style={{ display: 'flex', gap: '8px' }}>
            {[
              { id: 'releases', label: 'Bản phát hành Firmware', icon: FileCode, count: releases.length },
              { id: 'deploy', label: 'Chọn thiết bị Triển khai', icon: Rocket, count: selectedDeviceIds.size > 0 ? selectedDeviceIds.size : undefined },
              { id: 'processing', label: 'Tiến trình Nạp OTA', icon: Activity, count: totalTargetCount > 0 ? totalTargetCount : undefined },
              { id: 'logs', label: 'Nhật ký Cập nhật', icon: Terminal, count: liveLogs.length > 0 ? liveLogs.length : undefined }
            ].map(tab => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  style={{
                    padding: '14px 18px',
                    fontSize: '13px',
                    fontWeight: isActive ? 700 : 600,
                    border: 'none',
                    borderBottom: isActive ? '2.5px solid #10b981' : '2.5px solid transparent',
                    backgroundColor: 'transparent',
                    color: isActive ? '#10b981' : '#64748b',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    transition: 'all 150ms ease'
                  }}
                >
                  <Icon size={15} color={isActive ? '#10b981' : '#64748b'} />
                  <span>{tab.label}</span>
                  {tab.count !== undefined && (
                    <span style={{
                      fontSize: '11px',
                      padding: '2px 7px',
                      borderRadius: '10px',
                      backgroundColor: isActive ? '#ecfdf5' : '#f1f5f9',
                      color: isActive ? '#059669' : '#64748b',
                      fontWeight: 700
                    }}>
                      {tab.count}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>

        {/* Tab Panel Body */}
        <div style={{ padding: '24px' }}>

      {/* ========================================================= */}
      {/* TAB 1: BẢN PHÁT HÀNH FIRMWARE (TẢI LÊN TRÁI + DANH SÁCH PHẢI) */}
      {/* ========================================================= */}
      {activeTab === 'releases' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))', gap: '20px', alignItems: 'start' }}>
          
          {/* Cột 1 (Trái): Tải lên Firmware */}
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '24px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
            display: 'flex',
            flexDirection: 'column',
            gap: '18px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ width: '32px', height: '32px', borderRadius: '50%', backgroundColor: '#ecfdf5', color: '#10b981', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <UploadCloud size={18} />
              </div>
              <div>
                <h3 style={{ margin: 0, fontSize: '15.5px', fontWeight: 700, color: '#0f172a' }}>
                  Tải lên Firmware
                </h3>
                <span style={{ fontSize: '12px', color: '#64748b' }}>Chọn file firmware (.bin)</span>
              </div>
            </div>

            {/* Drag & Drop Zone */}
            <div
              onClick={() => fileInputRef.current?.click()}
              style={{
                border: selectedFile ? '2px solid #16a34a' : '2px dashed #cbd5e1',
                borderRadius: '12px',
                padding: '26px 16px',
                textAlign: 'center',
                backgroundColor: selectedFile ? '#f0fdf4' : '#fafafa',
                cursor: 'pointer',
                transition: 'all 150ms ease'
              }}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept=".bin"
                onChange={handleFileChange}
                style={{ display: 'none' }}
              />
              
              <UploadCloud size={32} style={{ color: selectedFile ? '#16a34a' : '#94a3b8', margin: '0 auto 8px' }} />
              
              <div style={{ fontSize: '13px', fontWeight: 600, color: '#334155' }}>
                {selectedFile ? 'Nhấp để thay đổi file .bin' : 'Kéo thả file vào đây hoặc'}
              </div>
              
              {!selectedFile && (
                <button
                  type="button"
                  style={{
                    marginTop: '10px',
                    padding: '7px 16px',
                    borderRadius: '6px',
                    backgroundColor: '#16a34a',
                    color: '#ffffff',
                    border: 'none',
                    fontSize: '12.5px',
                    fontWeight: 600,
                    cursor: 'pointer'
                  }}
                >
                  Chọn file .bin
                </button>
              )}
            </div>

            {/* Selected File Card */}
            {selectedFile && (
              <div style={{
                padding: '12px 16px',
                borderRadius: '10px',
                backgroundColor: '#ffffff',
                border: '1px solid #e2e8f0',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <FileCode size={24} color="#16a34a" />
                  <div>
                    <div style={{ fontSize: '13px', fontWeight: 700, color: '#0f172a' }}>{selectedFile.name}</div>
                    <div style={{ fontSize: '11.5px', color: '#64748b' }}>
                      {(selectedFile.size / (1024 * 1024)).toFixed(2)} MB
                    </div>
                  </div>
                </div>
                <div style={{ width: '22px', height: '22px', borderRadius: '50%', backgroundColor: '#16a34a', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Check size={14} />
                </div>
              </div>
            )}

            {uploadSuccess && (
              <div style={{
                padding: '10px 14px',
                borderRadius: '8px',
                backgroundColor: '#ecfdf5',
                border: '1px solid #a7f3d0',
                fontSize: '12px',
                color: '#065f46',
                display: 'flex',
                alignItems: 'center',
                gap: '8px'
              }}>
                <CheckCircle2 size={16} color="#059669" />
                <div>
                  <strong>Tải lên thành công!</strong> File firmware đã được tải lên và xác thực.
                </div>
              </div>
            )}

            {/* Form Inputs */}
            <form onSubmit={handleUploadRelease} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: '#334155', marginBottom: '4px' }}>
                  Số Phiên Bản (SemVer) *
                </label>
                <input
                  type="text"
                  placeholder="ví dụ: v1.2.0"
                  value={fileDetails.version}
                  onChange={(e) => setFileDetails(prev => ({ ...prev, version: e.target.value }))}
                  required
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    fontSize: '13px',
                    boxSizing: 'border-box'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: '#334155', marginBottom: '4px' }}>
                  Dòng Phần Cứng Tương Thích
                </label>
                <select
                  value={fileDetails.deviceModel}
                  onChange={(e) => setFileDetails(prev => ({ ...prev, deviceModel: e.target.value }))}
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    fontSize: '13px',
                    backgroundColor: '#ffffff',
                    boxSizing: 'border-box'
                  }}
                >
                  <option value="ESP32-CAM">ESP32-CAM</option>
                  <option value="ESP32-S3-SMARTBIN">ESP32-S3-SMARTBIN</option>
                  <option value="ESP32-DOIT-DEVKIT">ESP32-DOIT-DEVKIT</option>
                </select>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: '#334155', marginBottom: '4px' }}>
                  Ghi Chú Phát Hành / Changelog
                </label>
                <textarea
                  rows={3}
                  placeholder="- Tối ưu hiệu suất kết nối&#10;- Cải thiện thuật toán đo mức đầy&#10;- Sửa lỗi cảm biến"
                  value={fileDetails.releaseNotes}
                  onChange={(e) => setFileDetails(prev => ({ ...prev, releaseNotes: e.target.value }))}
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    fontSize: '12.5px',
                    boxSizing: 'border-box',
                    resize: 'vertical'
                  }}
                />
              </div>

              <button
                type="submit"
                disabled={uploading || !selectedFile}
                style={{
                  marginTop: '4px',
                  padding: '9px',
                  borderRadius: '8px',
                  backgroundColor: uploading || !selectedFile ? '#f1f5f9' : '#16a34a',
                  border: uploading || !selectedFile ? '1px solid #e2e8f0' : 'none',
                  color: uploading || !selectedFile ? '#94a3b8' : '#ffffff',
                  fontSize: '13px',
                  fontWeight: 700,
                  cursor: uploading || !selectedFile ? 'not-allowed' : 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '8px'
                }}
              >
                {uploading ? (
                  <>
                    <RefreshCw size={14} className="animate-spin" />
                    Đang xử lý & tải lên Supabase Storage...
                  </>
                ) : (
                  <>
                    <UploadCloud size={15} />
                    Đăng Bản Phát Hành Mới
                  </>
                )}
              </button>
            </form>
          </div>

          {/* Cột 2 (Phải): Danh Sách Bản Phát Hành Đã Xuất Bản */}
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '24px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div style={{ width: '32px', height: '32px', borderRadius: '50%', backgroundColor: '#ecfdf5', color: '#10b981', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <FileCode size={18} />
                </div>
                <h3 style={{ margin: 0, fontSize: '15.5px', fontWeight: 700, color: '#0f172a' }}>
                  Danh Sách Bản Phát Hành ({releases.length})
                </h3>
              </div>
            </div>

            {loadingReleases ? (
              <div style={{ padding: '40px', textAlign: 'center', color: '#94a3b8' }}>
                <RefreshCw size={22} className="animate-spin" style={{ margin: '0 auto 8px' }} />
                Đang tải danh sách bản phát hành...
              </div>
            ) : releases.length === 0 ? (
              <div style={{
                padding: '48px 20px',
                textAlign: 'center',
                backgroundColor: '#fafafa',
                borderRadius: '12px',
                border: '1px dashed #cbd5e1'
              }}>
                <FileCode size={36} style={{ color: '#94a3b8', margin: '0 auto 8px' }} />
                <div style={{ fontSize: '13.5px', fontWeight: 600, color: '#475569' }}>
                  Chưa có bản phát hành nào
                </div>
                <p style={{ fontSize: '12px', color: '#94a3b8', margin: '4px 0 0' }}>
                  Hãy tải lên file binary .bin ở khung bên trái để xuất bản phiên bản firmware đầu tiên.
                </p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', maxHeight: '540px', overflowY: 'auto' }}>
                {releases.map(rel => {
                  const isSelected = selectedRelease?.id === rel.id;
                  return (
                    <div
                      key={rel.id}
                      onClick={() => setModalRelease(rel)}
                      style={{
                        padding: '14px 16px',
                        borderRadius: '12px',
                        border: `1px solid ${isSelected ? '#16a34a' : '#e2e8f0'}`,
                        backgroundColor: isSelected ? '#f0fdf4' : '#f8fafc',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: '12px',
                        cursor: 'pointer',
                        transition: 'all 120ms ease'
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div style={{
                          width: '38px',
                          height: '38px',
                          borderRadius: '10px',
                          backgroundColor: '#ffffff',
                          border: '1px solid #cbd5e1',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: '#16a34a'
                        }}>
                          <FileCode size={20} />
                        </div>
                        <div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <span style={{ fontSize: '14px', fontWeight: 800, color: '#0f172a' }}>{rel.version}</span>
                            <span style={{ fontSize: '11px', padding: '1px 6px', borderRadius: '4px', backgroundColor: '#e2e8f0', color: '#475569', fontWeight: 600 }}>
                              {rel.device_model}
                            </span>
                            <span style={{ fontSize: '11px', padding: '1px 6px', borderRadius: '4px', backgroundColor: '#ecfdf5', color: '#059669', fontWeight: 600 }}>
                              Sẵn sàng
                            </span>
                          </div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginTop: '4px', fontSize: '11.5px', color: '#64748b' }}>
                            <span>{(rel.size_bytes / (1024 * 1024)).toFixed(2)} MB</span>
                            <span>•</span>
                            <span>{formatVietnamDateTimeClean(rel.created_at)}</span>
                          </div>
                        </div>
                      </div>

                      {/* Action Buttons */}
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setModalRelease(rel);
                          }}
                          style={{
                            padding: '6px 12px',
                            borderRadius: '6px',
                            backgroundColor: '#ffffff',
                            color: '#334155',
                            border: '1px solid #cbd5e1',
                            fontSize: '12px',
                            fontWeight: 600,
                            cursor: 'pointer'
                          }}
                        >
                          Chi tiết
                        </button>
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedRelease(rel);
                            setActiveTab('deploy');
                          }}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '5px',
                            padding: '6px 12px',
                            borderRadius: '6px',
                            backgroundColor: '#16a34a',
                            color: '#ffffff',
                            border: 'none',
                            fontSize: '12px',
                            fontWeight: 600,
                            cursor: 'pointer'
                          }}
                        >
                          <Rocket size={13} />
                          Nạp OTA
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

        </div>
      )}

      {/* ========================================================= */}
      {/* TAB 2: CHỌN THIẾT BỊ TRIỂN KHAI (CHUẨN 100% IMAGE 4)     */}
      {/* ========================================================= */}
      {activeTab === 'deploy' && (
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          padding: '24px',
          border: '1px solid #e2e8f0',
          boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
          display: 'flex',
          flexDirection: 'column',
          gap: '18px'
        }}>
          
          {/* Header Row */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ width: '32px', height: '32px', borderRadius: '50%', backgroundColor: '#ecfdf5', color: '#10b981', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Cpu size={18} />
              </div>
              <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 700, color: '#0f172a' }}>
                Chọn thiết bị triển khai
              </h3>
            </div>
            
            <div style={{
              padding: '6px 14px',
              borderRadius: '8px',
              backgroundColor: '#f1f5f9',
              color: '#1e293b',
              fontSize: '12.5px',
              fontWeight: 600
            }}>
              {selectedDeviceIds.size} thiết bị đã chọn
            </div>
          </div>

          {/* Search & Filter Bar */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{ position: 'relative', flex: 1 }}>
              <Search size={15} style={{ position: 'absolute', left: '12px', top: '12px', color: '#94a3b8' }} />
              <input
                type="text"
                placeholder="Tìm kiếm thiết bị..."
                value={searchQuery}
                onChange={(e) => { setSearchQuery(e.target.value); setPage(1); }}
                style={{
                  width: '100%',
                  padding: '9px 12px 9px 34px',
                  borderRadius: '8px',
                  border: '1px solid #cbd5e1',
                  fontSize: '13px',
                  boxSizing: 'border-box'
                }}
              />
            </div>

            <select
              value={filterMode}
              onChange={(e) => { setFilterMode(e.target.value); setPage(1); }}
              style={{
                padding: '9px 14px',
                borderRadius: '8px',
                border: '1px solid #cbd5e1',
                backgroundColor: '#ffffff',
                fontSize: '13px',
                color: '#334155',
                minWidth: '160px'
              }}
            >
              <option value="ONLINE">Chỉ thiết bị online</option>
              <option value="ALL">Tất cả thiết bị</option>
              <option value="OFFLINE">Chỉ thiết bị offline</option>
            </select>
          </div>

          {/* Table Devices */}
          <div style={{
            border: '1px solid #e2e8f0',
            borderRadius: '12px',
            overflow: 'hidden'
          }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13.5px' }}>
              <thead>
                <tr style={{ backgroundColor: '#ffffff', borderBottom: '1px solid #f1f5f9', color: '#475569', fontWeight: 600, fontSize: '12.5px' }}>
                  <th style={{ padding: '12px 16px', width: '36px' }}>
                    <input
                      type="checkbox"
                      checked={selectedDeviceIds.size === filteredBins.length && filteredBins.length > 0}
                      onChange={selectAllFiltered}
                      style={{ cursor: 'pointer', width: '15px', height: '15px', accentColor: '#16a34a' }}
                    />
                  </th>
                  <th style={{ padding: '12px 16px' }}>Thiết bị</th>
                  <th style={{ padding: '12px 16px' }}>Model</th>
                  <th style={{ padding: '12px 16px' }}>Phiên bản hiện tại</th>
                  <th style={{ padding: '12px 16px' }}>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {paginatedBins.length === 0 ? (
                  <tr>
                    <td colSpan={5} style={{ padding: '36px', textAlign: 'center', color: '#94a3b8' }}>
                      Không tìm thấy thiết bị nào phù hợp với bộ lọc.
                    </td>
                  </tr>
                ) : (
                  paginatedBins.map(bin => {
                    const isSelected = selectedDeviceIds.has(bin.device_id);

                    return (
                      <tr
                        key={bin.device_id}
                        onClick={() => toggleDeviceSelection(bin.device_id)}
                        style={{
                          borderBottom: '1px solid #f8fafc',
                          backgroundColor: isSelected ? '#ffffff' : '#ffffff',
                          cursor: 'pointer'
                        }}
                      >
                        <td style={{ padding: '12px 16px' }} onClick={e => e.stopPropagation()}>
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => toggleDeviceSelection(bin.device_id)}
                            style={{ cursor: 'pointer', width: '16px', height: '16px', accentColor: '#16a34a' }}
                          />
                        </td>
                        <td style={{ padding: '12px 16px', fontWeight: 600, color: '#0f172a' }}>
                          {bin.device_id}
                        </td>
                        <td style={{ padding: '12px 16px', color: '#334155' }}>
                          {bin.device_model || 'ESP32-CAM'}
                        </td>
                        <td style={{ padding: '12px 16px', color: '#334155' }}>
                          {bin.firmware_version || 'v1.1.0'}
                        </td>
                        <td style={{ padding: '12px 16px' }}>
                          {bin.is_online ? (
                            <span style={{
                              padding: '3px 10px',
                              borderRadius: '12px',
                              fontSize: '11.5px',
                              fontWeight: 600,
                              backgroundColor: '#ecfdf5',
                              color: '#059669'
                            }}>
                              Online
                            </span>
                          ) : (
                            <span style={{
                              padding: '3px 10px',
                              borderRadius: '12px',
                              fontSize: '11.5px',
                              fontWeight: 600,
                              backgroundColor: '#f1f5f9',
                              color: '#64748b'
                            }}>
                              Offline
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* Pagination Bar */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '12px',
            paddingTop: '6px'
          }}>
            <div style={{ fontSize: '13px', color: '#64748b' }}>
              Tổng {filteredBins.length} thiết bị
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                <button
                  type="button"
                  disabled={page <= 1}
                  onClick={() => setPage(p => Math.max(1, p - 1))}
                  style={{
                    width: '32px',
                    height: '32px',
                    borderRadius: '6px',
                    border: '1px solid #e2e8f0',
                    backgroundColor: '#f8fafc',
                    color: '#64748b',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: page <= 1 ? 'not-allowed' : 'pointer'
                  }}
                >
                  <ChevronLeft size={14} />
                </button>

                <button
                  type="button"
                  style={{
                    width: '32px',
                    height: '32px',
                    borderRadius: '6px',
                    border: '1.5px solid #16a34a',
                    backgroundColor: '#ffffff',
                    color: '#16a34a',
                    fontWeight: 700,
                    fontSize: '13px'
                  }}
                >
                  {page}
                </button>

                <button
                  type="button"
                  disabled={page >= totalPages}
                  onClick={() => setPage(p => Math.min(totalPages, p + 1))}
                  style={{
                    width: '32px',
                    height: '32px',
                    borderRadius: '6px',
                    border: '1px solid #e2e8f0',
                    backgroundColor: '#f8fafc',
                    color: '#64748b',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: page >= totalPages ? 'not-allowed' : 'pointer'
                  }}
                >
                  <ChevronRight size={14} />
                </button>
              </div>

              <select
                value={perPage}
                onChange={(e) => { setPerPage(Number(e.target.value)); setPage(1); }}
                style={{
                  padding: '6px 10px',
                  borderRadius: '6px',
                  border: '1px solid #cbd5e1',
                  backgroundColor: '#ffffff',
                  fontSize: '12.5px',
                  color: '#334155'
                }}
              >
                <option value={10}>10 / trang</option>
                <option value={25}>25 / trang</option>
                <option value={50}>50 / trang</option>
              </select>

              <button
                type="button"
                disabled={selectedDeviceIds.size === 0 || !selectedRelease}
                onClick={() => setConfirmModalOpen(true)}
                style={{
                  marginLeft: '8px',
                  padding: '7px 18px',
                  borderRadius: '8px',
                  backgroundColor: selectedDeviceIds.size === 0 || !selectedRelease ? '#f1f5f9' : '#16a34a',
                  border: selectedDeviceIds.size === 0 || !selectedRelease ? '1px solid #e2e8f0' : 'none',
                  color: selectedDeviceIds.size === 0 || !selectedRelease ? '#94a3b8' : '#ffffff',
                  fontSize: '13px',
                  fontWeight: 700,
                  cursor: selectedDeviceIds.size === 0 || !selectedRelease ? 'not-allowed' : 'pointer'
                }}
              >
                Bắt đầu triển khai OTA
              </button>
            </div>
          </div>

        </div>
      )}

      {/* ========================================================= */}
      {/* TAB 3: TIẾN TRÌNH NẠP OTA (CHUẨN 100% IMAGE 2 & 3)       */}
      {/* ========================================================= */}
      {activeTab === 'processing' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          
          {/* Main Card: Triển khai OTA (Chuẩn Image 2) */}
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '24px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
            display: 'flex',
            flexDirection: 'column',
            gap: '20px'
          }}>
            {/* Header */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ width: '32px', height: '32px', borderRadius: '50%', backgroundColor: '#ecfdf5', color: '#10b981', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Cpu size={18} />
              </div>
              <div>
                <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 700, color: '#0f172a' }}>
                  Triển khai OTA
                </h3>
                <span style={{ fontSize: '12.5px', color: '#64748b' }}>
                  {deploymentDetails
                    ? `Đang cập nhật firmware cho ${totalTargetCount} thiết bị`
                    : 'Đang cập nhật firmware cho các thiết bị đã chọn'}
                </span>
              </div>
            </div>

            {/* Device Progress List with Timeline Line */}
            {liveJobs.length === 0 ? (
              <div style={{
                padding: '48px 20px',
                textAlign: 'center',
                backgroundColor: '#fafafa',
                borderRadius: '12px',
                border: '1px dashed #cbd5e1'
              }}>
                <Activity size={36} style={{ margin: '0 auto 8px', color: '#94a3b8' }} />
                <div style={{ fontSize: '14px', fontWeight: 600, color: '#475569' }}>
                  Chưa có tiến trình OTA nào đang chạy
                </div>
                <p style={{ fontSize: '12px', color: '#94a3b8', margin: '4px 0 16px' }}>
                  Hãy chọn các thiết bị và bấm "Bắt đầu triển khai OTA" ở Tab 2.
                </p>
                <button
                  type="button"
                  onClick={() => setActiveTab('deploy')}
                  style={{
                    padding: '8px 16px',
                    borderRadius: '6px',
                    backgroundColor: '#16a34a',
                    color: '#ffffff',
                    border: 'none',
                    fontSize: '12.5px',
                    fontWeight: 600,
                    cursor: 'pointer'
                  }}
                >
                  Đi đến Chọn thiết bị
                </button>
              </div>
            ) : (
              <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: '16px' }}>
                
                {/* Vertical Timeline Guide Line */}
                <div style={{
                  position: 'absolute',
                  left: '18px',
                  top: '24px',
                  bottom: '24px',
                  width: '2px',
                  backgroundColor: '#e2e8f0',
                  zIndex: 1
                }} />

                {liveJobs.map((job, idx) => {
                  const isSuccess = job.status === 'SUCCESS';
                  const isDownloading = job.status === 'DOWNLOADING' || job.status === 'COMMAND_SENT';
                  const isVerifying = job.status === 'VERIFYING';
                  const isInstalling = job.status === 'INSTALLING' || job.status === 'REBOOTING';
                  const currentPercent = job.progress_percent || (isSuccess ? 100 : 0);
                  const downloadedMb = ((job.downloaded_bytes || 0) / (1024 * 1024)).toFixed(2);
                  const totalMb = ((job.total_bytes || 1) / (1024 * 1024)).toFixed(2);

                  let statusText = 'Đang tải firmware...';
                  if (isVerifying) statusText = 'Đang xác thực...';
                  if (isInstalling) statusText = 'Đang cài đặt firmware...';
                  if (isSuccess) statusText = 'Hoàn tất cập nhật';
                  if (job.status === 'FAILED') statusText = 'Cập nhật thất bại';

                  return (
                    <div
                      key={job.id}
                      style={{
                        position: 'relative',
                        zIndex: 2,
                        padding: '14px 18px',
                        borderRadius: '12px',
                        border: '1px solid #e2e8f0',
                        backgroundColor: '#ffffff',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: '16px',
                        flexWrap: 'wrap'
                      }}
                    >
                      {/* Left: Dot + Chip Icon + Device Info */}
                      <div style={{ display: 'flex', alignItems: 'center', gap: '14px', minWidth: '220px' }}>
                        {/* Timeline Step Dot */}
                        <div style={{
                          width: '12px',
                          height: '12px',
                          borderRadius: '50%',
                          backgroundColor: isDownloading ? '#2563eb' : '#ffffff',
                          border: `2px solid ${isDownloading ? '#2563eb' : (isSuccess ? '#10b981' : '#cbd5e1')}`,
                          flexShrink: 0
                        }} />

                        {/* Device Icon Box */}
                        <div style={{
                          width: '36px',
                          height: '36px',
                          borderRadius: '8px',
                          backgroundColor: '#f8fafc',
                          border: '1px solid #e2e8f0',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: '#334155'
                        }}>
                          <Cpu size={20} />
                        </div>

                        <div>
                          <div style={{ fontSize: '13.5px', fontWeight: 700, color: '#0f172a' }}>
                            {job.device_id}
                          </div>
                          <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>
                            {statusText}
                          </div>
                        </div>
                      </div>

                      {/* Middle: Blue Progress Bar & Byte Count */}
                      <div style={{ flex: 1, minWidth: '200px', maxWidth: '380px' }}>
                        <div style={{
                          width: '100%',
                          height: '6px',
                          borderRadius: '3px',
                          backgroundColor: '#f1f5f9',
                          overflow: 'hidden'
                        }}>
                          <div style={{
                            width: `${currentPercent}%`,
                            height: '100%',
                            borderRadius: '3px',
                            backgroundColor: '#2563eb',
                            transition: 'width 300ms ease'
                          }} />
                        </div>

                        <div style={{
                          fontSize: '11.5px',
                          color: '#64748b',
                          marginTop: '4px',
                          textAlign: 'right'
                        }}>
                          {isInstalling ? 'Cài đặt...' : `${downloadedMb} MB / ${totalMb} MB`}
                        </div>
                      </div>

                      {/* Right: % and Timing */}
                      <div style={{ display: 'flex', alignItems: 'center', gap: '24px' }}>
                        <div style={{ fontSize: '14px', fontWeight: 800, color: '#0f172a', minWidth: '40px' }}>
                          {currentPercent}%
                        </div>

                        <div style={{ fontSize: '12px', color: '#64748b', lineHeight: '1.5' }}>
                          <div>Bắt đầu: &nbsp;{formatVietnamTimeClean(job.started_at || job.created_at)}</div>
                          <div>Còn lại: &nbsp;&nbsp;{formatEta(job)}</div>
                        </div>
                      </div>

                    </div>
                  );
                })}

              </div>
            )}
          </div>

          {/* Bottom Card: Tiến độ tổng thể (Chuẩn 100% Image 3) */}
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '16px 24px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '16px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px', flex: 1, minWidth: '280px' }}>
              <span style={{ fontSize: '13px', fontWeight: 600, color: '#475569', whiteSpace: 'nowrap' }}>
                Tiến độ tổng thể
              </span>
              
              <div style={{
                flex: 1,
                height: '6px',
                borderRadius: '3px',
                backgroundColor: '#f1f5f9',
                overflow: 'hidden'
              }}>
                <div style={{
                  width: `${overallProgress}%`,
                  height: '100%',
                  borderRadius: '3px',
                  backgroundColor: '#2563eb',
                  transition: 'width 300ms ease'
                }} />
              </div>

              <span style={{ fontSize: '13.5px', fontWeight: 800, color: '#0f172a' }}>
                {overallProgress}%
              </span>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
              <span style={{ fontSize: '13px', color: '#64748b' }}>
                {totalSuccessCount} / {totalTargetCount} thiết bị hoàn thành
              </span>

              <button
                type="button"
                onClick={() => {
                  if (deploymentDetails) handleCancelDeployment(deploymentDetails.id);
                }}
                style={{
                  padding: '6px 16px',
                  borderRadius: '6px',
                  backgroundColor: '#ffffff',
                  border: '1px solid #f87171',
                  color: '#ef4444',
                  fontSize: '13px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Hủy triển khai
              </button>
            </div>
          </div>

        </div>
      )}

      {/* ========================================================= */}
      {/* TAB 4: NHẬT KÝ CẬP NHẬT (LIVE TIMELINE FEED)             */}
      {/* ========================================================= */}
      {activeTab === 'logs' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(380px, 1fr))', gap: '20px' }}>
          
          {/* Live Timeline Stream */}
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '24px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <h3 style={{ margin: 0, fontSize: '15.5px', fontWeight: 700, color: '#0f172a' }}>
                Nhật ký cập nhật
              </h3>
              <button
                type="button"
                onClick={() => setLiveLogs([])}
                style={{ background: 'none', border: 'none', color: '#94a3b8', fontSize: '12px', cursor: 'pointer' }}
              >
                Xóa màn hình
              </button>
            </div>

            {/* Timeline Stream */}
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '12px',
              maxHeight: '480px',
              overflowY: 'auto',
              paddingRight: '4px'
            }}>
              {filteredLogs.length === 0 ? (
                <div style={{ padding: '36px 12px', textAlign: 'center', color: '#94a3b8', fontSize: '12.5px' }}>
                  Chưa có nhật ký sự kiện nào được ghi nhận.
                </div>
              ) : (
                filteredLogs.map(log => {
                  const dotColor = log.type === 'success' ? '#16a34a' : (log.type === 'error' ? '#dc2626' : (log.type === 'warn' ? '#d97706' : '#16a34a'));
                  return (
                    <div
                      key={log.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '12px',
                        fontSize: '13px'
                      }}
                    >
                      <span style={{
                        width: '6px',
                        height: '6px',
                        borderRadius: '50%',
                        backgroundColor: dotColor,
                        flexShrink: 0
                      }} />
                      <span style={{ color: '#64748b', fontSize: '12px', fontFamily: 'monospace', flexShrink: 0 }}>
                        {formatVietnamTimeClean(log.timestamp)}
                      </span>
                      <span style={{ color: '#1e293b' }}>
                        {log.text}
                      </span>
                    </div>
                  );
                })
              )}
            </div>

            <div style={{ borderTop: '1px solid #f1f5f9', paddingTop: '12px', display: 'flex', justifyContent: 'flex-end' }}>
              <button
                type="button"
                onClick={() => {
                  const content = liveLogs.map(l => `[${formatVietnamTimeClean(l.timestamp)}] ${l.text}`).join('\n');
                  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
                  const url = URL.createObjectURL(blob);
                  const a = document.createElement('a');
                  a.href = url;
                  a.download = `ota_logs_${new Date().toISOString().slice(0, 10)}.txt`;
                  a.click();
                  URL.revokeObjectURL(url);
                }}
                style={{
                  padding: '7px 16px',
                  borderRadius: '6px',
                  backgroundColor: '#ffffff',
                  border: '1px solid #cbd5e1',
                  color: '#334155',
                  fontSize: '12.5px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Xem tất cả nhật ký
              </button>
            </div>
          </div>

          {/* Past Deployments History */}
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '24px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px'
          }}>
            <h3 style={{ margin: 0, fontSize: '15.5px', fontWeight: 700, color: '#0f172a' }}>
              Lịch Sử Các Đợt Nạp ({deployments.length})
            </h3>

            {loadingDeployments ? (
              <div style={{ padding: '36px', textAlign: 'center', color: '#94a3b8' }}>
                <RefreshCw size={20} className="animate-spin" style={{ margin: '0 auto 8px' }} />
                Đang tải lịch sử...
              </div>
            ) : deployments.length === 0 ? (
              <div style={{ padding: '36px', textAlign: 'center', color: '#94a3b8', fontSize: '13px' }}>
                Chưa có đợt triển khai OTA nào trong lịch sử.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', maxHeight: '480px', overflowY: 'auto' }}>
                {deployments.map(dep => (
                  <div
                    key={dep.id}
                    onClick={() => {
                      setSelectedDeploymentId(dep.id);
                      setActiveTab('processing');
                    }}
                    style={{
                      padding: '12px 14px',
                      borderRadius: '10px',
                      border: '1px solid #e2e8f0',
                      backgroundColor: '#f8fafc',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between'
                    }}
                  >
                    <div>
                      <div style={{ fontSize: '13.5px', fontWeight: 800, color: '#0f172a' }}>
                        {dep.release?.version || 'Bản OTA'}
                      </div>
                      <div style={{ fontSize: '11.5px', color: '#64748b', marginTop: '2px' }}>
                        {dep.success_count || 0}/{dep.target_count} hoàn thành • {formatVietnamDateTimeClean(dep.created_at)}
                      </div>
                    </div>

                    <button
                      type="button"
                      style={{
                        padding: '4px 10px',
                        borderRadius: '6px',
                        backgroundColor: '#ffffff',
                        border: '1px solid #cbd5e1',
                        fontSize: '11.5px',
                        fontWeight: 600,
                        color: '#2563eb',
                        cursor: 'pointer'
                      }}
                    >
                      Chi tiết &rarr;
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

        </div>
      )}

      </div>
    </div>

      {/* ========================================================= */}
      {/* 5. POPUP MODAL: THÔNG TIN CHI TIẾT FIRMWARE (CHUẨN ẢNH 1) */}
      {/* ========================================================= */}
      {modalRelease && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          width: '100vw',
          height: '100vh',
          backgroundColor: 'rgba(15, 23, 42, 0.6)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '26px',
            width: '100%',
            maxWidth: '520px',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            display: 'flex',
            flexDirection: 'column',
            gap: '18px'
          }}>
            {/* Modal Header */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div style={{ width: '32px', height: '32px', borderRadius: '50%', backgroundColor: '#ecfdf5', color: '#10b981', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Cpu size={18} />
                </div>
                <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 700, color: '#0f172a' }}>
                  Thông tin Firmware
                </h3>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{
                  padding: '3px 10px',
                  borderRadius: '6px',
                  fontSize: '12px',
                  fontWeight: 700,
                  backgroundColor: '#ecfdf5',
                  color: '#059669'
                }}>
                  Sẵn sàng
                </span>
                <button
                  type="button"
                  onClick={() => setModalRelease(null)}
                  style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#64748b', padding: '4px' }}
                >
                  <X size={18} />
                </button>
              </div>
            </div>

            {/* Clean Data Rows (Chuẩn 100% Mockup Ảnh 1) */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', fontSize: '13.5px' }}>
              
              {/* Row 1: Phiên bản */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: '#64748b' }}>
                  <Clock size={16} />
                  <span>Phiên bản</span>
                </div>
                <div style={{ fontWeight: 600, color: '#0f172a' }}>
                  {modalRelease.version}
                </div>
              </div>

              {/* Row 2: Tên file */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: '#64748b' }}>
                  <FileText size={16} />
                  <span>Tên file</span>
                </div>
                <div style={{ fontWeight: 500, color: '#0f172a', fontFamily: 'monospace' }}>
                  {modalRelease.file_name || `smart-waste-${modalRelease.version}.bin`}
                </div>
              </div>

              {/* Row 3: Kích thước */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: '#64748b' }}>
                  <HardDrive size={16} />
                  <span>Kích thước</span>
                </div>
                <div style={{ fontWeight: 600, color: '#0f172a' }}>
                  {(modalRelease.size_bytes / (1024 * 1024)).toFixed(2)} MB
                </div>
              </div>

              {/* Row 4: Kiểu thiết bị */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: '#64748b' }}>
                  <Cpu size={16} />
                  <span>Kiểu thiết bị</span>
                </div>
                <div style={{ fontWeight: 600, color: '#0f172a' }}>
                  {modalRelease.device_model}
                </div>
              </div>

              {/* Row 5: SHA-256 */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: '#64748b' }}>
                  <ShieldCheck size={16} />
                  <span>SHA-256</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <span style={{ fontSize: '12px', color: '#334155', fontFamily: 'monospace' }}>
                    {modalRelease.sha256 ? `${modalRelease.sha256.substring(0, 12)}...${modalRelease.sha256.substring(52)}` : '—'}
                  </span>
                  <button
                    type="button"
                    onClick={() => handleCopySha(modalRelease.sha256, modalRelease.id)}
                    style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#64748b', padding: '2px' }}
                    title="Sao chép toàn bộ SHA-256"
                  >
                    {copiedSha === modalRelease.id ? <Check size={13} color="#16a34a" /> : <Copy size={13} />}
                  </button>
                </div>
              </div>

              {/* Row 6: Ngày tạo */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: '#64748b' }}>
                  <Calendar size={16} />
                  <span>Ngày tạo</span>
                </div>
                <div style={{ fontSize: '13px', color: '#334155' }}>
                  {formatVietnamDateTimeClean(modalRelease.created_at)}
                </div>
              </div>

              {/* Row 7: Ghi chú */}
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', color: '#64748b', flexShrink: 0 }}>
                  <Edit3 size={16} />
                  <span>Ghi chú</span>
                </div>
                <div style={{ textAlign: 'right', fontSize: '12.5px', color: '#334155', lineHeight: '1.6', whiteSpace: 'pre-line' }}>
                  {modalRelease.release_notes || '- Tối ưu hiệu suất kết nối\n- Cải thiện thuật toán đo mức đầy\n- Sửa lỗi cảm biến'}
                </div>
              </div>

            </div>

            {/* Modal Actions */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '10px', marginTop: '6px' }}>
              <button
                type="button"
                onClick={() => setModalRelease(null)}
                style={{
                  padding: '8px 16px',
                  borderRadius: '8px',
                  backgroundColor: '#f1f5f9',
                  border: '1px solid #cbd5e1',
                  color: '#475569',
                  fontSize: '12.5px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Đóng
              </button>
              <button
                type="button"
                onClick={() => {
                  setSelectedRelease(modalRelease);
                  setModalRelease(null);
                  setActiveTab('deploy');
                }}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '8px 18px',
                  borderRadius: '8px',
                  backgroundColor: '#16a34a',
                  border: 'none',
                  color: '#ffffff',
                  fontSize: '12.5px',
                  fontWeight: 700,
                  cursor: 'pointer',
                  boxShadow: '0 2px 4px rgba(22,163,74,0.2)'
                }}
              >
                <Rocket size={14} />
                Nạp OTA Bản Này
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================= */}
      {/* 6. CONFIRMATION REVIEW MODAL                              */}
      {/* ========================================================= */}
      {confirmModalOpen && selectedRelease && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          width: '100vw',
          height: '100vh',
          backgroundColor: 'rgba(15, 23, 42, 0.6)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '26px',
            width: '100%',
            maxWidth: '500px',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            display: 'flex',
            flexDirection: 'column',
            gap: '18px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div style={{ padding: '9px', borderRadius: '10px', backgroundColor: '#f0fdf4', color: '#16a34a' }}>
                <ShieldCheck size={26} />
              </div>
              <div>
                <h3 style={{ margin: 0, fontSize: '17px', fontWeight: 800, color: '#0f172a' }}>
                  Xác Nhận Nạp OTA Từ Xa
                </h3>
                <span style={{ fontSize: '12.5px', color: '#64748b' }}>Kiểm duyệt an toàn phần cứng trước khi phát lệnh</span>
              </div>
            </div>

            {/* Campaign Summary Box */}
            <div style={{
              padding: '14px',
              borderRadius: '10px',
              backgroundColor: '#f8fafc',
              border: '1px solid #e2e8f0',
              display: 'flex',
              flexDirection: 'column',
              gap: '8px',
              fontSize: '12.5px'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Phiên bản mục tiêu:</span>
                <strong style={{ color: '#16a34a' }}>{selectedRelease.version}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Dòng phần cứng:</span>
                <strong>{selectedRelease.device_model}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Số lượng thiết bị nhận lệnh:</span>
                <strong style={{ color: '#0f172a' }}>{selectedDeviceIds.size} thùng rác</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Mã băm SHA-256:</span>
                <code style={{ fontSize: '11px', color: '#334155' }}>
                  {selectedRelease.sha256 ? `${selectedRelease.sha256.substring(0, 16)}...` : '—'}
                </code>
              </div>
            </div>

            {/* Zero-Brick Safety Banner */}
            <div style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: '8px',
              padding: '10px 12px',
              borderRadius: '8px',
              backgroundColor: '#ecfdf5',
              border: '1px solid #a7f3d0',
              fontSize: '12px',
              color: '#065f46'
            }}>
              <ShieldCheck size={16} style={{ flexShrink: 0, marginTop: '2px' }} />
              <div>
                <strong>Bảo Vệ Phần Cứng Chuẩn Zero-Brick:</strong> Firmware sẽ được nạp vào phân vùng phụ (app1). Nếu sau khi khởi động lại, thiết bị gặp lỗi bootloader sẽ tự động Rollback về phân vùng cũ an toàn.
              </div>
            </div>

            {/* Modal Actions */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '10px' }}>
              <button
                type="button"
                onClick={() => setConfirmModalOpen(false)}
                disabled={deploying}
                style={{
                  padding: '8px 16px',
                  borderRadius: '8px',
                  backgroundColor: '#f1f5f9',
                  border: '1px solid #cbd5e1',
                  color: '#475569',
                  fontSize: '12.5px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Huỷ bỏ
              </button>
              <button
                type="button"
                onClick={handleStartDeployment}
                disabled={deploying}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '8px 20px',
                  borderRadius: '8px',
                  backgroundColor: deploying ? '#f1f5f9' : '#16a34a',
                  border: deploying ? '1px solid #e2e8f0' : 'none',
                  color: deploying ? '#94a3b8' : '#ffffff',
                  fontSize: '12.5px',
                  fontWeight: 700,
                  cursor: deploying ? 'not-allowed' : 'pointer'
                }}
              >
                {deploying ? (
                  <>
                    <RefreshCw size={14} className="animate-spin" />
                    Đang phát lệnh...
                  </>
                ) : (
                  <>
                    <Rocket size={14} />
                    Xác Nhận & Nạp OTA
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
