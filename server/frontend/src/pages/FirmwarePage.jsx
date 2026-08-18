import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { 
  Cpu, 
  UploadCloud, 
  Rocket, 
  History, 
  CheckCircle2, 
  AlertTriangle, 
  XCircle, 
  RotateCcw, 
  ShieldCheck, 
  FileCode, 
  Copy, 
  Check, 
  RefreshCw, 
  Server, 
  Activity, 
  Info,
  Clock,
  ArrowRight,
  Filter,
  CheckSquare,
  Square,
  Sparkles,
  Zap,
  AlertCircle
} from 'lucide-react';
import { api } from '../services/api';
import { getSocket } from '../services/socket';

export default function FirmwarePage({ notify, bins = [], onOpenMap }) {
  const [activeTab, setActiveTab] = useState('releases'); // 'releases' | 'deploy' | 'monitor'
  
  // Releases State
  const [releases, setReleases] = useState([]);
  const [loadingReleases, setLoadingReleases] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const [fileDetails, setFileDetails] = useState({
    version: '',
    deviceModel: 'ESP32-S3-SMARTBIN',
    releaseNotes: ''
  });
  const [copiedSha, setCopiedSha] = useState(null);

  // Deployments State
  const [deployments, setDeployments] = useState([]);
  const [loadingDeployments, setLoadingDeployments] = useState(false);
  const [selectedReleaseForDeploy, setSelectedReleaseForDeploy] = useState(null);
  const [selectedDeviceIds, setSelectedDeviceIds] = useState(new Set());
  const [filterOnlineOnly, setFilterOnlineOnly] = useState(true);
  const [filterOutdatedOnly, setFilterOutdatedOnly] = useState(false);
  const [confirmModalOpen, setConfirmModalOpen] = useState(false);
  const [deploying, setDeploying] = useState(false);

  // Live Selected Deployment for Monitoring
  const [selectedDeploymentId, setSelectedDeploymentId] = useState(null);
  const [deploymentDetails, setDeploymentDetails] = useState(null);
  const [retryingJobId, setRetryingJobId] = useState(null);
  const [cancellingDepId, setCancellingDepId] = useState(null);

  // 1. Fetch Releases
  const fetchReleases = useCallback(async () => {
    setLoadingReleases(true);
    try {
      const data = await api.getFirmwareReleases();
      if (Array.isArray(data)) {
        setReleases(data);
        if (!selectedReleaseForDeploy && data.length > 0) {
          setSelectedReleaseForDeploy(data[0]);
        }
      }
    } catch (err) {
      notify(err.message || 'Không tải được danh sách firmware releases', 'error');
    } finally {
      setLoadingReleases(false);
    }
  }, [notify, selectedReleaseForDeploy]);

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
      notify(err.message || 'Không tải được lịch sử triển khai OTA', 'error');
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

  // 4. Realtime Socket.IO Listeners for Live OTA Progress
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

      // Update deployments list progress summary
      setDeployments(prev => prev.map(d => {
        if (d.id === payload.deploymentId) {
          return {
            ...d,
            status: payload.status === 'SUCCESS' ? d.status : d.status
          };
        }
        return d;
      }));
    };

    const handleDepCreated = (payload) => {
      if (payload && payload.deployment) {
        setDeployments(prev => [payload.deployment, ...prev]);
        notify(`Chiến dịch OTA cho bản ${payload.deployment.release?.version || ''} đã được kích hoạt!`, 'success');
      }
    };

    socket.on('otaJobUpdated', handleJobUpdated);
    socket.on('otaDeploymentCreated', handleDepCreated);

    return () => {
      socket.off('otaJobUpdated', handleJobUpdated);
      socket.off('otaDeploymentCreated', handleDepCreated);
    };
  }, [notify]);

  // Handle Copy SHA-256
  const handleCopySha = (sha, id) => {
    navigator.clipboard.writeText(sha);
    setCopiedSha(id);
    setTimeout(() => setCopiedSha(null), 2000);
  };

  // Handle File Selection
  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;

    if (!file.name.endsWith('.bin')) {
      notify('Vui lòng chỉ chọn file định dạng binary compiled (.bin)', 'error');
      return;
    }

    if (file.size > 3.5 * 1024 * 1024) {
      notify('Kích thước file vượt quá giới hạn 3.5 MB của phân vùng OTA 4MB Flash', 'error');
      return;
    }

    setSelectedFile(file);

    // Auto guess version from filename if matches pattern (e.g. smartwaste_v1.2.0.bin)
    const match = file.name.match(/v?(\d+\.\d+\.\d+(?:-[a-zA-Z0-9.]+)?)/);
    if (match && !fileDetails.version) {
      setFileDetails(prev => ({ ...prev, version: `v${match[1]}` }));
    }
  };

  // Handle Upload Release
  const handleUploadRelease = async (e) => {
    e.preventDefault();
    if (!selectedFile) {
      notify('Vui lòng chọn file firmware .bin', 'error');
      return;
    }

    if (!fileDetails.version || !fileDetails.version.match(/^v?\d+\.\d+\.\d+/)) {
      notify('Vui lòng nhập phiên bản SemVer hợp lệ (ví dụ: v1.1.0)', 'error');
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
      notify(result.message || 'Tạo bản phát hành firmware thành công!', 'success');
      setSelectedFile(null);
      setFileDetails({ version: '', deviceModel: 'ESP32-S3-SMARTBIN', releaseNotes: '' });
      fetchReleases();
      setActiveTab('releases');
    } catch (err) {
      notify(err.message || 'Lỗi tải lên firmware', 'error');
    } finally {
      setUploading(false);
    }
  };

  // Filter Target Devices for OTA Deploy
  const compatibleBins = useMemo(() => {
    return (bins || []).filter(b => {
      const model = b.device_model || 'ESP32-S3-SMARTBIN';
      const targetModel = selectedReleaseForDeploy?.device_model || 'ESP32-S3-SMARTBIN';
      if (model !== targetModel) return false;

      if (filterOnlineOnly && !b.is_online) return false;
      if (filterOutdatedOnly && selectedReleaseForDeploy && b.firmware_version === selectedReleaseForDeploy.version) return false;

      return true;
    });
  }, [bins, selectedReleaseForDeploy, filterOnlineOnly, filterOutdatedOnly]);

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
    if (selectedDeviceIds.size === compatibleBins.length) {
      setSelectedDeviceIds(new Set());
    } else {
      setSelectedDeviceIds(new Set(compatibleBins.map(b => b.device_id)));
    }
  };

  // Launch Deployment
  const handleStartDeployment = async () => {
    if (!selectedReleaseForDeploy) {
      notify('Vui lòng chọn bản release để triển khai', 'error');
      return;
    }

    if (selectedDeviceIds.size === 0) {
      notify('Vui lòng chọn ít nhất 1 thiết bị mục tiêu', 'error');
      return;
    }

    setDeploying(true);
    try {
      const result = await api.createOtaDeployment(selectedReleaseForDeploy.id, Array.from(selectedDeviceIds));
      notify(result.message || 'Đã phát động chiến dịch OTA thành công!', 'success');
      setConfirmModalOpen(false);
      setSelectedDeviceIds(new Set());
      fetchDeployments();
      if (result.deployment) {
        setSelectedDeploymentId(result.deployment.id);
        setActiveTab('monitor');
      }
    } catch (err) {
      notify(err.message || 'Lỗi kích hoạt triển khai OTA', 'error');
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
      notify(result.message || 'Đã huỷ chiến dịch', 'info');
      fetchDeployments();
      fetchDeploymentDetails(depId);
    } catch (err) {
      notify(err.message || 'Lỗi huỷ chiến dịch', 'error');
    } finally {
      setCancellingDepId(null);
    }
  };

  // Retry Device Job
  const handleRetryJob = async (jobId) => {
    setRetryingJobId(jobId);
    try {
      const result = await api.retryOtaDeviceJob(jobId);
      notify('Đã gửi lại lệnh OTA cho thiết bị', 'success');
      if (selectedDeploymentId) {
        fetchDeploymentDetails(selectedDeploymentId);
      }
    } catch (err) {
      notify(err.message || 'Lỗi thử lại OTA', 'error');
    } finally {
      setRetryingJobId(null);
    }
  };

  // Status Badge Helper
  const renderStatusBadge = (status) => {
    const styles = {
      READY: { bg: '#ecfdf5', color: '#059669', border: '#a7f3d0', label: 'Sẵn sàng nạp' },
      DRAFT: { bg: '#f1f5f9', color: '#475569', border: '#cbd5e1', label: 'Bản nháp' },
      RUNNING: { bg: '#eff6ff', color: '#2563eb', border: '#bfdbfe', label: 'Đang triển khai' },
      COMPLETED: { bg: '#ecfdf5', color: '#059669', border: '#a7f3d0', label: 'Hoàn tất' },
      PARTIAL_FAILED: { bg: '#fff7ed', color: '#c2410c', border: '#ffedd5', label: 'Có lỗi một phần' },
      CANCELLED: { bg: '#f8fafc', color: '#64748b', border: '#e2e8f0', label: 'Đã huỷ' },
      COMMAND_SENT: { bg: '#fefce8', color: '#a16207', border: '#fef08a', label: 'Đã gửi lệnh' },
      DOWNLOADING: { bg: '#eff6ff', color: '#1d4ed8', border: '#bfdbfe', label: 'Đang tải (Stream)' },
      VERIFYING: { bg: '#f5f3ff', color: '#6d28d9', border: '#ddd6fe', label: 'Kiểm tra Checksum' },
      INSTALLING: { bg: '#fff7ed', color: '#ea580c', border: '#fed7aa', label: 'Đang ghi Flash' },
      REBOOTING: { bg: '#fefce8', color: '#b45309', border: '#fef08a', label: 'Đang khởi động lại' },
      BOOT_VERIFYING: { bg: '#f0fdf4', color: '#15803d', border: '#bbf7d0', label: 'Tự kiểm tra khởi động' },
      SUCCESS: { bg: '#ecfdf5', color: '#059669', border: '#a7f3d0', label: 'Cập nhật thành công' },
      FAILED: { bg: '#fef2f2', color: '#dc2626', border: '#fecaca', label: 'Thất bại' },
      ROLLBACK_SUCCESS: { bg: '#eff6ff', color: '#4338ca', border: '#c7d2fe', label: 'Đã Rollback an toàn' },
      TIMED_OUT: { bg: '#fef2f2', color: '#991b1b', border: '#fecaca', label: 'Hết thời gian (Timeout)' }
    };

    const conf = styles[status] || { bg: '#f1f5f9', color: '#64748b', border: '#e2e8f0', label: status };
    return (
      <span style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        padding: '3px 8px',
        borderRadius: '6px',
        fontSize: '11px',
        fontWeight: 600,
        backgroundColor: conf.bg,
        color: conf.color,
        border: `1px solid ${conf.border}`
      }}>
        {status === 'SUCCESS' && <CheckCircle2 size={12} />}
        {status === 'DOWNLOADING' && <RefreshCw size={12} className="animate-spin" />}
        {status === 'FAILED' && <XCircle size={12} />}
        {status === 'ROLLBACK_SUCCESS' && <ShieldCheck size={12} />}
        {conf.label}
      </span>
    );
  };

  return (
    <div style={{ maxWidth: '1440px', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '24px' }}>
      
      {/* 1. Header & Summary Stats */}
      <div style={{
        backgroundColor: '#ffffff',
        borderRadius: '16px',
        padding: '24px 28px',
        border: '1px solid #e2e8f0',
        boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '16px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{
            width: '48px',
            height: '48px',
            borderRadius: '12px',
            backgroundColor: '#eff6ff',
            color: '#2563eb',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            border: '1px solid #bfdbfe'
          }}>
            <Cpu size={26} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <h1 style={{ margin: 0, fontSize: '20px', fontWeight: 800, color: '#0f172a' }}>
                Quản lý Firmware & Nạp OTA Không Dây
              </h1>
              <span style={{
                padding: '2px 8px',
                borderRadius: '12px',
                fontSize: '11px',
                fontWeight: 700,
                backgroundColor: '#ecfdf5',
                color: '#059669',
                border: '1px solid #a7f3d0'
              }}>
                Dual-Partition Rollback Ready
              </span>
            </div>
            <p style={{ margin: '4px 0 0', fontSize: '13px', color: '#64748b' }}>
              Phát hành bản build ESP32-S3, kiểm soát chữ ký số & triển khai nạp firmware từ xa an toàn cho toàn bộ hệ thống Smart Bins.
            </p>
          </div>
        </div>

        {/* Action Button & Quick Refresh */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button
            onClick={() => { fetchReleases(); fetchDeployments(); if (selectedDeploymentId) fetchDeploymentDetails(selectedDeploymentId); }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '8px 14px',
              borderRadius: '8px',
              backgroundColor: '#f8fafc',
              border: '1px solid #cbd5e1',
              color: '#475569',
              fontSize: '13px',
              fontWeight: 600,
              cursor: 'pointer'
            }}
          >
            <RefreshCw size={14} />
            Làm mới
          </button>
          <button
            onClick={() => setActiveTab('deploy')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '8px 16px',
              borderRadius: '8px',
              backgroundColor: '#2563eb',
              border: 'none',
              color: '#ffffff',
              fontSize: '13px',
              fontWeight: 600,
              cursor: 'pointer',
              boxShadow: '0 2px 4px rgba(37,99,235,0.2)'
            }}
          >
            <Rocket size={15} />
            Tạo đợt nạp OTA mới
          </button>
        </div>
      </div>

      {/* 2. Navigation Tabs */}
      <div style={{
        display: 'flex',
        gap: '8px',
        borderBottom: '1px solid #e2e8f0',
        paddingBottom: '2px'
      }}>
        {[
          { id: 'releases', label: 'Bản phát hành Firmware (Releases)', icon: FileCode, count: releases.length },
          { id: 'deploy', label: 'Triển khai OTA (Deploy Campaign)', icon: Rocket },
          { id: 'monitor', label: 'Giám sát Tiến trình & Lịch sử', icon: Activity, count: deployments.length }
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
                borderRadius: '8px 8px 0 0',
                border: 'none',
                borderBottom: isActive ? '2px solid #2563eb' : '2px solid transparent',
                backgroundColor: isActive ? '#ffffff' : 'transparent',
                color: isActive ? '#2563eb' : '#64748b',
                fontSize: '14px',
                fontWeight: isActive ? 700 : 500,
                cursor: 'pointer',
                transition: 'all 150ms ease'
              }}
            >
              <Icon size={16} />
              {tab.label}
              {tab.count !== undefined && (
                <span style={{
                  padding: '1px 6px',
                  borderRadius: '10px',
                  fontSize: '11px',
                  fontWeight: 700,
                  backgroundColor: isActive ? '#eff6ff' : '#f1f5f9',
                  color: isActive ? '#2563eb' : '#64748b'
                }}>
                  {tab.count}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* 3. TAB 1: RELEASES & UPLOAD */}
      {activeTab === 'releases' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(320px, 380px) 1fr', gap: '24px' }}>
          
          {/* Upload Form Card */}
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '24px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <div style={{ padding: '8px', borderRadius: '8px', backgroundColor: '#eff6ff', color: '#2563eb' }}>
                <UploadCloud size={20} />
              </div>
              <div>
                <h3 style={{ margin: 0, fontSize: '15px', fontWeight: 700, color: '#0f172a' }}>
                  Phát hành Firmware Mới
                </h3>
                <span style={{ fontSize: '12px', color: '#64748b' }}>Hỗ trợ file compiled .bin (ESP32-S3)</span>
              </div>
            </div>

            <form onSubmit={handleUploadRelease} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {/* Drag & Drop File Zone */}
              <div style={{
                border: selectedFile ? '2px solid #2563eb' : '2px dashed #cbd5e1',
                borderRadius: '12px',
                padding: '20px',
                textAlign: 'center',
                backgroundColor: selectedFile ? '#eff6ff' : '#f8fafc',
                cursor: 'pointer',
                transition: 'all 150ms ease'
              }}
              onClick={() => document.getElementById('firmware-file-input').click()}
              >
                <input
                  id="firmware-file-input"
                  type="file"
                  accept=".bin"
                  onChange={handleFileChange}
                  style={{ display: 'none' }}
                />
                <UploadCloud size={32} style={{ color: selectedFile ? '#2563eb' : '#94a3b8', margin: '0 auto 8px' }} />
                {selectedFile ? (
                  <div>
                    <div style={{ fontSize: '13px', fontWeight: 700, color: '#1e293b' }}>{selectedFile.name}</div>
                    <div style={{ fontSize: '12px', color: '#2563eb', marginTop: '2px' }}>
                      {(selectedFile.size / (1024 * 1024)).toFixed(2)} MB • Sẵn sàng tải lên
                    </div>
                  </div>
                ) : (
                  <div>
                    <div style={{ fontSize: '13px', fontWeight: 600, color: '#334155' }}>
                      Nhấp để chọn file <code style={{ backgroundColor: '#e2e8f0', padding: '1px 4px', borderRadius: '4px' }}>.bin</code>
                    </div>
                    <div style={{ fontSize: '11px', color: '#94a3b8', marginTop: '4px' }}>
                      Giới hạn tối đa 3.5 MB (Chuẩn 4MB Flash)
                    </div>
                  </div>
                )}
              </div>

              {/* Version Input */}
              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: '#334155', marginBottom: '4px' }}>
                  Số Phiên Bản (SemVer) *
                </label>
                <input
                  type="text"
                  placeholder="ví dụ: v1.1.0"
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

              {/* Device Model Selector */}
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
                  <option value="ESP32-S3-SMARTBIN">ESP32-S3-SMARTBIN (Khuyên dùng)</option>
                  <option value="ESP32-DOIT-DEVKIT">ESP32-DOIT-DEVKIT (Legacy)</option>
                </select>
              </div>

              {/* Release Notes */}
              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, color: '#334155', marginBottom: '4px' }}>
                  Ghi Chú Phát Hành / Changelog
                </label>
                <textarea
                  rows={3}
                  placeholder="Mô tả các tính năng mới hoặc bản vá lỗi..."
                  value={fileDetails.releaseNotes}
                  onChange={(e) => setFileDetails(prev => ({ ...prev, releaseNotes: e.target.value }))}
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    fontSize: '13px',
                    boxSizing: 'border-box',
                    resize: 'vertical'
                  }}
                />
              </div>

              {/* Submit Button */}
              <button
                type="submit"
                disabled={uploading || !selectedFile}
                style={{
                  padding: '10px',
                  borderRadius: '8px',
                  backgroundColor: uploading || !selectedFile ? '#94a3b8' : '#2563eb',
                  border: 'none',
                  color: '#ffffff',
                  fontSize: '13px',
                  fontWeight: 700,
                  cursor: uploading || !selectedFile ? 'not-allowed' : 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '8px',
                  transition: 'background-color 150ms'
                }}
              >
                {uploading ? (
                  <>
                    <RefreshCw size={15} className="animate-spin" />
                    Đang phân tích & tải lên Supabase Storage...
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

          {/* Releases List Card */}
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '24px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 700, color: '#0f172a' }}>
                Danh Sách Bản Phát Hành Đã Xuất Bản ({releases.length})
              </h3>
            </div>

            {loadingReleases ? (
              <div style={{ padding: '40px', textAlign: 'center', color: '#94a3b8' }}>
                <RefreshCw size={24} className="animate-spin" style={{ margin: '0 auto 8px' }} />
                Đang tải danh sách bản phát hành...
              </div>
            ) : releases.length === 0 ? (
              <div style={{
                padding: '40px 20px',
                textAlign: 'center',
                backgroundColor: '#f8fafc',
                borderRadius: '12px',
                border: '1px dashed #cbd5e1'
              }}>
                <FileCode size={36} style={{ color: '#94a3b8', margin: '0 auto 8px' }} />
                <div style={{ fontSize: '14px', fontWeight: 600, color: '#475569' }}>Chưa có bản phát hành nào</div>
                <p style={{ fontSize: '12px', color: '#94a3b8', margin: '4px 0 0' }}>
                  Hãy tải lên file firmware đầu tiên ở khung bên trái để bắt đầu quản lý OTA.
                </p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {releases.map(rel => (
                  <div
                    key={rel.id}
                    style={{
                      padding: '16px 20px',
                      borderRadius: '12px',
                      border: '1px solid #e2e8f0',
                      backgroundColor: '#f8fafc',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: '16px',
                      flexWrap: 'wrap'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                      <div style={{
                        width: '40px',
                        height: '40px',
                        borderRadius: '10px',
                        backgroundColor: '#ffffff',
                        border: '1px solid #cbd5e1',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: '#2563eb'
                      }}>
                        <FileCode size={22} />
                      </div>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <span style={{ fontSize: '15px', fontWeight: 800, color: '#0f172a' }}>{rel.version}</span>
                          <span style={{ fontSize: '12px', padding: '1px 6px', borderRadius: '4px', backgroundColor: '#e2e8f0', color: '#475569', fontWeight: 600 }}>
                            {rel.device_model}
                          </span>
                          {renderStatusBadge(rel.status)}
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginTop: '4px', fontSize: '12px', color: '#64748b' }}>
                          <span>{(rel.size_bytes / (1024 * 1024)).toFixed(2)} MB</span>
                          <span>•</span>
                          <span>{new Date(rel.created_at).toLocaleDateString('vi-VN')} {new Date(rel.created_at).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}</span>
                        </div>
                        {rel.release_notes && (
                          <div style={{ fontSize: '12px', color: '#334155', marginTop: '6px', fontStyle: 'italic' }}>
                            "{rel.release_notes}"
                          </div>
                        )}
                      </div>
                    </div>

                    {/* SHA256 & Action Button */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '6px',
                        padding: '4px 8px',
                        borderRadius: '6px',
                        backgroundColor: '#ffffff',
                        border: '1px solid #cbd5e1',
                        fontSize: '11px',
                        fontFamily: 'monospace',
                        color: '#475569'
                      }}>
                        <span>SHA: {rel.sha256.substring(0, 8)}...{rel.sha256.substring(56)}</span>
                        <button
                          onClick={() => handleCopySha(rel.sha256, rel.id)}
                          style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#64748b', padding: '2px' }}
                          title="Sao chép toàn bộ SHA-256"
                        >
                          {copiedSha === rel.id ? <Check size={12} color="#059669" /> : <Copy size={12} />}
                        </button>
                      </div>

                      <button
                        onClick={() => {
                          setSelectedReleaseForDeploy(rel);
                          setActiveTab('deploy');
                        }}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '6px',
                          padding: '6px 12px',
                          borderRadius: '6px',
                          backgroundColor: '#2563eb',
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
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* 4. TAB 2: DEPLOY OTA CAMPAIGN */}
      {activeTab === 'deploy' && (
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          padding: '24px',
          border: '1px solid #e2e8f0',
          boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
          display: 'flex',
          flexDirection: 'column',
          gap: '20px'
        }}>
          {/* Step 1: Selected Release Header */}
          <div style={{
            padding: '16px 20px',
            borderRadius: '12px',
            backgroundColor: '#eff6ff',
            border: '1px solid #bfdbfe',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '12px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <Sparkles size={22} color="#2563eb" />
              <div>
                <span style={{ fontSize: '12px', fontWeight: 700, color: '#2563eb', textTransform: 'uppercase' }}>
                  Bước 1: Bản Firmware Mục Tiêu
                </span>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '2px' }}>
                  <span style={{ fontSize: '16px', fontWeight: 800, color: '#0f172a' }}>
                    {selectedReleaseForDeploy ? `${selectedReleaseForDeploy.version} (${selectedReleaseForDeploy.device_model})` : 'Chưa chọn bản phát hành'}
                  </span>
                  {selectedReleaseForDeploy && renderStatusBadge(selectedReleaseForDeploy.status)}
                </div>
              </div>
            </div>

            {/* Release Dropdown Selector */}
            <select
              value={selectedReleaseForDeploy?.id || ''}
              onChange={(e) => {
                const found = releases.find(r => r.id === e.target.value);
                if (found) setSelectedReleaseForDeploy(found);
              }}
              style={{
                padding: '8px 12px',
                borderRadius: '8px',
                border: '1px solid #93c5fd',
                backgroundColor: '#ffffff',
                fontSize: '13px',
                fontWeight: 600,
                color: '#1e40af'
              }}
            >
              {releases.map(r => (
                <option key={r.id} value={r.id}>
                  {r.version} - {r.device_model} ({(r.size_bytes / (1024 * 1024)).toFixed(2)} MB)
                </option>
              ))}
            </select>
          </div>

          {/* Step 2: Device Selection Header & Filters */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
            <div>
              <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 700, color: '#0f172a' }}>
                Bước 2: Chọn Thiết Bị Nhận Cập Nhật ({selectedDeviceIds.size} / {compatibleBins.length} đã chọn)
              </h3>
              <span style={{ fontSize: '12px', color: '#64748b' }}>
                Chỉ những thiết bị tương thích phần cứng và đạt điều kiện an toàn mới được hiển thị.
              </span>
            </div>

            {/* Filter Pills */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <button
                onClick={selectAllFiltered}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '6px 12px',
                  borderRadius: '6px',
                  backgroundColor: '#f1f5f9',
                  border: '1px solid #cbd5e1',
                  fontSize: '12px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                {selectedDeviceIds.size === compatibleBins.length && compatibleBins.length > 0 ? <CheckSquare size={14} color="#2563eb" /> : <Square size={14} />}
                {selectedDeviceIds.size === compatibleBins.length && compatibleBins.length > 0 ? 'Bỏ chọn tất cả' : 'Chọn tất cả'}
              </button>

              <button
                onClick={() => setFilterOnlineOnly(prev => !prev)}
                style={{
                  padding: '6px 12px',
                  borderRadius: '6px',
                  backgroundColor: filterOnlineOnly ? '#ecfdf5' : '#f8fafc',
                  border: `1px solid ${filterOnlineOnly ? '#a7f3d0' : '#e2e8f0'}`,
                  color: filterOnlineOnly ? '#059669' : '#64748b',
                  fontSize: '12px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Chỉ thiết bị Online
              </button>

              <button
                onClick={() => setFilterOutdatedOnly(prev => !prev)}
                style={{
                  padding: '6px 12px',
                  borderRadius: '6px',
                  backgroundColor: filterOutdatedOnly ? '#fff7ed' : '#f8fafc',
                  border: `1px solid ${filterOutdatedOnly ? '#fed7aa' : '#e2e8f0'}`,
                  color: filterOutdatedOnly ? '#ea580c' : '#64748b',
                  fontSize: '12px',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Chỉ phiên bản cũ (&lt; {selectedReleaseForDeploy?.version || ''})
              </button>
            </div>
          </div>

          {/* Devices Table */}
          <div style={{
            border: '1px solid #e2e8f0',
            borderRadius: '12px',
            overflow: 'hidden'
          }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
              <thead>
                <tr style={{ backgroundColor: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569', fontWeight: 600 }}>
                  <th style={{ padding: '12px 16px', width: '40px' }}>
                    <input
                      type="checkbox"
                      checked={selectedDeviceIds.size === compatibleBins.length && compatibleBins.length > 0}
                      onChange={selectAllFiltered}
                      style={{ cursor: 'pointer' }}
                    />
                  </th>
                  <th style={{ padding: '12px 16px' }}>Mã Thiết Bị / Tên Thùng</th>
                  <th style={{ padding: '12px 16px' }}>Địa Điểm</th>
                  <th style={{ padding: '12px 16px' }}>Phiên Bản Hiện Tại</th>
                  <th style={{ padding: '12px 16px' }}>Mục Tiêu Sau OTA</th>
                  <th style={{ padding: '12px 16px' }}>Trạng Thái Kết Nối</th>
                </tr>
              </thead>
              <tbody>
                {compatibleBins.length === 0 ? (
                  <tr>
                    <td colSpan={6} style={{ padding: '32px', textAlign: 'center', color: '#94a3b8' }}>
                      Không tìm thấy thiết bị nào phù hợp với bộ lọc hiện tại.
                    </td>
                  </tr>
                ) : (
                  compatibleBins.map(bin => {
                    const isSelected = selectedDeviceIds.has(bin.device_id);
                    const isAlreadyUpdated = bin.firmware_version === selectedReleaseForDeploy?.version;

                    return (
                      <tr
                        key={bin.device_id}
                        onClick={() => toggleDeviceSelection(bin.device_id)}
                        style={{
                          borderBottom: '1px solid #f1f5f9',
                          backgroundColor: isSelected ? '#eff6ff' : '#ffffff',
                          cursor: 'pointer',
                          transition: 'background-color 100ms'
                        }}
                      >
                        <td style={{ padding: '12px 16px' }} onClick={e => e.stopPropagation()}>
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => toggleDeviceSelection(bin.device_id)}
                            style={{ cursor: 'pointer' }}
                          />
                        </td>
                        <td style={{ padding: '12px 16px', fontWeight: 700, color: '#0f172a' }}>
                          #{bin.device_id}
                          {bin.name && <span style={{ fontWeight: 400, color: '#64748b', marginLeft: '6px' }}>({bin.name})</span>}
                        </td>
                        <td style={{ padding: '12px 16px', color: '#475569' }}>
                          {bin.location || 'Chưa định vị'}
                        </td>
                        <td style={{ padding: '12px 16px' }}>
                          <code style={{ padding: '2px 6px', borderRadius: '4px', backgroundColor: '#f1f5f9', color: '#334155', fontWeight: 600 }}>
                            {bin.firmware_version || 'v1.0.0'}
                          </code>
                        </td>
                        <td style={{ padding: '12px 16px' }}>
                          <span style={{
                            fontWeight: 700,
                            color: isAlreadyUpdated ? '#059669' : '#2563eb',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px'
                          }}>
                            {isAlreadyUpdated ? <CheckCircle2 size={14} /> : <ArrowRight size={14} />}
                            {selectedReleaseForDeploy?.version}
                          </span>
                        </td>
                        <td style={{ padding: '12px 16px' }}>
                          {bin.is_online ? (
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', color: '#059669', fontWeight: 600, fontSize: '12px' }}>
                              <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#10b981' }} />
                              Online
                            </span>
                          ) : (
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', color: '#94a3b8', fontWeight: 500, fontSize: '12px' }}>
                              <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#cbd5e1' }} />
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

          {/* Action Deploy Bar */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            paddingTop: '12px',
            borderTop: '1px solid #e2e8f0'
          }}>
            <div style={{ fontSize: '13px', color: '#475569' }}>
              Đã chọn <strong style={{ color: '#0f172a' }}>{selectedDeviceIds.size}</strong> thiết bị để nâng cấp lên bản <strong style={{ color: '#2563eb' }}>{selectedReleaseForDeploy?.version}</strong>
            </div>

            <button
              disabled={selectedDeviceIds.size === 0 || !selectedReleaseForDeploy}
              onClick={() => setConfirmModalOpen(true)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '10px 24px',
                borderRadius: '8px',
                backgroundColor: selectedDeviceIds.size === 0 || !selectedReleaseForDeploy ? '#94a3b8' : '#2563eb',
                color: '#ffffff',
                border: 'none',
                fontSize: '14px',
                fontWeight: 700,
                cursor: selectedDeviceIds.size === 0 || !selectedReleaseForDeploy ? 'not-allowed' : 'pointer',
                boxShadow: '0 2px 6px rgba(37,99,235,0.25)'
              }}
            >
              <Rocket size={16} />
              Tiếp Tục & Xác Nhận Triển Khai
            </button>
          </div>
        </div>
      )}

      {/* 5. TAB 3: LIVE MONITOR & HISTORY */}
      {activeTab === 'monitor' && (
        <div style={{ display: 'grid', gridTemplateColumns: '340px 1fr', gap: '24px' }}>
          
          {/* Deployments List Sidebar */}
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '20px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
            maxHeight: '750px',
            overflowY: 'auto'
          }}>
            <h3 style={{ margin: '0 0 4px', fontSize: '15px', fontWeight: 700, color: '#0f172a' }}>
              Lịch Sử Đợt Nạp ({deployments.length})
            </h3>

            {deployments.length === 0 ? (
              <div style={{ padding: '24px', textAlign: 'center', color: '#94a3b8', fontSize: '13px' }}>
                Chưa có đợt triển khai OTA nào.
              </div>
            ) : (
              deployments.map(dep => {
                const isSelected = selectedDeploymentId === dep.id;
                return (
                  <div
                    key={dep.id}
                    onClick={() => setSelectedDeploymentId(dep.id)}
                    style={{
                      padding: '14px',
                      borderRadius: '10px',
                      border: `1px solid ${isSelected ? '#2563eb' : '#e2e8f0'}`,
                      backgroundColor: isSelected ? '#eff6ff' : '#f8fafc',
                      cursor: 'pointer',
                      transition: 'all 150ms ease'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: '14px', fontWeight: 800, color: '#0f172a' }}>
                        {dep.release?.version || 'OTA Campaign'}
                      </span>
                      {renderStatusBadge(dep.status)}
                    </div>
                    
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '6px', fontSize: '12px', color: '#64748b' }}>
                      <span>Mục tiêu: {dep.target_count} thiết bị</span>
                      <span>Thành công: {dep.success_count}/{dep.target_count}</span>
                    </div>

                    <div style={{ fontSize: '11px', color: '#94a3b8', marginTop: '4px' }}>
                      {new Date(dep.created_at).toLocaleDateString('vi-VN')} {new Date(dep.created_at).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                    </div>
                  </div>
                );
              })
            )}
          </div>

          {/* Deployment Detail & Live Device Jobs Monitor */}
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '24px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
            display: 'flex',
            flexDirection: 'column',
            gap: '20px'
          }}>
            {!deploymentDetails ? (
              <div style={{ padding: '60px 20px', textAlign: 'center', color: '#94a3b8' }}>
                <Activity size={32} style={{ margin: '0 auto 8px', color: '#cbd5e1' }} />
                Vui lòng chọn một đợt triển khai từ danh sách bên trái để theo dõi tiến độ.
              </div>
            ) : (
              <>
                {/* Deployment Overview Card */}
                <div style={{
                  padding: '16px 20px',
                  borderRadius: '12px',
                  backgroundColor: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  flexWrap: 'wrap',
                  gap: '16px'
                }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <h3 style={{ margin: 0, fontSize: '17px', fontWeight: 800, color: '#0f172a' }}>
                        Chiến dịch nâng cấp lên {deploymentDetails.release?.version}
                      </h3>
                      {renderStatusBadge(deploymentDetails.status)}
                    </div>
                    <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
                      Bắt đầu: {new Date(deploymentDetails.started_at || deploymentDetails.created_at).toLocaleString('vi-VN')}
                      {deploymentDetails.completed_at && ` • Hoàn thành: ${new Date(deploymentDetails.completed_at).toLocaleString('vi-VN')}`}
                    </div>
                  </div>

                  {deploymentDetails.status === 'RUNNING' && (
                    <button
                      onClick={() => handleCancelDeployment(deploymentDetails.id)}
                      disabled={cancellingDepId === deploymentDetails.id}
                      style={{
                        padding: '6px 14px',
                        borderRadius: '6px',
                        backgroundColor: '#fef2f2',
                        color: '#dc2626',
                        border: '1px solid #fecaca',
                        fontSize: '12px',
                        fontWeight: 700,
                        cursor: 'pointer'
                      }}
                    >
                      {cancellingDepId === deploymentDetails.id ? 'Đang huỷ...' : 'Huỷ chiến dịch (An toàn)'}
                    </button>
                  )}
                </div>

                {/* Device Jobs Progress List */}
                <div>
                  <h4 style={{ margin: '0 0 12px', fontSize: '14px', fontWeight: 700, color: '#334155' }}>
                    Tiến Trình Từng Thiết Bị ({deploymentDetails.jobs?.length || 0})
                  </h4>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    {(deploymentDetails.jobs || []).map(job => {
                      const isSuccess = job.status === 'SUCCESS';
                      const isFailed = job.status === 'FAILED' || job.status === 'ROLLBACK_FAILED' || job.status === 'TIMED_OUT';
                      const isUpdating = job.status === 'DOWNLOADING' || job.status === 'INSTALLING' || job.status === 'VERIFYING' || job.status === 'REBOOTING' || job.status === 'COMMAND_SENT';

                      return (
                        <div
                          key={job.id}
                          style={{
                            padding: '16px 20px',
                            borderRadius: '12px',
                            border: `1px solid ${isSuccess ? '#a7f3d0' : (isFailed ? '#fecaca' : '#e2e8f0')}`,
                            backgroundColor: isSuccess ? '#f0fdf4' : (isFailed ? '#fff5f5' : '#ffffff'),
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '10px'
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                              <span style={{ fontSize: '14px', fontWeight: 800, color: '#0f172a' }}>
                                #{job.device_id}
                              </span>
                              <span style={{ fontSize: '12px', color: '#64748b' }}>
                                ({job.previous_version || 'v1.0.0'} &rarr; <strong>{job.target_version}</strong>)
                              </span>
                              {renderStatusBadge(job.status)}
                            </div>

                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                              {job.boot_id_after && (
                                <span style={{ fontSize: '11px', color: '#059669', fontFamily: 'monospace' }}>
                                  Boot ID: {job.boot_id_after.substring(0, 10)}...
                                </span>
                              )}

                              {isFailed && (
                                <button
                                  onClick={() => handleRetryJob(job.id)}
                                  disabled={retryingJobId === job.id}
                                  style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '4px',
                                    padding: '4px 10px',
                                    borderRadius: '6px',
                                    backgroundColor: '#2563eb',
                                    color: '#ffffff',
                                    border: 'none',
                                    fontSize: '11px',
                                    fontWeight: 600,
                                    cursor: 'pointer'
                                  }}
                                >
                                  <RotateCcw size={12} className={retryingJobId === job.id ? 'animate-spin' : ''} />
                                  Thử lại OTA
                                </button>
                              )}
                            </div>
                          </div>

                          {/* Animated Progress Bar */}
                          <div>
                            <div style={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              fontSize: '11px',
                              fontWeight: 600,
                              color: '#64748b',
                              marginBottom: '4px'
                            }}>
                              <span>{job.status}</span>
                              <span>{job.progress_percent || 0}% ({((job.downloaded_bytes || 0) / (1024 * 1024)).toFixed(2)} / {((job.total_bytes || 1) / (1024 * 1024)).toFixed(2)} MB)</span>
                            </div>
                            
                            <div style={{
                              width: '100%',
                              height: '8px',
                              borderRadius: '4px',
                              backgroundColor: '#e2e8f0',
                              overflow: 'hidden'
                            }}>
                              <div style={{
                                width: `${job.progress_percent || (isSuccess ? 100 : 0)}%`,
                                height: '100%',
                                borderRadius: '4px',
                                backgroundColor: isSuccess ? '#10b981' : (isFailed ? '#ef4444' : '#2563eb'),
                                transition: 'width 300ms cubic-bezier(0.4, 0, 0.2, 1)'
                              }} />
                            </div>
                          </div>

                          {/* Error Code Message if any */}
                          {job.error_message && (
                            <div style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: '6px',
                              fontSize: '12px',
                              color: '#dc2626',
                              backgroundColor: '#fef2f2',
                              padding: '6px 10px',
                              borderRadius: '6px'
                            }}>
                              <AlertCircle size={14} />
                              <span>{job.error_code ? `[${job.error_code}] ` : ''}{job.error_message}</span>
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* 6. CONFIRMATION REVIEW MODAL */}
      {confirmModalOpen && selectedReleaseForDeploy && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          width: '100vw',
          height: '100vh',
          backgroundColor: 'rgba(15, 23, 42, 0.65)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '28px',
            width: '100%',
            maxWidth: '520px',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
            display: 'flex',
            flexDirection: 'column',
            gap: '20px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
              <div style={{ padding: '10px', borderRadius: '12px', backgroundColor: '#eff6ff', color: '#2563eb' }}>
                <ShieldCheck size={28} />
              </div>
              <div>
                <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 800, color: '#0f172a' }}>
                  Xác Nhận Nạp OTA Từ Xa
                </h3>
                <span style={{ fontSize: '13px', color: '#64748b' }}>Kiểm duyệt an toàn phần cứng trước khi phát lệnh</span>
              </div>
            </div>

            {/* Campaign Summary */}
            <div style={{
              padding: '16px',
              borderRadius: '12px',
              backgroundColor: '#f8fafc',
              border: '1px solid #e2e8f0',
              display: 'flex',
              flexDirection: 'column',
              gap: '10px',
              fontSize: '13px'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Phiên bản mục tiêu:</span>
                <strong style={{ color: '#2563eb' }}>{selectedReleaseForDeploy.version}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Dòng phần cứng:</span>
                <strong>{selectedReleaseForDeploy.device_model}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Số lượng thiết bị nhận lệnh:</span>
                <strong style={{ color: '#0f172a' }}>{selectedDeviceIds.size} thùng rác</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Mã băm toàn vẹn:</span>
                <code style={{ fontSize: '11px', color: '#334155' }}>{selectedReleaseForDeploy.sha256.substring(0, 16)}...</code>
              </div>
            </div>

            {/* Safety Notice */}
            <div style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: '10px',
              padding: '12px 14px',
              borderRadius: '8px',
              backgroundColor: '#ecfdf5',
              border: '1px solid #a7f3d0',
              fontSize: '12px',
              color: '#065f46'
            }}>
              <ShieldCheck size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
              <div>
                <strong>Bảo Vệ Phần Cứng Chuẩn Zero-Brick:</strong> Firmware sẽ được nạp vào phân vùng phụ (app1). Nếu sau khi khởi động lại, thiết bị gặp lỗi bootloader sẽ tự động Rollback về phân vùng cũ an toàn.
              </div>
            </div>

            {/* Modal Actions */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '10px', marginTop: '4px' }}>
              <button
                type="button"
                onClick={() => setConfirmModalOpen(false)}
                disabled={deploying}
                style={{
                  padding: '10px 18px',
                  borderRadius: '8px',
                  backgroundColor: '#f1f5f9',
                  border: '1px solid #cbd5e1',
                  color: '#475569',
                  fontSize: '13px',
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
                  gap: '8px',
                  padding: '10px 22px',
                  borderRadius: '8px',
                  backgroundColor: deploying ? '#94a3b8' : '#2563eb',
                  border: 'none',
                  color: '#ffffff',
                  fontSize: '13px',
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
