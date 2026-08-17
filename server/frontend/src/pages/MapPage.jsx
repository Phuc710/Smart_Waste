import React, { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import L from 'leaflet';
import {
  MapPin,
  Search,
  Navigation,
  Edit3,
  Trash2,
  Activity,
  Radio,
  CheckCircle2,
  AlertTriangle,
  X,
  Compass,
  RotateCcw,
  Sliders,
  ChevronRight,
  Eye,
  Truck,
  Layers,
  Copy,
  Check,
  Send,
  PlusCircle,
  AlertOctagon,
  Sparkles,
  RefreshCw,
  Clock,
  User,
  Shield,
  FileText,
  ArrowLeft,
  ArrowRight,
  Calendar,
  Cpu,
  Power,
  Loader2
} from 'lucide-react';
import { api } from '../services/api';
import { getSocket } from '../services/socket';
import { 
  formatVietnamTime, 
  formatVietnamDateTime, 
  getVietnamRelativeTime,
  VIETNAM_TIMEZONE,
  VIETNAM_LOCALE
} from '../utils/dateTime';

// Helper tính góc la bàn (bearing) giữa 2 tọa độ
function calculateBearing(lat1, lng1, lat2, lng2) {
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const lat1Rad = lat1 * Math.PI / 180;
  const lat2Rad = lat2 * Math.PI / 180;
  const y = Math.sin(dLng) * Math.cos(lat2Rad);
  const x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLng);
  const brng = Math.atan2(y, x) * 180 / Math.PI;
  return (brng + 360) % 360;
}

// Bảng Việt hóa trạng thái nhiệm vụ (Collection Job Statuses)
const JOB_STATUS_LABEL = {
  PENDING: 'Chờ nhận việc',
  ASSIGNED: 'Chờ tài xế xác nhận',
  ACCEPTED: 'Đã tiếp nhận',
  IN_PROGRESS: 'Đang thu gom',
  PAUSED: 'Tạm dừng',
  COMPLETED: 'Hoàn tất',
  CANCELLED: 'Đã hủy',
  REJECTED: 'Từ chối',
  EXPIRED: 'Hết hạn nhận việc'
};

const getJobStatusLabel = (status) => JOB_STATUS_LABEL[status] || status || 'Chờ xử lý';

const getJobStatusStyle = (status) => {
  switch (status) {
    case 'PENDING': return { bg: '#fef3c7', color: '#f59e0b', border: '#fde68a' };
    case 'ASSIGNED': return { bg: '#f5f3ff', color: '#7c3aed', border: '#ddd6fe' };
    case 'ACCEPTED': return { bg: '#eff6ff', color: '#2563eb', border: '#bfdbfe' };
    case 'IN_PROGRESS': return { bg: '#eff6ff', color: '#2563eb', border: '#bfdbfe' };
    case 'PAUSED': return { bg: '#fff7ed', color: '#f97316', border: '#fed7aa' };
    case 'COMPLETED': return { bg: '#ecfdf5', color: '#16a34a', border: '#bbf7d0' };
    case 'CANCELLED': return { bg: '#f1f5f9', color: '#64748b', border: '#e2e8f0' };
    case 'REJECTED': return { bg: '#fef2f2', color: '#dc2626', border: '#fecaca' };
    case 'EXPIRED': return { bg: '#f8fafc', color: '#475569', border: '#cbd5e1' };
    default: return { bg: '#f1f5f9', color: '#64748b', border: '#e2e8f0' };
  }
};

// Helper tạo mã xe ổn định dựa trên mã nhân viên / tên tài xế (Rút gọn 4 hex nếu là UUID)
const getStableTruckId = (emp, index = 0) => {
  if (!emp) return `XE-${index + 1}`;
  if (emp.vehicle_code) return emp.vehicle_code;
  const rawId = String(emp.employee_id || emp.username || emp.id || '');
  if (rawId.includes('-') && rawId.length > 10) {
    const cleanHex = rawId.replace(/[^a-zA-Z0-9]/g, '').slice(-4).toUpperCase();
    return `XE-${cleanHex}`;
  }
  if (rawId.length > 0) {
    const clean = rawId.replace(/[^a-zA-Z0-9]/g, '').slice(-4).toUpperCase();
    return `XE-${clean || (index + 1)}`;
  }
  return `XE-0${index + 1}`;
};

// Helper tính completed_bin_ids an toàn từ job object
const getCompletedBinIds = (job) => {
  if (!job) return [];
  if (Array.isArray(job.completed_bin_ids)) return job.completed_bin_ids;
  if (Array.isArray(job.items)) {
    return job.items
      .filter(i => ['COLLECTED', 'SKIPPED', 'INCIDENT'].includes(i.status))
      .map(i => i.bin_id);
  }
  return [];
};

// Helper tính Trạng thái Chuẩn hóa của Xe Thu Gom
const getTruckStatusInfo = (currentJob) => {
  if (!currentJob) {
    return {
      text: 'Đang chờ việc',
      badgeText: 'Đang chờ việc',
      color: '#059669',
      bg: '#ecfdf5',
      border: '#a7f3d0',
      markerBg: '#059669',
      icon: '🟢'
    };
  }
  switch (currentJob.status) {
    case 'PENDING':
      return {
        text: 'Chờ nhận việc',
        badgeText: 'Chờ nhận việc',
        color: '#f59e0b',
        bg: '#fef3c7',
        border: '#fde68a',
        markerBg: '#f59e0b',
        icon: '🟡'
      };
    case 'ASSIGNED':
      return {
        text: 'Chờ xác nhận',
        badgeText: 'Chờ tài xế xác nhận',
        color: '#7c3aed',
        bg: '#f5f3ff',
        border: '#ddd6fe',
        markerBg: '#7c3aed',
        icon: '🟣'
      };
    case 'ACCEPTED':
      return {
        text: 'Đã tiếp nhận',
        badgeText: 'Đã nhận – Chuẩn bị đi',
        color: '#2563eb',
        bg: '#eff6ff',
        border: '#bfdbfe',
        markerBg: '#2563eb',
        icon: '🔵'
      };
    case 'IN_PROGRESS':
      return {
        text: 'Đang thu gom',
        badgeText: 'Đang thu gom',
        color: '#059669',
        bg: '#ecfdf5',
        border: '#a7f3d0',
        markerBg: '#059669',
        icon: '🟢'
      };
    case 'PAUSED':
      return {
        text: 'Tạm dừng',
        badgeText: 'Tuyến tạm dừng',
        color: '#f97316',
        bg: '#fff7ed',
        border: '#fed7aa',
        markerBg: '#f97316',
        icon: '🟠'
      };
    default:
      return {
        text: 'Đang chờ việc',
        badgeText: 'Đang chờ việc',
        color: '#059669',
        bg: '#ecfdf5',
        border: '#a7f3d0',
        markerBg: '#059669',
        icon: '🟢'
      };
  }
};

// Helper tính Trạng thái Thu Gom Chuẩn hóa của Thùng Rác
const getBinCollectionStatusInfo = (bin, activeJobs) => {
  const activeJob = (activeJobs || []).find(j =>
    (j.target_bin_ids || []).includes(bin?.device_id) &&
    ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'PAUSED'].includes(j.status)
  );

  if (!activeJob) {
    return {
      text: 'Chờ thu gom',
      color: '#64748b',
      bg: '#f1f5f9',
      border: '#e2e8f0'
    };
  }

  const completedIds = getCompletedBinIds(activeJob);
  const isCompleted = completedIds.includes(bin?.device_id);
  if (isCompleted) {
    return {
      text: 'Đã thu gom',
      color: '#16a34a',
      bg: '#ecfdf5',
      border: '#bbf7d0'
    };
  }

  switch (activeJob.status) {
    case 'ASSIGNED':
      return {
        text: 'Chờ xác nhận',
        color: '#7c3aed',
        bg: '#f5f3ff',
        border: '#ddd6fe'
      };
    case 'ACCEPTED':
      return {
        text: 'Đã tiếp nhận',
        color: '#2563eb',
        bg: '#eff6ff',
        border: '#bfdbfe'
      };
    case 'IN_PROGRESS':
      return {
        text: 'Đang thu gom',
        color: '#2563eb',
        bg: '#eff6ff',
        border: '#bfdbfe'
      };
    case 'PAUSED':
      return {
        text: 'Tạm dừng',
        color: '#f97316',
        bg: '#fff7ed',
        border: '#fed7aa'
      };
    default:
      return {
        text: 'Chờ thu gom',
        color: '#64748b',
        bg: '#f1f5f9',
        border: '#e2e8f0'
      };
  }
};

export default function MapPage({ bins = [], selectedBin: initialSelectedBin, employeeLocations = [], onNotify, onSendCommand }) {
  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const binMarkersRef = useRef(new Map());
  const employeeMarkersRef = useRef(new Map());
  const routeLayerRef = useRef(null);
  const routeArrowsLayerRef = useRef(null);
  const waypointsLayerRef = useRef(null);

  // Guards for Single-Shot Initial Focus
  const initialHandledBinIdRef = useRef(null);
  const initialBoundsFittedRef = useRef(false);

  // Selection state IDs (stable primitives to prevent redundant focus loops)
  const [selectedBinId, setSelectedBinId] = useState(null);
  const [selectedTruckId, setSelectedTruckId] = useState(null);
  const [selectedJobId, setSelectedJobId] = useState(null);
  const [panelMode, setPanelMode] = useState('bin'); // 'bin' | 'truck' | 'job'
  const [listTab, setListTab] = useState('bins'); // 'bins' | 'trucks' | 'jobs'

  const selectedBinIdRef = useRef(null);
  const selectedTruckIdRef = useRef(null);

  useEffect(() => {
    selectedBinIdRef.current = selectedBinId;
  }, [selectedBinId]);

  useEffect(() => {
    selectedTruckIdRef.current = selectedTruckId;
  }, [selectedTruckId]);

  const [employees, setEmployees] = useState([]);
  const [activeJobs, setActiveJobs] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatus, setFilterStatus] = useState('all'); // all, overfull, full, partial, empty, offline

  const [routeInfo, setRouteInfo] = useState(null);
  const [calculatingRoute, setCalculatingRoute] = useState(false);
  const [copiedCoords, setCopiedCoords] = useState(false);
  const [commandInProgress, setCommandInProgress] = useState(false);

  // Overfull Realtime Alert Banner
  const [overfullAlert, setOverfullAlert] = useState(null);

  // Quick Dispatch Modal State
  const [dispatchModalOpen, setDispatchModalOpen] = useState(false);
  const [dispatchTruck, setDispatchTruck] = useState(null);
  const [selectedBinIdsForDispatch, setSelectedBinIdsForDispatch] = useState([]);
  const [dispatching, setDispatching] = useState(false);

  // Reassign Modal State
  const [reassignModalOpen, setReassignModalOpen] = useState(false);
  const [reassigningJob, setReassigningJob] = useState(null);
  const [reassignTargetTruck, setReassignTargetTruck] = useState(null);
  const [reassigning, setReassigning] = useState(false);

  // Edit Coords Modal
  const [editingBin, setEditingBin] = useState(null);
  const [editLat, setEditLat] = useState('');
  const [editLng, setEditLng] = useState('');
  const [savingCoords, setSavingCoords] = useState(false);

  // Helper get color for fill percent
  const getFillColor = (level, isOnline) => {
    if (!isOnline) return '#64748B';
    if (level >= 85) return '#EF4444';
    if (level >= 70) return '#F97316';
    if (level >= 30) return '#F59E0B';
    return '#10B981';
  };

  const getFillLabel = (level, isOnline) => {
    if (!isOnline) return 'Offline';
    if (level >= 85) return 'Quá đầy (≥85%)';
    if (level >= 70) return 'Đầy (70-84%)';
    if (level >= 30) return 'Mức vừa (30-69%)';
    return 'Trống (<30%)';
  };

  // Format Time Helpers (Vietnam UTC+7)
  const formatTime = (dateStr) => {
    return formatVietnamTime(dateStr, true);
  };

  const formatLastSeen = (dateStr) => {
    if (!dateStr) return '--:--:--';
    return formatVietnamTime(dateStr, true);
  };

  const formatDateTime = (dateStr) => {
    return formatVietnamDateTime(dateStr, { second: undefined });
  };

  // Clear Route Layers
  const handleClearRoute = useCallback(() => {
    if (routeLayerRef.current) routeLayerRef.current.remove();
    if (routeArrowsLayerRef.current) routeArrowsLayerRef.current.remove();
    if (waypointsLayerRef.current) waypointsLayerRef.current.remove();
    routeLayerRef.current = null;
    routeArrowsLayerRef.current = null;
    waypointsLayerRef.current = null;
    setRouteInfo(null);
  }, []);

  // Core Selector Handlers (Only focuses ONCE upon explicit user selection)
  const handleSelectBin = useCallback((binOrId, shouldFly = true) => {
    if (!binOrId) return;
    const bId = typeof binOrId === 'string' ? binOrId : binOrId.device_id;
    const binObj = (bins || []).find(b => b.device_id === bId) || (typeof binOrId === 'object' ? binOrId : null);
    if (!binObj) return;

    setSelectedBinId(bId);
    setSelectedTruckId(null);
    setSelectedJobId(null);
    setPanelMode('bin');
    setListTab('bins');
    handleClearRoute();

    if (shouldFly) {
      const lat = Number(binObj.latitude);
      const lng = Number(binObj.longitude);
      const map = mapInstanceRef.current;
      if (map && Number.isFinite(lat) && Number.isFinite(lng) && lat !== 0) {
        map.flyTo([lat, lng], 16, { animate: true, duration: 0.5 });
        setTimeout(() => {
          const marker = binMarkersRef.current.get(bId);
          if (marker) marker.openPopup();
        }, 150);
      }
    }
  }, [bins, handleClearRoute]);

  // Draw Route (Google Maps Navigation Style: Solid line, Animated flow, Waypoints with checkmarks)
  const drawTruckRoute = useCallback((truck, currentJob, shouldFitBounds = true) => {
    const map = mapInstanceRef.current;
    if (!map) return;

    if (!currentJob || !currentJob.target_bin_ids || currentJob.target_bin_ids.length === 0) {
      handleClearRoute();
      return;
    }

    const truckLat = Number(truck.latitude || 10.7769);
    const truckLng = Number(truck.longitude || 106.7009);
    const targetBins = (currentJob.target_bin_ids || [])
      .map(id => (bins || []).find(b => b.device_id === id))
      .filter(b => b && Number(b.latitude) && Number(b.longitude));

    if (targetBins.length === 0) return;

    const completedIds = getCompletedBinIds(currentJob);

    const waypointsData = targetBins.map((b, idx) => ({
      id: b.device_id,
      name: b.name || b.device_id,
      lat: Number(b.latitude),
      lng: Number(b.longitude),
      level: Number(b.level_percent || 0),
      isDone: completedIds.includes(b.device_id)
    }));

    const coordinates = [
      [truckLng, truckLat],
      ...targetBins.map(b => [Number(b.longitude), Number(b.latitude)])
    ];

    setCalculatingRoute(true);
    api.calculateRoute(coordinates).then(result => {
      setRouteInfo({
        ...result,
        truckId: truck.truckId || 'TRUCK',
        driverName: truck.driverName || truck.full_name || currentJob.employee_name,
        jobId: currentJob.id,
        targetBinsCount: targetBins.length,
        waypoints: waypointsData
      });

      if (routeLayerRef.current) routeLayerRef.current.remove();
      if (routeArrowsLayerRef.current) routeArrowsLayerRef.current.remove();
      if (waypointsLayerRef.current) waypointsLayerRef.current.remove();

      const latLngs = result.coordinates.map(([lng, lat]) => [lat, lng]);

      // 1. Viền ngoài trắng phát sáng
      const outerGlowLine = L.polyline(latLngs, {
        color: '#ffffff',
        weight: 12,
        opacity: 0.95,
        lineJoin: 'round',
        lineCap: 'round'
      });

      // 2. Tuyến chính màu xanh đậm
      const mainSolidLine = L.polyline(latLngs, {
        color: '#2563eb',
        weight: 7,
        opacity: 0.95,
        lineJoin: 'round',
        lineCap: 'round'
      });

      // 3. Nét đứt chuyển động chỉ hướng dòng chảy
      const animatedDashLine = L.polyline(latLngs, {
        color: '#93c5fd',
        weight: 4,
        opacity: 0.95,
        dashArray: '10, 16',
        className: 'grab-route-flow',
        lineJoin: 'round',
        lineCap: 'round'
      });

      const polylineGroup = L.featureGroup([outerGlowLine, mainSolidLine, animatedDashLine]).addTo(map);
      routeLayerRef.current = polylineGroup;

      // 4. Mũi tên chỉ hướng dọc đường đi
      const arrowsGroup = L.featureGroup();
      const step = Math.max(5, Math.floor(latLngs.length / 14));
      for (let i = 1; i < latLngs.length - 1; i += step) {
        const p1 = latLngs[i];
        const p2 = latLngs[Math.min(i + 1, latLngs.length - 1)];
        const bearing = calculateBearing(p1[0], p1[1], p2[0], p2[1]);

        const arrowIcon = L.divIcon({
          className: 'route-directional-arrow-icon',
          html: `
            <div style="
              transform: rotate(${bearing}deg);
              width: 14px;
              height: 14px;
              display: flex;
              align-items: center;
              justify-content: center;
              pointer-events: none;
            ">
              <svg width="10" height="10" viewBox="0 0 24 24" fill="#2563eb">
                <path d="M12 2L22 19L12 15L2 19L12 2Z"/>
              </svg>
            </div>
          `,
          iconSize: [14, 14],
          iconAnchor: [7, 7]
        });

        L.marker(p1, { icon: arrowIcon, interactive: false }).addTo(arrowsGroup);
      }
      arrowsGroup.addTo(map);
      routeArrowsLayerRef.current = arrowsGroup;

      // 5. Badges điểm dừng thu gom (Click vào waypoint sẽ trực tiếp mở thùng đó)
      const waypointsGroup = L.featureGroup();
      targetBins.forEach((targetBin, idx) => {
        const isDone = completedIds.includes(targetBin.device_id);

        const waypointIcon = L.divIcon({
          className: 'route-waypoint-pill-wrapper',
          html: `
            <div style="
              transform: translate(-50%, -40px);
              display: inline-flex;
              align-items: center;
              gap: 3px;
              padding: 1.5px 6px;
              border-radius: 4px;
              background: ${isDone ? '#16a34a' : '#2563eb'};
              color: #ffffff;
              font-size: 9.5px;
              font-weight: 700;
              cursor: pointer;
              white-space: nowrap;
            ">
              <span>${isDone ? '✓ Đã gom' : `Điểm ${idx + 1}`}</span>
            </div>
          `,
          iconSize: [0, 0]
        });
        const wpMarker = L.marker([Number(targetBin.latitude), Number(targetBin.longitude)], { icon: waypointIcon, interactive: true }).addTo(waypointsGroup);
        wpMarker.on('click', () => {
          handleSelectBin(targetBin, true);
        });
      });

      waypointsGroup.addTo(map);
      waypointsLayerRef.current = waypointsGroup;

      if (shouldFitBounds) {
        map.fitBounds(polylineGroup.getBounds(), { padding: [60, 60], maxZoom: 16 });
      }
    }).catch(err => {
      onNotify(`Lỗi định tuyến OSRM: ${err.message}`, 'error');
    }).finally(() => {
      setCalculatingRoute(false);
    });
  }, [bins, onNotify, handleClearRoute, handleSelectBin]);

  const handleSelectTruck = useCallback((empOrId, shouldFly = true) => {
    if (!empOrId) return;
    const eId = typeof empOrId === 'string' ? empOrId : (empOrId.employee_id || empOrId.id);
    const empObj = (employees || []).find(e => String(e.employee_id || e.id).toLowerCase() === String(eId).toLowerCase()) || (typeof empOrId === 'object' ? empOrId : null);
    if (!empObj) return;

    const currentJob = (activeJobs || []).find(j =>
      String(j.employee_id).toLowerCase() === String(eId).toLowerCase() &&
      ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'PAUSED'].includes(j.status)
    );
    const isCollecting = Boolean(currentJob);
    const truckId = getStableTruckId(empObj);
    const driverName = empObj.full_name || empObj.username || 'Tài xế';
    const truckObj = { ...empObj, truckId, driverName, isCollecting, currentJob };

    setSelectedTruckId(eId);
    setSelectedBinId(null);
    setSelectedJobId(null);
    setPanelMode('truck');
    setListTab('trucks');

    if (isCollecting && currentJob) {
      drawTruckRoute(truckObj, currentJob, shouldFly);
    } else {
      handleClearRoute();
      if (shouldFly) {
        const lat = Number(empObj.latitude);
        const lng = Number(empObj.longitude);
        const map = mapInstanceRef.current;
        if (map && Number.isFinite(lat) && Number.isFinite(lng) && lat !== 0) {
          map.flyTo([lat, lng], 15.5, { animate: true, duration: 0.5 });
          setTimeout(() => {
            const marker = employeeMarkersRef.current.get(eId);
            if (marker) marker.openPopup();
          }, 150);
        }
      }
    }
  }, [employees, activeJobs, drawTruckRoute, handleClearRoute]);

  const handleSelectJob = useCallback((job, shouldFly = true) => {
    if (!job) return;
    setSelectedJobId(job.id);
    setSelectedBinId(null);
    setSelectedTruckId(job.employee_id);
    setPanelMode('job');
    setListTab('jobs');

    const emp = (employees || []).find(e => String(e.employee_id || e.id).toLowerCase() === String(job.employee_id).toLowerCase());
    const truckObj = emp ? { ...emp, truckId: getStableTruckId(emp), driverName: job.employee_name, isCollecting: true, currentJob: job } : null;

    if (truckObj) {
      drawTruckRoute(truckObj, job, shouldFly);
    }
  }, [employees, drawTruckRoute]);

  // Load initial employees & active dispatch jobs
  useEffect(() => {
    async function loadData() {
      try {
        const [locList, jobsList] = await Promise.all([
          api.getMapLocations().catch(() => []),
          api.getActiveDispatchJobs().catch(() => [])
        ]);
        if (Array.isArray(locList) && locList.length > 0) setEmployees(locList);
        if (Array.isArray(jobsList)) setActiveJobs(jobsList);
      } catch (_) { }
    }
    loadData();
  }, []);

  // Listen to Realtime Socket.IO Events
  useEffect(() => {
    const socket = getSocket();
    if (!socket) return;

    const onJobUpdated = (job) => {
      setActiveJobs(prev => {
        const filtered = prev.filter(j => j.id !== job.id);
        if (['PENDING', 'ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'PAUSED'].includes(job.status)) {
          filtered.push(job);
        }
        return filtered;
      });

      const msg = getJobStatusLabel(job.status);
      onNotify(`Nhiệm vụ ${job.id?.slice(-8) || ''} (${job.employee_name || 'Tài xế'}): ${msg}`, 'info');
    };

    const onOverfullAlert = (alertData) => {
      setOverfullAlert(alertData);
      onNotify(`🚨 CẢNH BÁO: ${alertData.name || alertData.binId} đã đầy ${alertData.levelPercent}%!`, 'warning');
    };

    socket.on('jobUpdated', onJobUpdated);
    socket.on('binOverfullAlert', onOverfullAlert);

    return () => {
      socket.off('jobUpdated', onJobUpdated);
      socket.off('binOverfullAlert', onOverfullAlert);
    };
  }, [onNotify]);

  // Sync employeeLocations from Realtime Socket.IO props
  useEffect(() => {
    if (Array.isArray(employeeLocations) && employeeLocations.length > 0) {
      setEmployees(prev => {
        const map = new Map(prev.map(e => [e.employee_id || e.id, e]));
        for (const emp of employeeLocations) {
          map.set(emp.employee_id || emp.id, { ...(map.get(emp.employee_id || emp.id) || {}), ...emp });
        }
        return [...map.values()];
      });
    }
  }, [employeeLocations]);

  // Handle Initial Selected Bin ONLY ONCE upon prop change
  useEffect(() => {
    if (!initialSelectedBin?.device_id) return;
    if (initialHandledBinIdRef.current === initialSelectedBin.device_id) return;
    initialHandledBinIdRef.current = initialSelectedBin.device_id;

    const timer = setTimeout(() => {
      handleSelectBin(initialSelectedBin, true);
    }, 200);

    return () => clearTimeout(timer);
  }, [initialSelectedBin, handleSelectBin]);

  // Default selection if nothing is selected on first data load
  useEffect(() => {
    if ((bins || []).length > 0 && !selectedBinId && !selectedTruckId && !selectedJobId && !initialSelectedBin) {
      setSelectedBinId(bins[0].device_id);
      setPanelMode('bin');
    }
  }, [bins, selectedBinId, selectedTruckId, selectedJobId, initialSelectedBin]);

  // Initialize Leaflet Map
  useEffect(() => {
    if (!mapContainerRef.current || mapInstanceRef.current) return;

    const map = L.map(mapContainerRef.current, {
      center: [10.7769, 106.7009],
      zoom: 13,
      zoomControl: false
    });

    L.control.zoom({ position: 'bottomright' }).addTo(map);

    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
      attribution: '&copy; CartoDB &copy; OpenStreetMap',
      maxZoom: 19
    }).addTo(map);

    mapInstanceRef.current = map;

    // Auto-invalidate size on container resize
    const resizeObserver = new ResizeObserver(() => {
      map.invalidateSize();
    });
    if (mapContainerRef.current) {
      resizeObserver.observe(mapContainerRef.current);
    }

    return () => {
      resizeObserver.disconnect();
      map.remove();
      mapInstanceRef.current = null;
    };
  }, []);

  // 1. Render Smart Bin Markers
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    binMarkersRef.current.forEach(marker => marker.remove());
    binMarkersRef.current.clear();

    const bounds = L.latLngBounds([]);
    let validCoordsCount = 0;

    (bins || []).forEach(bin => {
      const lat = Number(bin.latitude);
      const lng = Number(bin.longitude);
      if (!Number.isFinite(lat) || !Number.isFinite(lng) || lat === 0 || lng === 0) return;

      const level = Math.min(100, Math.max(0, Number(bin.level_percent || 0)));
      const isSelected = panelMode === 'bin' && selectedBinId === bin.device_id;
      validCoordsCount++;
      bounds.extend([lat, lng]);

      const pinColor = getFillColor(level, bin.is_online);
      const isUrgent = bin.is_online && level >= 85;

      const pinHtml = `
        <div class="bin-marker-node ${isSelected ? 'is-selected' : ''}" style="
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          transform: ${isSelected ? 'scale(1.15)' : 'scale(1)'};
          transition: transform 160ms ease;
          position: relative;
        ">
          ${isUrgent ? `
            <div style="
              position: absolute;
              top: 0px;
              left: calc(50% - 13px);
              width: 26px;
              height: 26px;
              border-radius: 50%;
              background: #ef4444;
              opacity: 0.75;
              animation: radarPulse 1.8s cubic-bezier(0.25, 0.46, 0.45, 0.94) infinite;
              pointer-events: none;
              z-index: 1;
            "></div>
          ` : ''}

          <div style="
            width: 26px;
            height: 26px;
            border-radius: 50%;
            background-color: ${pinColor};
            display: flex;
            align-items: center;
            justify-content: center;
            color: #ffffff;
            position: relative;
            z-index: 2;
          ">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 3h6v2H9z"></path>
              <path d="M4 6h16"></path>
              <path d="M5 7l1.5 13.5a2 2 0 002 1.5h7a2 2 0 002-1.5L19 7"></path>
              <line x1="9" y1="10" x2="9" y2="17"></line>
              <line x1="12" y1="10" x2="12" y2="17"></line>
              <line x1="15" y1="10" x2="15" y2="17"></line>
            </svg>
          </div>

          <div class="bin-label" style="
            margin-top: 2px;
            padding: 1px 4px;
            border-radius: 3px;
            background: rgba(255, 255, 255, 0.95);
            color: #475569;
            font-size: 9.5px;
            font-weight: 700;
            line-height: 1.1;
            white-space: nowrap;
            z-index: 2;
          ">
            ${!bin.is_online ? 'OFF' : `${level}%`}
          </div>
        </div>
      `;

      const customIcon = L.divIcon({
        className: 'bin-marker-icon-wrapper',
        html: pinHtml,
        iconSize: [36, 42],
        iconAnchor: [18, 21],
        popupAnchor: [0, -22]
      });

      const marker = L.marker([lat, lng], { icon: customIcon, zIndexOffset: isSelected ? 500 : 100 }).addTo(map);

      // Popup Thùng rác
      const isLidOpen = bin.state === 'OPEN';
      const popupContent = `
        <div style="font-family: inherit; min-width: 240px; padding: 4px;">
          <div style="display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; margin-bottom: 8px; padding-bottom: 6px; border-bottom: 1px solid #f1f5f9;">
            <div>
              <span style="font-size: 10px; font-weight: 800; color: #64748b; font-family: monospace; display: block;">MÃ: ${bin.device_id}</span>
              <strong style="font-size: 13.5px; color: #0f172a; line-height: 1.3;">${bin.name || bin.device_id}</strong>
            </div>
            <span style="display: inline-flex; align-items: center; gap: 4px; padding: 2px 8px; border-radius: 9999px; font-size: 10.5px; font-weight: 700; background: ${bin.is_online ? '#ecfdf5' : '#f1f5f9'}; color: ${bin.is_online ? '#16a34a' : '#64748b'}; border: 1px solid ${bin.is_online ? '#bbf7d0' : '#e2e8f0'};">
              <span style="width: 5px; height: 5px; border-radius: 50%; background: ${bin.is_online ? '#16a34a' : '#94a3b8'};"></span>
              ${bin.is_online ? 'Online' : 'Offline'}
            </span>
          </div>

          <div style="font-size: 11.5px; color: #475569; margin-bottom: 4px; line-height: 1.35;">
            📍 <strong>Vị trí:</strong> ${bin.location || 'Chưa cập nhật'}
          </div>

          <div style="font-size: 11px; color: #64748b; margin-bottom: 10px; font-family: monospace;">
            🌐 <strong>GPS:</strong> ${lat.toFixed(4)}, ${lng.toFixed(4)}
          </div>

          <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; background: #f8fafc; padding: 8px 12px; border-radius: 10px; font-size: 12px; border: 1px solid #e2e8f0;">
            <div>
              <span style="color: #64748b; font-weight: 500;">Mức rác: </span>
              <strong style="color: ${pinColor}; font-weight: 800; font-size: 13px;">${level}%</strong>
            </div>
            <div>
              <span style="color: #64748b; font-weight: 500;">Nắp thùng: </span>
              <strong style="color: ${isLidOpen ? '#16a34a' : '#334155'}; font-weight: 700;">${isLidOpen ? 'Đang mở' : 'Đã đóng'}</strong>
            </div>
          </div>
        </div>
      `;

      marker.bindPopup(popupContent, { closeButton: false, offset: [0, -10] });

      marker.on('click', () => {
        handleSelectBin(bin, true);
      });

      marker.on('mouseover', () => marker.openPopup());
      marker.on('mouseout', () => {
        if (selectedBinIdRef.current !== bin.device_id) marker.closePopup();
      });

      binMarkersRef.current.set(bin.device_id, marker);
    });

    // Auto fit bounds ONCE on initial mount
    if (validCoordsCount > 0 && !initialBoundsFittedRef.current && !initialSelectedBin) {
      initialBoundsFittedRef.current = true;
      map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
    }
  }, [bins, selectedBinId, panelMode, handleSelectBin, initialSelectedBin]);

  // 2. Render Collection Truck Markers
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    employeeMarkersRef.current.forEach(marker => marker.remove());
    employeeMarkersRef.current.clear();

    (employees || []).forEach((emp, index) => {
      const lat = Number(emp.latitude);
      const lng = Number(emp.longitude);
      if (!Number.isFinite(lat) || !Number.isFinite(lng) || lat === 0 || lng === 0) return;

      const empId = String(emp.employee_id || emp.id || '').toLowerCase();
      const currentJob = activeJobs.find(j =>
        String(j.employee_id).toLowerCase() === empId &&
        ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'PAUSED'].includes(j.status)
      );

      const truckId = getStableTruckId(emp, index);
      const driverName = emp.full_name || emp.username || `Tài xế ${index + 1}`;
      const shortName = driverName.split(' ').slice(-2).join(' ');
      const speedKmH = emp.speed ? Number(emp.speed * 3.6).toFixed(0) : '0';

      const truckStatus = getTruckStatusInfo(currentJob);
      const isBusyTruck = Boolean(currentJob && ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS'].includes(currentJob.status));

      const isSelected = panelMode === 'truck' && selectedTruckId === (emp.employee_id || emp.id);
      const isDimmed = panelMode === 'truck' && selectedTruckId && !isSelected;
      const shouldPulse = isBusyTruck || isSelected;

      const truckHtml = `
        <div class="truck-marker-node ${isSelected ? 'is-selected' : ''}" style="
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          transform: ${isSelected ? 'scale(1.15)' : isDimmed ? 'scale(0.92)' : 'scale(1)'};
          opacity: ${isDimmed ? '0.4' : '1'};
          transition: transform 160ms ease, opacity 160ms ease;
          position: relative;
        ">
          ${shouldPulse ? `
            <div style="
              position: absolute;
              top: 0px;
              left: calc(50% - 13px);
              width: 26px;
              height: 26px;
              border-radius: 50%;
              background: ${truckStatus.markerBg};
              opacity: 0.75;
              animation: radarPulse 1.8s cubic-bezier(0.25, 0.46, 0.45, 0.94) infinite;
              pointer-events: none;
              z-index: 1;
            "></div>
          ` : ''}

          <div style="
            width: 26px;
            height: 26px;
            border-radius: 50%;
            background-color: ${truckStatus.markerBg};
            display: flex;
            align-items: center;
            justify-content: center;
            color: #ffffff;
            position: relative;
            z-index: 2;
          ">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="1" y="3" width="15" height="13"></rect>
              <polygon points="16 8 20 8 23 11 23 16 16 8"></polygon>
              <circle cx="5.5" cy="18.5" r="2.5"></circle>
              <circle cx="18.5" cy="18.5" r="2.5"></circle>
            </svg>
          </div>

          <div class="truck-label" style="
            margin-top: 2px;
            padding: 1px 4px;
            border-radius: 3px;
            background: rgba(255, 255, 255, 0.95);
            color: #475569;
            font-size: 9.5px;
            font-weight: 700;
            line-height: 1.1;
            white-space: nowrap;
            z-index: 2;
          ">
            ${shortName}
          </div>
        </div>
      `;

      const truckIcon = L.divIcon({
        className: 'truck-marker-icon-wrapper',
        html: truckHtml,
        iconSize: [46, 42],
        iconAnchor: [23, 21],
        popupAnchor: [0, -22]
      });

      const marker = L.marker([lat, lng], {
        icon: truckIcon,
        zIndexOffset: isSelected ? 1000 : 800,
        opacity: isDimmed ? 0.4 : 1
      }).addTo(map);

      const truckPopup = `
        <div style="font-family: inherit; min-width: 250px; padding: 2px;">
          <div style="display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; margin-bottom: 8px; padding-bottom: 6px; border-bottom: 1px solid #f1f5f9;">
            <div>
              <div style="font-size: 11px; font-weight: 800; color: #2563eb; font-family: monospace; display: flex; align-items: center; gap: 4px;">
                <span>🚚</span> <span>Xe thu gom: ${truckId}</span>
              </div>
              <div style="font-size: 12px; font-weight: 600; color: #334155; margin-top: 2px;">
                👤 Tài xế: <strong style="color: #0f172a;">${driverName}</strong>
              </div>
            </div>
            <span style="display: inline-flex; align-items: center; gap: 4px; padding: 2.5px 8px; border-radius: 9999px; font-size: 10.5px; font-weight: 700; background: #ecfdf5; color: #16a34a; border: 1px solid #bbf7d0; white-space: nowrap;">
              <span style="width: 5px; height: 5px; border-radius: 50%; background: #16a34a;"></span>
              Online
            </span>
          </div>

          <div style="display: flex; flex-direction: column; gap: 5px; font-size: 11.5px; margin-bottom: 8px;">
            <div style="color: #475569; display: flex; align-items: center; justify-content: space-between;">
              <span>📍 <strong>Tọa độ:</strong></span>
              <span style="font-family: monospace; color: #334155; font-size: 11px;">${lat.toFixed(4)}, ${lng.toFixed(4)}</span>
            </div>
            <div style="color: #475569; display: flex; align-items: center; justify-content: space-between;">
              <span>🚦 <strong>Trạng thái:</strong></span>
              <span style="font-weight: 700; color: ${truckStatus.color}; font-size: 11px;">
                ${truckStatus.badgeText}
              </span>
            </div>
          </div>

          <div style="display: flex; align-items: center; justify-content: space-between; background: #f8fafc; padding: 6px 12px; border-radius: 8px; font-size: 11px; border: 1px solid #e2e8f0;">
            <div>
              <span style="color: #64748b;">Vận tốc: </span>
              <strong style="color: #0f172a; font-size: 11.5px;">${speedKmH} km/h</strong>
            </div>
            <div style="width: 1px; height: 12px; background: #cbd5e1;"></div>
            <div>
              <span style="color: #64748b;">Hướng: </span>
              <strong style="color: #0f172a; font-size: 11.5px;">${Number(emp.heading || 0).toFixed(0)}°</strong>
            </div>
            <div style="width: 1px; height: 12px; background: #cbd5e1;"></div>
            <div>
              <span style="color: #64748b;">Cập nhật: </span>
              <strong style="color: #0f172a; font-size: 11px; font-family: monospace;">${formatTime(emp.recorded_at || Date.now())}</strong>
            </div>
          </div>
        </div>
      `;

      marker.bindPopup(truckPopup, { closeButton: false, offset: [0, -10] });

      marker.on('click', () => {
        handleSelectTruck(emp, true);
      });

      marker.on('mouseover', () => marker.openPopup());
      marker.on('mouseout', () => {
        if (selectedTruckIdRef.current !== (emp.employee_id || emp.id)) marker.closePopup();
      });

      employeeMarkersRef.current.set(emp.employee_id || emp.id, marker);
    });
  }, [employees, activeJobs, selectedTruckId, panelMode, handleSelectTruck]);

  // Handle Command Action for Active Bin (2-way Handshake)
  const handleBinCommand = async (action) => {
    const targetBin = (bins || []).find(b => b.device_id === selectedBinId);
    if (!targetBin || !onSendCommand || commandInProgress) return;
    setCommandInProgress(true);
    try {
      await onSendCommand(targetBin.device_id, action);
    } catch (_) {
    } finally {
      setCommandInProgress(false);
    }
  };

  // Open Quick Dispatch Modal
  const handleOpenDispatchModal = (truck = null, preselectedBin = null) => {
    const availableEmployees = (employees || []).filter(e => {
      const eId = String(e.employee_id || e.id || '').toLowerCase();
      return !activeJobs.some(j => String(j.employee_id).toLowerCase() === eId && ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'PAUSED'].includes(j.status));
    });

    setDispatchTruck(truck || availableEmployees[0] || employees[0] || null);
    if (preselectedBin) {
      setSelectedBinIdsForDispatch([preselectedBin.device_id]);
    } else {
      const urgentBins = (bins || []).filter(b => (b.level_percent || 0) >= 80 && b.collection_status !== 'RESERVED');
      setSelectedBinIdsForDispatch(urgentBins.slice(0, 3).map(b => b.device_id));
    }
    setDispatchModalOpen(true);
  };

  // Submit Dispatch Job (Admin Gán việc)
  const handleSubmitDispatch = async () => {
    if (!dispatchTruck) {
      onNotify('Vui lòng chọn nhân viên / xe thu gom.', 'error');
      return;
    }
    if (selectedBinIdsForDispatch.length === 0) {
      onNotify('Vui lòng chọn ít nhất 1 thùng rác để gán tuyến.', 'error');
      return;
    }

    setDispatching(true);
    try {
      const empId = dispatchTruck.employee_id || dispatchTruck.id;
      const empName = dispatchTruck.full_name || dispatchTruck.username || 'Nhân viên thu gom';
      const res = await api.assignDispatchJob(empId, empName, selectedBinIdsForDispatch);

      onNotify(`Đã phân công nhiệm vụ cho ${empName}: ${selectedBinIdsForDispatch.length} thùng rác! (Chờ tài xế xác nhận)`, 'success');
      setDispatchModalOpen(false);

      if (res?.job) {
        handleSelectJob(res.job, true);
      }
    } catch (err) {
      onNotify(`Lỗi gán tuyến: ${err.message}`, 'error');
    } finally {
      setDispatching(false);
    }
  };

  // Open Reassign Modal
  const handleOpenReassignModal = (job) => {
    if (!job) return;
    setReassigningJob(job);
    const availableEmployees = (employees || []).filter(e => {
      const eId = String(e.employee_id || e.id || '').toLowerCase();
      const isCurrentOwner = eId === String(job.employee_id).toLowerCase();
      const isBusyWithOtherJob = activeJobs.some(j => j.id !== job.id && String(j.employee_id).toLowerCase() === eId && ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'PAUSED'].includes(j.status));
      return !isCurrentOwner && !isBusyWithOtherJob;
    });

    setReassignTargetTruck(availableEmployees[0] || null);
    setReassignModalOpen(true);
  };

  // Submit Reassign Job
  const handleSubmitReassign = async () => {
    if (!reassigningJob || !reassignTargetTruck) {
      onNotify('Vui lòng chọn xe / nhân viên tiếp quản nhiệm vụ.', 'error');
      return;
    }

    setReassigning(true);
    try {
      const targetEmpId = reassignTargetTruck.employee_id || reassignTargetTruck.id;
      const targetEmpName = reassignTargetTruck.full_name || reassignTargetTruck.username || 'Tài xế mới';
      const res = await api.reassignDispatchJob(reassigningJob.id, targetEmpId, targetEmpName);

      onNotify(`Đã điều chuyển nhiệm vụ sang ${targetEmpName} thành công!`, 'success');
      setReassignModalOpen(false);

      if (res?.new_job) {
        handleSelectJob(res.new_job, true);
      }
    } catch (err) {
      onNotify(`Lỗi điều chuyển: ${err.message}`, 'error');
    } finally {
      setReassigning(false);
    }
  };

  // Cancel an Active Dispatch Job
  const handleCancelJob = async (jobId) => {
    if (!jobId) return;
    if (!window.confirm('Bạn có chắc chắn muốn hủy nhiệm vụ thu gom này?')) return;
    try {
      await api.cancelDispatchJob(jobId);
      handleClearRoute();
      if (selectedJobId === jobId) setSelectedJobId(null);
      onNotify('Đã hủy tuyến thu gom thành công.', 'info');
    } catch (err) {
      onNotify(`Lỗi hủy tuyến: ${err.message}`, 'error');
    }
  };

  // 1-Click Dispatch from Realtime Alert Banner
  const handleQuickDispatchFromAlert = async () => {
    if (!overfullAlert) return;
    const targetBinId = overfullAlert.binId;
    const nearestTruck = overfullAlert.suggestedNearestTruck;

    if (!nearestTruck) {
      handleOpenDispatchModal(null, { device_id: targetBinId });
      return;
    }

    try {
      const res = await api.assignDispatchJob(nearestTruck.employee_id, nearestTruck.driverName, [targetBinId]);
      onNotify(`Đã điều phối ${nearestTruck.driverName} đến gom ${overfullAlert.name || targetBinId}! (Chờ xác nhận)`, 'success');
      setOverfullAlert(null);

      if (res?.job) {
        handleSelectJob(res.job, true);
      }
    } catch (err) {
      onNotify(`Lỗi điều phối nhanh: ${err.message}`, 'error');
    }
  };

  // Save Coordinates Modal
  const handleSaveCoordinates = async (e) => {
    e.preventDefault();
    if (!editingBin) return;

    const lat = Number(editLat);
    const lng = Number(editLng);

    if (!Number.isFinite(lat) || !Number.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
      onNotify('Tọa độ GPS không hợp lệ.', 'error');
      return;
    }

    setSavingCoords(true);
    try {
      await api.updateCoordinates(editingBin.device_id, lat, lng);
      onNotify(`Đã cập nhật tọa độ cho ${editingBin.name || editingBin.device_id}!`, 'success');
      setEditingBin(null);
    } catch (err) {
      onNotify(`Lỗi lưu tọa độ: ${err.message}`, 'error');
    } finally {
      setSavingCoords(false);
    }
  };

  // Copy Coordinates to Clipboard
  const handleCopyCoords = (lat, lng) => {
    if (!lat || !lng) return;
    navigator.clipboard.writeText(`${lat}, ${lng}`);
    setCopiedCoords(true);
    setTimeout(() => setCopiedCoords(false), 2000);
    onNotify('Đã sao chép tọa độ vào clipboard!', 'info');
  };

  // Filtered and Sorted bins list
  const filteredBins = useMemo(() => {
    return (bins || [])
      .filter(b => {
        const matchesSearch =
          (b.device_id || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
          (b.name || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
          (b.location || '').toLowerCase().includes(searchTerm.toLowerCase());

        if (!matchesSearch) return false;
        const level = Number(b.level_percent || 0);
        if (filterStatus === 'overfull') return level > 85 && b.is_online;
        if (filterStatus === 'full') return level >= 70 && level <= 85 && b.is_online;
        if (filterStatus === 'partial') return level >= 30 && level < 70 && b.is_online;
        if (filterStatus === 'empty') return level < 30 && b.is_online;
        if (filterStatus === 'offline') return !b.is_online;
        return true;
      })
      .sort((a, b) => {
        if (a.is_online && !b.is_online) return -1;
        if (!a.is_online && b.is_online) return 1;
        return (Number(b.level_percent) || 0) - (Number(a.level_percent) || 0);
      });
  }, [bins, searchTerm, filterStatus]);

  // Derived Active Objects (Always fresh with latest sensor readings)
  const activeBin = useMemo(() => {
    if (!selectedBinId) return null;
    return (bins || []).find(b => b.device_id === selectedBinId) || { device_id: selectedBinId };
  }, [bins, selectedBinId]);

  const activeTruck = useMemo(() => {
    if (!selectedTruckId) return null;
    const emp = (employees || []).find(e => String(e.employee_id || e.id).toLowerCase() === String(selectedTruckId).toLowerCase());
    if (!emp) return null;
    const empId = String(emp.employee_id || emp.id || '').toLowerCase();
    const currentJob = (activeJobs || []).find(j =>
      String(j.employee_id).toLowerCase() === empId &&
      ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'PAUSED'].includes(j.status)
    );
    const isCollecting = Boolean(currentJob);
    const truckId = getStableTruckId(emp);
    const driverName = emp.full_name || emp.username || 'Tài xế';
    return { ...emp, truckId, driverName, isCollecting, currentJob };
  }, [employees, activeJobs, selectedTruckId]);

  const selectedJob = useMemo(() => {
    if (!selectedJobId) return null;
    return (activeJobs || []).find(j => j.id === selectedJobId) || null;
  }, [activeJobs, selectedJobId]);

  const activeLevel = activeBin ? Math.min(100, Math.max(0, Number(activeBin.level_percent || 0))) : 0;
  const statusColor = activeBin ? getFillColor(activeLevel, activeBin.is_online) : '#16A34A';
  const statusText = activeBin ? getFillLabel(activeLevel, activeBin.is_online) : 'Trống (<30%)';

  const binCollectionStatus = activeBin ? getBinCollectionStatusInfo(activeBin, activeJobs) : null;
  const truckStatus = activeTruck ? getTruckStatusInfo(activeTruck.currentJob) : null;

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '1fr 400px',
      gap: '20px',
      height: 'calc(-120px + 122vh)',
      minHeight: '580px'
    }} className="map-page-grid">

      {/* LEFT COLUMN: Clean Map Container */}
      <div style={{
        position: 'relative',
        height: '100%',
        borderRadius: '16px',
        overflow: 'hidden',
        border: '1px solid #e2e8f0',
        boxShadow: '0 2px 8px rgba(0,0,0,0.04)'
      }}>
        {/* Leaflet Map Target */}
        <div ref={mapContainerRef} style={{ width: '100%', height: '100%' }} />

        {/* Top Realtime Overfull Alert Banner */}
        {overfullAlert && (
          <div style={{
            position: 'absolute',
            top: '16px',
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 1050,
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            padding: '10px 16px',
            borderRadius: '14px',
            backgroundColor: 'rgba(254, 242, 242, 0.98)',
            backdropFilter: 'blur(8px)',
            border: '1.5px solid #ef4444',
            boxShadow: '0 8px 24px rgba(220, 38, 38, 0.2)',
            color: '#991b1b',
            fontSize: '12.5px',
            fontWeight: 600,
            animation: 'fadeIn 300ms ease'
          }}>
            <AlertOctagon size={18} color="#dc2626" />
            <div>
              <strong>{overfullAlert.name || overfullAlert.binId}</strong> đã đầy <strong>{overfullAlert.levelPercent}%</strong>!
              {overfullAlert.suggestedNearestTruck && (
                <span style={{ marginLeft: '6px', color: '#b91c1c' }}>
                  (Gợi ý: <strong>{overfullAlert.suggestedNearestTruck.driverName}</strong> cách {overfullAlert.suggestedNearestTruck.distanceKm} km)
                </span>
              )}
            </div>
            <button
              onClick={handleQuickDispatchFromAlert}
              className="btn-primary"
              style={{
                padding: '5px 12px',
                fontSize: '11.5px',
                borderRadius: '8px',
                backgroundColor: '#dc2626',
                gap: '4px'
              }}
            >
              <Sparkles size={13} />
              <span>Tạo nhiệm vụ ngay</span>
            </button>
            <button
              onClick={() => setOverfullAlert(null)}
              style={{ background: 'none', border: 'none', color: '#991b1b', cursor: 'pointer', padding: '2px' }}
            >
              <X size={15} />
            </button>
          </div>
        )}

        {/* Full-width Route Router Bar at Top of Map */}
        {routeInfo && (
          <div style={{
            position: 'absolute',
            top: overfullAlert ? '68px' : '16px',
            left: '16px',
            right: '16px',
            zIndex: 1000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '12px',
            padding: '9px 16px',
            borderRadius: '14px',
            backgroundColor: 'rgba(255, 255, 255, 0.98)',
            backdropFilter: 'blur(12px)',
            border: '1.5px solid rgba(37, 99, 235, 0.25)',
            boxShadow: '0 8px 24px rgba(15, 23, 42, 0.12)',
            animation: 'fadeIn 250ms ease',
            pointerEvents: 'auto'
          }}>
            {/* Left: Start Truck + Steps Flow */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', overflowX: 'auto', flex: 1, paddingRight: '8px' }}>
              <span style={{ fontSize: '11px', fontWeight: 800, color: '#1e40af', textTransform: 'uppercase', flexShrink: 0, marginRight: '2px' }}>
                🗺️ Lộ trình:
              </span>

              <div style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '5px',
                padding: '3px 9px',
                borderRadius: '8px',
                backgroundColor: '#eff6ff',
                border: '1px solid #bfdbfe',
                color: '#1e40af',
                fontSize: '11.5px',
                fontWeight: 700,
                flexShrink: 0
              }}>
                <Truck size={13} color="#2563eb" />
                <span>{routeInfo.truckId} ({routeInfo.driverName})</span>
              </div>

              <span style={{ color: '#2563eb', fontWeight: 900, fontSize: '13px', flexShrink: 0 }}>➔</span>

              {/* Waypoints sequence */}
              {(routeInfo.waypoints || []).map((step, idx) => (
                <React.Fragment key={step.id || idx}>
                  <div
                    onClick={() => {
                      handleSelectBin(step.id, true);
                    }}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px',
                      padding: '3px 8px',
                      borderRadius: '8px',
                      backgroundColor: step.isDone ? '#f0fdf4' : '#ffffff',
                      border: `1.5px solid ${step.isDone ? '#16a34a' : '#cbd5e1'}`,
                      color: step.isDone ? '#15803d' : '#0f172a',
                      fontSize: '11.5px',
                      fontWeight: 700,
                      cursor: 'pointer',
                      flexShrink: 0,
                      transition: 'all 150ms ease'
                    }}
                    title="Bấm để xem chi tiết thùng rác này"
                  >
                    <span style={{
                      width: '16px',
                      height: '16px',
                      borderRadius: '50%',
                      backgroundColor: step.isDone ? '#16a34a' : '#2563eb',
                      color: '#ffffff',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '9.5px',
                      fontWeight: 800
                    }}>
                      {step.isDone ? '✓' : idx + 1}
                    </span>
                    <span>{step.name}</span>
                    <span style={{
                      fontSize: '9.5px',
                      padding: '1px 4px',
                      borderRadius: '4px',
                      backgroundColor: getFillColor(step.level, true),
                      color: '#ffffff',
                      fontWeight: 800
                    }}>
                      {step.level}%
                    </span>
                  </div>

                  {idx < (routeInfo.waypoints.length - 1) && (
                    <span style={{ color: '#2563eb', fontWeight: 900, fontSize: '13px', flexShrink: 0 }}>➔</span>
                  )}
                </React.Fragment>
              ))}
            </div>

            {/* Right: Distance & Time Metrics + Close button */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexShrink: 0, borderLeft: '1px solid #e2e8f0', paddingLeft: '12px' }}>
              <div style={{ fontSize: '12px', color: '#1e3a8a', fontWeight: 800, whiteSpace: 'nowrap' }}>
                📍 {(routeInfo.distanceMeters / 1000).toFixed(1)} km · ~{Math.round(routeInfo.durationSeconds / 60)} min
              </div>
              <button
                onClick={handleClearRoute}
                style={{
                  background: '#f1f5f9',
                  border: 'none',
                  color: '#64748b',
                  borderRadius: '6px',
                  padding: '4px 6px',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center'
                }}
                title="Đóng thanh lộ trình"
              >
                <X size={14} />
              </button>
            </div>
          </div>
        )}

        {/* Floating Legend Bar at Bottom-Left */}
        <div style={{
          position: 'absolute',
          bottom: '16px',
          left: '16px',
          zIndex: 1000,
          display: 'flex',
          alignItems: 'center',
          gap: '14px',
          padding: '8px 16px',
          borderRadius: '12px',
          backgroundColor: 'rgba(255, 255, 255, 0.96)',
          backdropFilter: 'blur(12px)',
          border: '1px solid rgba(226, 232, 240, 0.85)',
          boxShadow: '0 4px 18px rgba(0,0,0,0.08)',
          fontSize: '11.5px',
          fontWeight: 600,
          color: '#334155',
          flexWrap: 'nowrap'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <div style={{ width: '20px', height: '20px', borderRadius: '50%', backgroundColor: 'rgba(22, 163, 74, 0.12)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#16A34A' }}>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M9 3h6v2H9z" /><path d="M4 6h16" /><path d="M5 7l1.5 13.5a2 2 0 002 1.5h7a2 2 0 002-1.5L19 7" /><line x1="9" y1="10" x2="9" y2="17" /><line x1="12" y1="10" x2="12" y2="17" /><line x1="15" y1="10" x2="15" y2="17" /></svg>
            </div>
            <span>Trống (&lt;30%)</span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <div style={{ width: '20px', height: '20px', borderRadius: '50%', backgroundColor: 'rgba(245, 158, 11, 0.12)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#F59E0B' }}>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M9 3h6v2H9z" /><path d="M4 6h16" /><path d="M5 7l1.5 13.5a2 2 0 002 1.5h7a2 2 0 002-1.5L19 7" /><line x1="9" y1="10" x2="9" y2="17" /><line x1="12" y1="10" x2="12" y2="17" /><line x1="15" y1="10" x2="15" y2="17" /></svg>
            </div>
            <span>Mức vừa (30-70%)</span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <div style={{ width: '20px', height: '20px', borderRadius: '50%', backgroundColor: 'rgba(239, 68, 68, 0.12)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#EF4444' }}>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M9 3h6v2H9z" /><path d="M4 6h16" /><path d="M5 7l1.5 13.5a2 2 0 002 1.5h7a2 2 0 002-1.5L19 7" /><line x1="9" y1="10" x2="9" y2="17" /><line x1="12" y1="10" x2="12" y2="17" /><line x1="15" y1="10" x2="15" y2="17" /></svg>
            </div>
            <span>Đầy (70-85%)</span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <div style={{ width: '20px', height: '20px', borderRadius: '50%', backgroundColor: 'rgba(220, 38, 38, 0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#DC2626' }}>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M9 3h6v2H9z" /><path d="M4 6h16" /><path d="M5 7l1.5 13.5a2 2 0 002 1.5h7a2 2 0 002-1.5L19 7" /><line x1="9" y1="10" x2="9" y2="17" /><line x1="12" y1="10" x2="12" y2="17" /><line x1="15" y1="10" x2="15" y2="17" /></svg>
            </div>
            <span>Quá đầy (&gt;85%)</span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <div style={{ width: '20px', height: '20px', borderRadius: '50%', backgroundColor: 'rgba(100, 116, 139, 0.12)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#64748B' }}>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M9 3h6v2H9z" /><path d="M4 6h16" /><path d="M5 7l1.5 13.5a2 2 0 002 1.5h7a2 2 0 002-1.5L19 7" /><line x1="9" y1="10" x2="9" y2="17" /><line x1="12" y1="10" x2="12" y2="17" /><line x1="15" y1="10" x2="15" y2="17" /></svg>
            </div>
            <span>Offline</span>
          </div>

          <div style={{ width: '1px', height: '18px', backgroundColor: '#e2e8f0', margin: '0 2px' }} />

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Truck size={15} color="#2563EB" />
            <span style={{ color: '#334155' }}>Xe thu gom ({employees.length})</span>
          </div>
        </div>
      </div>

      {/* RIGHT COLUMN: Multi-Mode Detail Inspector & Lists Panel */}
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: '14px',
        height: '100%',
        overflowY: 'auto'
      }}>

        {/* ==================================================================== */}
        {/* 1. STATE: CLICK BIN (Chi tiết Thùng rác)                              */}
        {/* ==================================================================== */}
        {panelMode === 'bin' && activeBin && (
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '18px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 2px 8px rgba(0,0,0,0.03)',
            display: 'flex',
            flexDirection: 'column',
            gap: '14px'
          }}>
            {/* Header: Mã thiết bị + Tên thùng + Trực tuyến */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <span style={{ fontSize: '18px' }}>🗑️</span>
                  <h3 style={{ fontSize: '16px', fontWeight: 800, color: '#0f172a', margin: 0 }}>
                    {activeBin.device_id || 'BIN-001'}
                  </h3>
                </div>
                <span style={{ fontSize: '12px', color: '#2563eb', fontWeight: 700 }}>
                  {activeBin.name || activeBin.device_id}
                </span>
              </div>

              <span style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '5px',
                padding: '3px 10px',
                borderRadius: '9999px',
                fontSize: '11px',
                fontWeight: 700,
                backgroundColor: activeBin.is_online ? '#ecfdf5' : '#f1f5f9',
                color: activeBin.is_online ? '#16a34a' : '#64748b',
                border: `1px solid ${activeBin.is_online ? '#bbf7d0' : '#e2e8f0'}`
              }}>
                <span style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: activeBin.is_online ? '#16a34a' : '#94a3b8' }} />
                <span>{activeBin.is_online ? 'Online' : 'Offline'}</span>
              </span>
            </div>

            {/* Thông tin Địa điểm & Tọa độ */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '12.5px' }}>
              <div>
                <span style={{ color: '#64748b', fontWeight: 500 }}>📍 Vị trí: </span>
                <strong style={{ color: '#0f172a' }}>{activeBin.location || 'Chưa cập nhật'}</strong>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div>
                  <span style={{ color: '#64748b', fontWeight: 500 }}>🌐 Tọa độ: </span>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', color: '#334155' }}>
                    {activeBin.latitude ? `${Number(activeBin.latitude).toFixed(4)}, ${Number(activeBin.longitude).toFixed(4)}` : 'Chưa có tọa độ'}
                  </span>
                </div>
                {activeBin.latitude && (
                  <button
                    onClick={() => handleCopyCoords(activeBin.latitude, activeBin.longitude)}
                    style={{ background: 'none', border: 'none', color: copiedCoords ? '#16a34a' : '#64748b', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '11px' }}
                  >
                    {copiedCoords ? <Check size={13} /> : <Copy size={13} />}
                    <span>{copiedCoords ? 'Đã chép' : 'Sao chép'}</span>
                  </button>
                )}
              </div>
            </div>

            {/* Mức rác hiện tại (Fill Level Bar) */}
            <div style={{ backgroundColor: '#f8fafc', borderRadius: '12px', padding: '12px 14px', border: '1px solid #e2e8f0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: '6px' }}>
                  <span style={{ fontSize: '12px', fontWeight: 600, color: '#64748b' }}>Mức rác hiện tại:</span>
                  <strong style={{ fontSize: '18px', fontWeight: 800, color: statusColor }}>{activeLevel}%</strong>
                </div>
                <span style={{ 
                  fontSize: '11px', 
                  fontWeight: 700, 
                  color: statusColor, 
                  backgroundColor: '#ffffff', 
                  padding: '2px 8px', 
                  borderRadius: '6px', 
                  border: `1px solid ${statusColor}33` 
                }}>
                  {statusText}
                </span>
              </div>
              <div style={{ height: '8px', borderRadius: '9999px', backgroundColor: '#e2e8f0', overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${activeLevel}%`, borderRadius: '9999px', backgroundColor: statusColor, transition: 'width 300ms ease' }} />
              </div>
            </div>

            {/* Thông số vận hành 2x2 Grid gọn gàng (Chỉ có Action Button trực quan) */}
            <div style={{ 
              display: 'grid', 
              gridTemplateColumns: '1fr 1fr', 
              gap: '10px 14px', 
              padding: '12px 14px', 
              borderRadius: '12px', 
              backgroundColor: '#f8fafc', 
              fontSize: '12px', 
              border: '1px solid #e2e8f0' 
            }}>
              {/* Item 1: Trạng thái nắp (Chỉ có button) */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '6px' }}>
                <span style={{ color: '#64748b', fontSize: '11.5px', fontWeight: 500, whiteSpace: 'nowrap' }}>Trạng thái nắp:</span>
                {onSendCommand ? (
                  <button
                    onClick={() => handleBinCommand(activeBin.state === 'OPEN' ? 'CLOSE' : 'OPEN')}
                    disabled={commandInProgress}
                    style={{
                      background: activeBin.state === 'OPEN' ? '#fef2f2' : '#eff6ff',
                      border: `1px solid ${activeBin.state === 'OPEN' ? '#fecaca' : '#bfdbfe'}`,
                      borderRadius: '6px',
                      padding: '2px 8px',
                      fontSize: '11px',
                      fontWeight: 700,
                      color: activeBin.state === 'OPEN' ? '#dc2626' : '#2563eb',
                      cursor: commandInProgress ? 'not-allowed' : 'pointer',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px',
                      transition: 'all 150ms ease',
                      boxShadow: '0 1px 2px rgba(0,0,0,0.04)'
                    }}
                    title={activeBin.state === 'OPEN' ? 'Gửi lệnh ĐÓNG nắp tới ESP32' : 'Gửi lệnh MỞ nắp tới ESP32'}
                  >
                    {commandInProgress && <Loader2 size={10} className="spin-animation" />}
                    <span>{activeBin.state === 'OPEN' ? 'Đóng' : 'Mở'}</span>
                  </button>
                ) : (
                  <strong style={{ color: activeBin.state === 'OPEN' ? '#16a34a' : '#0f172a', fontSize: '12px' }}>
                    {activeBin.state === 'OPEN' ? 'Mở' : 'Đóng'}
                  </strong>
                )}
              </div>

              {/* Item 2: Chế độ hoạt động (Chỉ có button) */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '6px' }}>
                <span style={{ color: '#64748b', fontSize: '11.5px', fontWeight: 500, whiteSpace: 'nowrap' }}>Chế độ:</span>
                {onSendCommand ? (
                  <button
                    onClick={() => handleBinCommand(activeBin.control_mode === 'MANUAL' ? 'AUTO' : 'MANUAL')}
                    disabled={commandInProgress}
                    style={{
                      background: activeBin.control_mode === 'MANUAL' ? '#f0fdf4' : '#f0fdfa',
                      border: `1px solid ${activeBin.control_mode === 'MANUAL' ? '#bbf7d0' : '#99f6e4'}`,
                      borderRadius: '6px',
                      padding: '2px 8px',
                      fontSize: '11px',
                      fontWeight: 700,
                      color: activeBin.control_mode === 'MANUAL' ? '#16a34a' : '#0d9488',
                      cursor: commandInProgress ? 'not-allowed' : 'pointer',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px',
                      transition: 'all 150ms ease',
                      boxShadow: '0 1px 2px rgba(0,0,0,0.04)'
                    }}
                    title={activeBin.control_mode === 'MANUAL' ? 'Chuyển về chế độ Tự động (AUTO)' : 'Chuyển sang chế độ Thủ công (MANUAL)'}
                  >
                    {commandInProgress && <Loader2 size={10} className="spin-animation" />}
                    <span>{activeBin.control_mode === 'MANUAL' ? 'Tự động' : 'Thủ công'}</span>
                  </button>
                ) : (
                  <strong style={{ color: '#0f172a', fontSize: '12px' }}>
                    {activeBin.control_mode === 'MANUAL' ? 'Thủ công' : 'Tự động'}
                  </strong>
                )}
              </div>

              {/* Item 3: Thu gom */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '6px' }}>
                <span style={{ color: '#64748b', fontSize: '11.5px', fontWeight: 500 }}>Thu gom:</span>
                <strong style={{
                  color: binCollectionStatus?.color || '#0f172a',
                  fontSize: '12px',
                  lineHeight: '1.2'
                }}>
                  {binCollectionStatus?.text}
                </strong>
              </div>

              {/* Item 4: Cập nhật */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '6px' }}>
                <span style={{ color: '#64748b', fontSize: '11.5px', fontWeight: 500 }}>Cập nhật:</span>
                <span style={{ color: '#0f172a', fontFamily: 'var(--font-mono)', fontSize: '12px', fontWeight: 700 }}>
                  {formatLastSeen(activeBin.last_seen || activeBin.updated_at)}
                </span>
              </div>
            </div>

            {/* Action Buttons */}
            <div style={{ display: 'flex', gap: '8px', marginTop: '2px' }}>
              <button
                onClick={() => {
                  setEditingBin(activeBin);
                  setEditLat(activeBin.latitude || '');
                  setEditLng(activeBin.longitude || '');
                }}
                className="btn-ghost"
                style={{ flex: 1, padding: '9px', fontSize: '12px', borderRadius: '10px', justifyContent: 'center', backgroundColor: '#f1f5f9' }}
              >
                <Edit3 size={13} color="#64748b" />
                <span>Sửa tọa độ GPS</span>
              </button>

              <button
                onClick={() => handleOpenDispatchModal(null, activeBin)}
                className="btn-primary"
                style={{ flex: 1, padding: '9px', fontSize: '12px', borderRadius: '10px', justifyContent: 'center', backgroundColor: '#2563eb' }}
              >
                <Send size={13} />
                <span>Gán nhiệm vụ</span>
              </button>
            </div>
          </div>
        )}

        {/* ==================================================================== */}
        {/* 2. STATE: CLICK TRUCK (Chi tiết Xe thu gom & Tài xế)                 */}
        {/* ==================================================================== */}
        {panelMode === 'truck' && activeTruck && (
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '18px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 2px 8px rgba(0,0,0,0.03)',
            display: 'flex',
            flexDirection: 'column',
            gap: '14px'
          }}>
            {/* Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <span style={{ fontSize: '18px' }}>🚛</span>
                  <h3 style={{ fontSize: '16px', fontWeight: 800, color: '#0f172a', margin: 0 }}>
                    {activeTruck.truckId || 'XE THU GOM'}
                  </h3>
                </div>
                <span style={{ fontSize: '12px', color: '#2563eb', fontWeight: 700 }}>
                  {activeTruck.driverName || activeTruck.full_name || 'Tài xế'}
                </span>
              </div>

              <span style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '5px',
                padding: '3px 10px',
                borderRadius: '9999px',
                fontSize: '11px',
                fontWeight: 700,
                backgroundColor: '#ecfdf5',
                color: '#16a34a',
                border: '1px solid #bbf7d0'
              }}>
                <span style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#16a34a' }} />
                <span>Online</span>
              </span>
            </div>

            {/* Driver & Location Specs */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '12.5px' }}>
              <div>
                <span style={{ color: '#64748b', fontWeight: 500 }}>👤 Tài xế: </span>
                <strong style={{ color: '#0f172a' }}>{activeTruck.full_name || activeTruck.driverName}</strong>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div>
                  <span style={{ color: '#64748b', fontWeight: 500 }}>📍 Vị trí: </span>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', color: '#334155' }}>
                    {activeTruck.latitude ? `${Number(activeTruck.latitude).toFixed(4)}, ${Number(activeTruck.longitude).toFixed(4)}` : 'Chưa có GPS'}
                  </span>
                </div>
                {activeTruck.latitude && (
                  <button
                    onClick={() => handleCopyCoords(activeTruck.latitude, activeTruck.longitude)}
                    style={{ background: 'none', border: 'none', color: copiedCoords ? '#16a34a' : '#64748b', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '11px' }}
                  >
                    {copiedCoords ? <Check size={13} /> : <Copy size={13} />}
                    <span>{copiedCoords ? 'Đã chép' : 'Sao chép'}</span>
                  </button>
                )}
              </div>
            </div>

            {/* Telemetry Grid */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', padding: '10px', borderRadius: '10px', backgroundColor: '#f8fafc', fontSize: '11.5px', border: '1px solid #f1f5f9' }}>
              <div>
                <span style={{ color: '#64748b', fontSize: '11px', display: 'block', marginBottom: '2px' }}>Vận tốc xe:</span>
                <strong style={{ color: '#0f172a', fontSize: '12.5px' }}>
                  {activeTruck.speed ? `${(activeTruck.speed * 3.6).toFixed(0)} km/h` : '0 km/h'}
                </strong>
              </div>
              <div>
                <span style={{ color: '#64748b', fontSize: '11px', display: 'block', marginBottom: '2px' }}>Hướng di chuyển:</span>
                <strong style={{ color: '#0f172a', fontSize: '12.5px' }}>
                  {Number(activeTruck.heading || 0).toFixed(0)}°
                </strong>
              </div>
              <div>
                <span style={{ color: '#64748b', fontSize: '11px', display: 'block', marginBottom: '2px' }}>Nhiệm vụ:</span>
                <strong style={{ color: truckStatus.color, fontSize: '12.5px', lineHeight: '1.2', display: 'block' }}>
                  {truckStatus.badgeText}
                </strong>
              </div>
              <div>
                <span style={{ color: '#64748b', fontSize: '11px', display: 'block', marginBottom: '2px' }}>Cập nhật lần cuối:</span>
                <span style={{ color: '#0f172a', fontFamily: 'var(--font-mono)', fontSize: '12px', fontWeight: 700 }}>
                  {formatLastSeen(activeTruck.recorded_at)}
                </span>
              </div>
            </div>

            {/* Current Active Job Section with Route Metrics */}
            {activeTruck.currentJob ? (
              <div style={{ backgroundColor: '#eff6ff', borderRadius: '12px', padding: '14px', border: '1px solid #bfdbfe' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span style={{ fontSize: '15px' }}>📋</span>
                    <strong style={{ fontSize: '13px', color: '#1e3a8a' }}>
                      {activeTruck.currentJob.id}
                    </strong>
                  </div>
                  <span style={{
                    fontSize: '10.5px',
                    fontWeight: 700,
                    padding: '2px 8px',
                    borderRadius: '6px',
                    backgroundColor: getJobStatusStyle(activeTruck.currentJob.status).bg,
                    color: getJobStatusStyle(activeTruck.currentJob.status).color,
                    border: `1px solid ${getJobStatusStyle(activeTruck.currentJob.status).border}`
                  }}>
                    {getJobStatusLabel(activeTruck.currentJob.status)}
                  </span>
                </div>

                {/* Route Metrics Row */}
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, 1fr)',
                  gap: '6px',
                  backgroundColor: '#ffffff',
                  padding: '8px 10px',
                  borderRadius: '8px',
                  border: '1px solid #dbeafe',
                  marginBottom: '10px',
                  textAlign: 'center',
                  fontSize: '11px'
                }}>
                  <div>
                    <span style={{ color: '#64748b', fontSize: '10px', display: 'block' }}>Quãng đường</span>
                    <strong style={{ color: '#1e40af', fontSize: '12px' }}>
                      {routeInfo ? `${(routeInfo.distanceMeters / 1000).toFixed(1)} km` : '--'}
                    </strong>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', fontSize: '10px', display: 'block' }}>Thời gian</span>
                    <strong style={{ color: '#1e40af', fontSize: '12px' }}>
                      {routeInfo ? `~${Math.round(routeInfo.durationSeconds / 60)} min` : '--'}
                    </strong>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', fontSize: '10px', display: 'block' }}>Tiến độ</span>
                    <strong style={{ color: '#16a34a', fontSize: '12px' }}>
                      {getCompletedBinIds(activeTruck.currentJob).length} / {activeTruck.currentJob.target_bin_ids?.length || 0}
                    </strong>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '6px' }}>
                  <button
                    onClick={() => handleSelectJob(activeTruck.currentJob, true)}
                    className="btn-primary"
                    style={{ flex: 1, padding: '8px 10px', fontSize: '11.5px', borderRadius: '8px', backgroundColor: '#2563eb', justifyContent: 'center' }}
                  >
                    <FileText size={13} />
                    <span>Xem chi tiết nhiệm vụ</span>
                  </button>

                  <button
                    onClick={() => handleOpenReassignModal(activeTruck.currentJob)}
                    className="btn-ghost"
                    style={{ padding: '8px 10px', fontSize: '11.5px', borderRadius: '8px', backgroundColor: '#ffffff', border: '1px solid #cbd5e1', color: '#334155' }}
                    title="Điều chuyển cho xe khác"
                  >
                    <RefreshCw size={13} />
                    <span>Điều chuyển</span>
                  </button>
                </div>
              </div>
            ) : (
              <div style={{ padding: '14px', borderRadius: '12px', backgroundColor: '#f8fafc', border: '1px dashed #cbd5e1', textAlign: 'center' }}>
                <div style={{ fontSize: '12.5px', fontWeight: 700, color: '#334155', marginBottom: '4px' }}>
                  Xe đang chờ phân công
                </div>
                <p style={{ fontSize: '12px', color: '#64748b', margin: '0 0 10px 0', lineHeight: 1.4 }}>
                  Xe đang chờ việc, sẵn sàng tiếp nhận tuyến thu gom mới.
                </p>
                <button
                  onClick={() => handleOpenDispatchModal(activeTruck)}
                  className="btn-primary"
                  style={{ padding: '7px 14px', fontSize: '11.5px', borderRadius: '8px', backgroundColor: '#2563eb', margin: '0 auto', gap: '5px' }}
                >
                  <PlusCircle size={13} />
                  <span>Phân công nhiệm vụ</span>
                </button>
              </div>
            )}
          </div>
        )}

        {/* ==================================================================== */}
        {/* 3. STATE: CLICK JOB / ROUTE (Chi tiết Nhiệm vụ Thu gom)              */}
        {/* ==================================================================== */}
        {panelMode === 'job' && selectedJob && (
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '18px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 2px 8px rgba(0,0,0,0.03)',
            display: 'flex',
            flexDirection: 'column',
            gap: '14px'
          }}>
            {/* Header with Back button */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <button
                    onClick={() => {
                      if (selectedTruckId) setPanelMode('truck');
                      else if (selectedBinId) setPanelMode('bin');
                      else setPanelMode('bin');
                    }}
                    style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '2px', color: '#64748b', display: 'flex', alignItems: 'center' }}
                    title="Quay lại"
                  >
                    <ArrowLeft size={16} />
                  </button>
                  <h3 style={{ fontSize: '15px', fontWeight: 800, color: '#0f172a', margin: 0 }}>
                    {selectedJob.id}
                  </h3>
                </div>
                <span style={{ fontSize: '11px', color: '#64748b', marginLeft: '22px' }}>
                  Phụ trách: <strong>{selectedJob.employee_name}</strong>
                </span>
              </div>

              <span style={{
                display: 'inline-flex',
                alignItems: 'center',
                padding: '3px 9px',
                borderRadius: '6px',
                fontSize: '11px',
                fontWeight: 700,
                backgroundColor: getJobStatusStyle(selectedJob.status).bg,
                color: getJobStatusStyle(selectedJob.status).color,
                border: `1px solid ${getJobStatusStyle(selectedJob.status).border}`
              }}>
                {getJobStatusLabel(selectedJob.status)}
              </span>
            </div>

            {/* Route Stats 3-Column Metrics */}
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(3, 1fr)',
              gap: '6px',
              backgroundColor: '#eff6ff',
              padding: '10px',
              borderRadius: '10px',
              border: '1px solid #bfdbfe',
              textAlign: 'center',
              fontSize: '11.5px'
            }}>
              <div>
                <span style={{ color: '#64748b', fontSize: '10.5px', display: 'block', marginBottom: '1px' }}>Quãng đường</span>
                <strong style={{ color: '#1e40af', fontSize: '13px' }}>
                  {routeInfo ? `${(routeInfo.distanceMeters / 1000).toFixed(1)} km` : '--'}
                </strong>
              </div>
              <div>
                <span style={{ color: '#64748b', fontSize: '10.5px', display: 'block', marginBottom: '1px' }}>Thời gian</span>
                <strong style={{ color: '#1e40af', fontSize: '13px' }}>
                  {routeInfo ? `~${Math.round(routeInfo.durationSeconds / 60)} min` : '--'}
                </strong>
              </div>
              <div>
                <span style={{ color: '#64748b', fontSize: '10.5px', display: 'block', marginBottom: '1px' }}>Tiến độ</span>
                <strong style={{ color: '#16a34a', fontSize: '13px' }}>
                  {getCompletedBinIds(selectedJob).length} / {selectedJob.target_bin_ids?.length || 0} đã gom
                </strong>
              </div>
            </div>

            {/* Operator & Time Info */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', padding: '10px', borderRadius: '10px', backgroundColor: '#f8fafc', fontSize: '11.5px', border: '1px solid #f1f5f9' }}>
              <div>
                <span style={{ color: '#64748b', fontSize: '10.5px', display: 'block' }}>Khởi tạo:</span>
                <span style={{ color: '#334155' }}>{formatDateTime(selectedJob.created_at || selectedJob.assigned_at)}</span>
              </div>
              <div>
                <span style={{ color: '#64748b', fontSize: '10.5px', display: 'block' }}>Bắt đầu:</span>
                <span style={{ color: '#16a34a', fontWeight: 600 }}>{selectedJob.started_at ? formatDateTime(selectedJob.started_at) : 'Chưa bắt đầu'}</span>
              </div>
              {selectedJob.paused_at && (
                <div>
                  <span style={{ color: '#64748b', fontSize: '10.5px', display: 'block' }}>Tạm dừng lúc:</span>
                  <span style={{ color: '#d97706', fontWeight: 600 }}>{formatTime(selectedJob.paused_at)}</span>
                </div>
              )}
            </div>

            {/* Target Bins List in this Job */}
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                <span style={{ fontSize: '12px', fontWeight: 700, color: '#334155' }}>
                  Danh sách thùng trong tuyến ({selectedJob.target_bin_ids?.length || 0}):
                </span>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '5px', maxHeight: '180px', overflowY: 'auto' }}>
                {(selectedJob.target_bin_ids || []).map((binId, idx) => {
                  const bObj = (bins || []).find(b => b.device_id === binId);
                  const isDone = getCompletedBinIds(selectedJob).includes(binId);
                  const lvl = bObj ? Number(bObj.level_percent || 0) : 0;
                  const color = bObj ? getFillColor(lvl, bObj.is_online) : '#64748b';

                  return (
                    <div
                      key={binId}
                      onClick={() => handleSelectBin(binId, true)}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '6px 10px',
                        borderRadius: '8px',
                        backgroundColor: isDone ? '#f0fdf4' : '#f8fafc',
                        border: `1px solid ${isDone ? '#bbf7d0' : '#e2e8f0'}`,
                        cursor: 'pointer',
                        fontSize: '11.5px'
                      }}
                      title="Bấm để xem chi tiết thùng rác này"
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{
                          width: '18px',
                          height: '18px',
                          borderRadius: '50%',
                          backgroundColor: isDone ? '#16a34a' : '#1a73e8',
                          color: '#ffffff',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: '10px',
                          fontWeight: 800
                        }}>
                          {isDone ? '✓' : idx + 1}
                        </span>
                        <div>
                          <strong>{bObj?.name || binId}</strong>
                          <div style={{ fontSize: '10px', color: '#64748b' }}>{bObj?.location || 'Chưa cập nhật'}</div>
                        </div>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span style={{ fontWeight: 800, color }}>{lvl}%</span>
                        <span style={{ fontSize: '10px', fontWeight: 700, color: isDone ? '#16a34a' : '#2563eb' }}>
                          {isDone ? 'Đã gom' : 'Chờ gom'}
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Actions for Active Job */}
            <div style={{ display: 'flex', gap: '8px', marginTop: '4px' }}>
              <button
                onClick={() => handleOpenReassignModal(selectedJob)}
                className="btn-ghost"
                style={{ flex: 1, padding: '9px', fontSize: '11.5px', borderRadius: '10px', justifyContent: 'center', backgroundColor: '#f1f5f9' }}
              >
                <RefreshCw size={13} color="#2563eb" />
                <span>Điều chuyển</span>
              </button>

              <button
                onClick={() => handleCancelJob(selectedJob.id)}
                className="btn-ghost"
                style={{ flex: 1, padding: '9px', fontSize: '11.5px', borderRadius: '10px', justifyContent: 'center', backgroundColor: '#fef2f2', color: '#dc2626', border: '1px solid #fee2e2' }}
              >
                <X size={13} />
                <span>Hủy nhiệm vụ</span>
              </button>
            </div>
          </div>
        )}

        {/* ==================================================================== */}
        {/* BOTTOM TABBED LISTS: THÙNG RÁC / XE THU GOM / NHIỆM VỤ               */}
        {/* ==================================================================== */}
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          padding: '16px',
          border: '1px solid #e2e8f0',
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          minHeight: '260px'
        }}>
          {/* Top Segmented Tab Switcher */}
          <div style={{ display: 'flex', backgroundColor: '#f1f5f9', padding: '3px', borderRadius: '10px', marginBottom: '12px' }}>
            <button
              onClick={() => setListTab('bins')}
              style={{
                flex: 1,
                padding: '6px 8px',
                border: 'none',
                borderRadius: '8px',
                fontSize: '11.5px',
                fontWeight: 700,
                cursor: 'pointer',
                backgroundColor: listTab === 'bins' ? '#ffffff' : 'transparent',
                color: listTab === 'bins' ? '#0f172a' : '#64748b',
                boxShadow: listTab === 'bins' ? '0 1px 4px rgba(0,0,0,0.08)' : 'none',
                transition: 'all 150ms ease'
              }}
            >
              🗑️ Thùng ({bins?.length || 0})
            </button>

            <button
              onClick={() => setListTab('trucks')}
              style={{
                flex: 1,
                padding: '6px 8px',
                border: 'none',
                borderRadius: '8px',
                fontSize: '11.5px',
                fontWeight: 700,
                cursor: 'pointer',
                backgroundColor: listTab === 'trucks' ? '#ffffff' : 'transparent',
                color: listTab === 'trucks' ? '#0f172a' : '#64748b',
                boxShadow: listTab === 'trucks' ? '0 1px 4px rgba(0,0,0,0.08)' : 'none',
                transition: 'all 150ms ease'
              }}
            >
              🚛 Xe ({employees?.length || 0})
            </button>

            <button
              onClick={() => setListTab('jobs')}
              style={{
                flex: 1,
                padding: '6px 8px',
                border: 'none',
                borderRadius: '8px',
                fontSize: '11.5px',
                fontWeight: 700,
                cursor: 'pointer',
                backgroundColor: listTab === 'jobs' ? '#ffffff' : 'transparent',
                color: listTab === 'jobs' ? '#0f172a' : '#64748b',
                boxShadow: listTab === 'jobs' ? '0 1px 4px rgba(0,0,0,0.08)' : 'none',
                transition: 'all 150ms ease'
              }}
            >
              📋 Nhiệm vụ ({activeJobs?.length || 0})
            </button>
          </div>

          {/* TAB 1: BINS LIST (Sorted by % Descending) */}
          {listTab === 'bins' && (
            <>
              {/* Search input */}
              <div style={{ position: 'relative', marginBottom: '8px' }}>
                <Search size={14} color="#64748b" style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)' }} />
                <input
                  type="text"
                  placeholder="Tìm theo tên, mã hoặc vị trí..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '7px 10px 7px 30px',
                    borderRadius: '8px',
                    border: '1px solid #e2e8f0',
                    fontSize: '11.5px',
                    outline: 'none',
                    backgroundColor: '#f8fafc'
                  }}
                />
              </div>

              {/* Quick Filter Status Pills */}
              <div style={{ display: 'flex', gap: '5px', overflowX: 'auto', paddingBottom: '8px', marginBottom: '4px' }}>
                {[
                  { id: 'all', label: 'Tất cả' },
                  { id: 'overfull', label: 'Quá đầy (>85%)' },
                  { id: 'full', label: 'Đầy (70-85%)' },
                  { id: 'partial', label: 'Mức vừa (30-70%)' },
                  { id: 'empty', label: 'Trống (<30%)' },
                  { id: 'offline', label: 'Offline' }
                ].map(tab => (
                  <button
                    key={tab.id}
                    onClick={() => setFilterStatus(tab.id)}
                    style={{
                      padding: '3px 8px',
                      borderRadius: '9999px',
                      border: 'none',
                      fontSize: '10.5px',
                      fontWeight: 600,
                      cursor: 'pointer',
                      backgroundColor: filterStatus === tab.id ? '#2563eb' : '#f1f5f9',
                      color: filterStatus === tab.id ? '#ffffff' : '#64748b',
                      transition: 'all 150ms ease',
                      whiteSpace: 'nowrap'
                    }}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>

              {/* Bins Scroll List */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', overflowY: 'auto', flex: 1 }}>
                {filteredBins.map(bin => {
                  const level = Math.min(100, Math.max(0, Number(bin.level_percent || 0)));
                  const isSelected = panelMode === 'bin' && selectedBinId === bin.device_id;
                  const dotColor = getFillColor(level, bin.is_online);

                  return (
                    <div
                      key={bin.device_id}
                      onClick={() => handleSelectBin(bin, true)}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '8px 10px',
                        borderRadius: '10px',
                        backgroundColor: isSelected ? '#eff6ff' : '#f8fafc',
                        border: isSelected ? '1.5px solid #2563eb' : '1px solid #f1f5f9',
                        cursor: 'pointer',
                        transition: 'all 150ms ease'
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0 }}>
                        <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: dotColor, flexShrink: 0 }} />
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontSize: '12px', fontWeight: 700, color: '#111a4a', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                            {bin.name || bin.device_id}
                          </div>
                          <div style={{ fontSize: '10.5px', color: '#64748b', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                            {bin.location || 'Chưa có vị trí'}
                          </div>
                        </div>
                      </div>

                      <span style={{ fontSize: '12px', fontWeight: 800, color: dotColor, marginLeft: '8px', flexShrink: 0 }}>
                        {bin.is_online ? `${level}%` : 'TẮT'}
                      </span>
                    </div>
                  );
                })}
              </div>
            </>
          )}

          {/* TAB 2: TRUCKS LIST */}
          {listTab === 'trucks' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', overflowY: 'auto', flex: 1 }}>
              {(employees || []).map((emp, index) => {
                const empId = String(emp.employee_id || emp.id || '').toLowerCase();
                const currentJob = activeJobs.find(j =>
                  String(j.employee_id).toLowerCase() === empId &&
                  ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'PAUSED'].includes(j.status)
                );
                const isCollecting = Boolean(currentJob);
                const truckId = getStableTruckId(emp, index);
                const driverName = emp.full_name || emp.username || `Tài xế ${index + 1}`;
                const truckStatusObj = getTruckStatusInfo(currentJob);
                const isSelected = panelMode === 'truck' && selectedTruckId === (emp.employee_id || emp.id);

                return (
                  <div
                    key={emp.employee_id || emp.id}
                    onClick={() => handleSelectTruck(emp, true)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '9px 10px',
                      borderRadius: '10px',
                      backgroundColor: isSelected ? '#eff6ff' : '#f8fafc',
                      border: isSelected ? '1.5px solid #2563eb' : '1px solid #f1f5f9',
                      cursor: 'pointer',
                      transition: 'all 150ms ease'
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0 }}>
                      <div style={{
                        width: '28px',
                        height: '28px',
                        borderRadius: '50%',
                        backgroundColor: truckStatusObj.markerBg,
                        color: '#ffffff',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0
                      }}>
                        <Truck size={14} />
                      </div>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontSize: '12px', fontWeight: 700, color: '#0f172a', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                          {truckId} · {driverName}
                        </div>
                        <div style={{ fontSize: '10.5px', color: truckStatusObj.color }}>
                          {isCollecting ? `${truckStatusObj.text} (${currentJob.target_bin_ids?.length || 0} thùng)` : 'Đang chờ việc'}
                        </div>
                      </div>
                    </div>

                    <span style={{
                      fontSize: '10.5px',
                      fontWeight: 700,
                      padding: '2px 7px',
                      borderRadius: '6px',
                      backgroundColor: truckStatusObj.bg,
                      color: truckStatusObj.color,
                      border: `1px solid ${truckStatusObj.border}`
                    }}>
                      {truckStatusObj.text}
                    </span>
                  </div>
                );
              })}
            </div>
          )}

          {/* TAB 3: ACTIVE JOBS LIST */}
          {listTab === 'jobs' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', overflowY: 'auto', flex: 1 }}>
              {activeJobs.length === 0 ? (
                <div style={{ padding: '24px 12px', textAlign: 'center', color: '#94a3b8', fontSize: '12px' }}>
                  Không có nhiệm vụ nào đang hoạt động.
                </div>
              ) : (
                activeJobs.map(job => {
                  const isSelected = panelMode === 'job' && selectedJobId === job.id;
                  const st = getJobStatusStyle(job.status);
                  return (
                    <div
                      key={job.id}
                      onClick={() => handleSelectJob(job, true)}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '9px 10px',
                        borderRadius: '10px',
                        backgroundColor: isSelected ? '#eff6ff' : '#f8fafc',
                        border: isSelected ? '1.5px solid #2563eb' : '1px solid #f1f5f9',
                        cursor: 'pointer',
                        transition: 'all 150ms ease'
                      }}
                    >
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontSize: '12px', fontWeight: 700, color: '#0f172a' }}>
                          {job.id}
                        </div>
                        <div style={{ fontSize: '10.5px', color: '#64748b' }}>
                          👤 {job.employee_name} · <strong>{job.target_bin_ids?.length || 0} thùng</strong>
                        </div>
                      </div>

                      <span style={{
                        fontSize: '10.5px',
                        fontWeight: 700,
                        padding: '2px 7px',
                        borderRadius: '6px',
                        backgroundColor: st.bg,
                        color: st.color,
                        border: `1px solid ${st.border}`
                      }}>
                        {getJobStatusLabel(job.status)}
                      </span>
                    </div>
                  );
                })
              )}
            </div>
          )}
        </div>

      </div>

      {/* QUICK DISPATCH MODAL (ADMIN GÁN VIỆC) */}
      {dispatchModalOpen && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.5)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1200
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '24px',
            width: '540px',
            maxWidth: '92vw',
            boxShadow: '0 20px 40px rgba(0,0,0,0.2)',
            maxHeight: '88vh',
            display: 'flex',
            flexDirection: 'column'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <div style={{ width: '32px', height: '32px', borderRadius: '8px', backgroundColor: '#eff6ff', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#2563eb' }}>
                  <Send size={18} />
                </div>
                <h3 style={{ fontSize: '16px', fontWeight: 800, color: '#111a4a', margin: 0 }}>
                  Tạo Nhiệm vụ Thu gom
                </h3>
              </div>
              <button onClick={() => setDispatchModalOpen(false)} style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer' }}>
                <X size={18} />
              </button>
            </div>

            <p style={{ fontSize: '12.5px', color: '#64748b', margin: '0 0 16px 0' }}>
              Chọn xe và mục tiêu để hệ thống tự động tính toán lộ trình OSRM tối ưu.
            </p>

            <div style={{ overflowY: 'auto', flex: 1, display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {/* Chọn Nhân viên / Xe */}
              <div>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, color: '#334155', marginBottom: '6px' }}>
                  1. Chọn Nhân viên / Xe phụ trách:
                </label>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: '8px' }}>
                  {(employees || []).map((emp, idx) => {
                    const empId = emp.employee_id || emp.id;
                    const isSelected = (dispatchTruck?.employee_id || dispatchTruck?.id) === empId;
                    const currentJob = activeJobs.find(j => String(j.employee_id).toLowerCase() === String(empId).toLowerCase() && ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'PAUSED'].includes(j.status));
                    const isBusy = Boolean(currentJob);
                    const truckSt = getTruckStatusInfo(currentJob);

                    return (
                      <div
                        key={empId}
                        onClick={() => {
                          if (!isBusy) setDispatchTruck(emp);
                          else onNotify(`${emp.full_name || 'Nhân viên'} đang bận làm nhiệm vụ khác!`, 'warning');
                        }}
                        style={{
                          padding: '10px',
                          borderRadius: '10px',
                          border: `1.5px solid ${isSelected ? '#2563eb' : isBusy ? '#fecaca' : '#e2e8f0'}`,
                          backgroundColor: isSelected ? '#eff6ff' : isBusy ? '#fff7ed' : '#ffffff',
                          cursor: isBusy ? 'not-allowed' : 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          gap: '8px',
                          opacity: isBusy ? 0.75 : 1
                        }}
                      >
                        <Truck size={18} color={isSelected ? '#2563eb' : isBusy ? '#ea580c' : '#64748b'} />
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontSize: '12px', fontWeight: 700, color: '#0f172a', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {emp.full_name || emp.username || `Tài xế ${idx + 1}`}
                          </div>
                          <div style={{ fontSize: '10px', color: truckSt.color, fontWeight: 600 }}>
                            {isBusy ? 'Đang bận' : truckSt.text}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Chọn các thùng rác */}
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                  <label style={{ fontSize: '12px', fontWeight: 700, color: '#334155' }}>
                    2. Chọn thùng rác cần gom ({selectedBinIdsForDispatch.length} đã chọn):
                  </label>
                  <div style={{ display: 'flex', gap: '6px' }}>
                    <button
                      type="button"
                      onClick={() => {
                        const urgent = (bins || []).filter(b => (b.level_percent || 0) >= 85).map(b => b.device_id);
                        setSelectedBinIdsForDispatch(urgent);
                      }}
                      style={{ background: '#fee2e2', border: 'none', color: '#dc2626', fontSize: '10.5px', fontWeight: 700, padding: '3px 8px', borderRadius: '6px', cursor: 'pointer' }}
                    >
                      Chọn tất cả &gt;85%
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        const warn = (bins || []).filter(b => (b.level_percent || 0) >= 70).map(b => b.device_id);
                        setSelectedBinIdsForDispatch(warn);
                      }}
                      style={{ background: '#fef3c7', border: 'none', color: '#d97706', fontSize: '10.5px', fontWeight: 700, padding: '3px 8px', borderRadius: '6px', cursor: 'pointer' }}
                    >
                      Chọn &ge;70%
                    </button>
                  </div>
                </div>

                <div style={{ maxHeight: '180px', overflowY: 'auto', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '6px' }}>
                  {(bins || []).map(b => {
                    const isChecked = selectedBinIdsForDispatch.includes(b.device_id);
                    const lvl = Number(b.level_percent || 0);
                    const color = getFillColor(lvl, b.is_online);

                    return (
                      <div
                        key={b.device_id}
                        onClick={() => {
                          setSelectedBinIdsForDispatch(prev =>
                            isChecked ? prev.filter(id => id !== b.device_id) : [...prev, b.device_id]
                          );
                        }}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '6px 10px',
                          borderRadius: '8px',
                          backgroundColor: isChecked ? '#eff6ff' : 'transparent',
                          cursor: 'pointer',
                          fontSize: '12px'
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() => { }}
                            style={{ accentColor: '#2563eb', cursor: 'pointer' }}
                          />
                          <div>
                            <strong>{b.name || b.device_id}</strong>
                            <div style={{ fontSize: '10.5px', color: '#64748b' }}>{b.location || 'Chưa cập nhật địa chỉ'}</div>
                          </div>
                        </div>
                        <span style={{ fontWeight: 800, color }}>{lvl}%</span>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px', paddingTop: '12px', borderTop: '1px solid #f1f5f9' }}>
              <button
                type="button"
                onClick={() => setDispatchModalOpen(false)}
                style={{ padding: '8px 14px', borderRadius: '8px', border: '1px solid #cbd5e1', backgroundColor: '#ffffff', fontSize: '12px', fontWeight: 600, color: '#64748b', cursor: 'pointer' }}
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={handleSubmitDispatch}
                disabled={dispatching || selectedBinIdsForDispatch.length === 0}
                className="btn-primary"
                style={{ padding: '8px 18px', fontSize: '12px', borderRadius: '8px', backgroundColor: '#2563eb', gap: '6px' }}
              >
                <Send size={13} />
                <span>{dispatching ? 'Đang tạo nhiệm vụ...' : 'Gán nhiệm vụ & Tính tuyến'}</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* REASSIGN MODAL (ĐIỀU CHUYỂN NHIỆM VỤ SANG XE KHÁC) */}
      {reassignModalOpen && reassigningJob && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.5)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1200
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            padding: '24px',
            width: '480px',
            maxWidth: '92vw',
            boxShadow: '0 20px 40px rgba(0,0,0,0.2)',
            display: 'flex',
            flexDirection: 'column'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <div style={{ width: '32px', height: '32px', borderRadius: '8px', backgroundColor: '#eff6ff', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#2563eb' }}>
                  <RefreshCw size={18} />
                </div>
                <h3 style={{ fontSize: '16px', fontWeight: 800, color: '#111a4a', margin: 0 }}>
                  Điều chuyển Tuyến thu gom
                </h3>
              </div>
              <button onClick={() => setReassignModalOpen(false)} style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer' }}>
                <X size={18} />
              </button>
            </div>

            <p style={{ fontSize: '12.5px', color: '#64748b', margin: '0 0 14px 0' }}>
              Chuyển giao các thùng rác còn lại của nhiệm vụ <strong>{reassigningJob.id}</strong> (hiện do <strong>{reassigningJob.employee_name}</strong> phụ trách) sang tài xế mới.
            </p>

            <div style={{ marginBottom: '16px' }}>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, color: '#334155', marginBottom: '6px' }}>
                Chọn Tài xế / Xe tiếp nhận:
              </label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', maxHeight: '200px', overflowY: 'auto' }}>
                {(employees || [])
                  .filter(e => {
                    const eId = String(e.employee_id || e.id).toLowerCase();
                    const isCurrent = eId === String(reassigningJob.employee_id).toLowerCase();
                    const isBusy = activeJobs.some(j => j.id !== reassigningJob.id && String(j.employee_id).toLowerCase() === eId && ['ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'PAUSED'].includes(j.status));
                    return !isCurrent && !isBusy;
                  })
                  .map(emp => {
                    const empId = emp.employee_id || emp.id;
                    const isSelected = (reassignTargetTruck?.employee_id || reassignTargetTruck?.id) === empId;

                    return (
                      <div
                        key={empId}
                        onClick={() => setReassignTargetTruck(emp)}
                        style={{
                          padding: '10px 12px',
                          borderRadius: '10px',
                          border: `1.5px solid ${isSelected ? '#2563eb' : '#e2e8f0'}`,
                          backgroundColor: isSelected ? '#eff6ff' : '#ffffff',
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between'
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <Truck size={16} color={isSelected ? '#2563eb' : '#64748b'} />
                          <strong style={{ fontSize: '12.5px', color: '#0f172a' }}>{emp.full_name || emp.username || 'Nhân viên'}</strong>
                        </div>
                        <span style={{ fontSize: '11px', color: '#16a34a', fontWeight: 600 }}>Đang chờ việc</span>
                      </div>
                    );
                  })}
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', paddingTop: '12px', borderTop: '1px solid #f1f5f9' }}>
              <button
                type="button"
                onClick={() => setReassignModalOpen(false)}
                style={{ padding: '8px 14px', borderRadius: '8px', border: '1px solid #cbd5e1', backgroundColor: '#ffffff', fontSize: '12px', fontWeight: 600, color: '#64748b', cursor: 'pointer' }}
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={handleSubmitReassign}
                disabled={reassigning || !reassignTargetTruck}
                className="btn-primary"
                style={{ padding: '8px 18px', fontSize: '12px', borderRadius: '8px', backgroundColor: '#2563eb', gap: '6px' }}
              >
                <RefreshCw size={13} />
                <span>{reassigning ? 'Đang chuyển giao...' : 'Xác nhận Điều chuyển'}</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Edit Location GPS Modal */}
      {editingBin && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.5)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1100
        }}>
          <div style={{ backgroundColor: '#ffffff', borderRadius: '16px', padding: '24px', width: '420px', maxWidth: '92vw', boxShadow: '0 20px 40px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: 800, color: '#111a4a', margin: 0 }}>Chỉnh sửa Vị trí GPS</h3>
              <button onClick={() => setEditingBin(null)} style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer' }}><X size={18} /></button>
            </div>

            <p style={{ fontSize: '12.5px', color: '#64748b', marginBottom: '16px' }}>Cập nhật tọa độ cho <strong>{editingBin.name || editingBin.device_id}</strong></p>

            <form onSubmit={handleSaveCoordinates}>
              <div style={{ marginBottom: '12px' }}>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, color: '#334155', marginBottom: '4px' }}>Vĩ độ (Latitude)</label>
                <input type="number" step="any" required placeholder="VD: 10.7769" value={editLat} onChange={(e) => setEditLat(e.target.value)} style={{ width: '100%', padding: '10px 12px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '13px', outline: 'none' }} />
              </div>

              <div style={{ marginBottom: '20px' }}>
                <label style={{ display: 'block', fontSize: '12px', fontWeight: 700, color: '#334155', marginBottom: '4px' }}>Kinh độ (Longitude)</label>
                <input type="number" step="any" required placeholder="VD: 106.7009" value={editLng} onChange={(e) => setEditLng(e.target.value)} style={{ width: '100%', padding: '10px 12px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '13px', outline: 'none' }} />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                <button type="button" onClick={() => setEditingBin(null)} style={{ padding: '8px 14px', borderRadius: '8px', border: '1px solid #cbd5e1', backgroundColor: '#ffffff', fontSize: '12px', fontWeight: 600, color: '#64748b', cursor: 'pointer' }}>Hủy</button>
                <button type="submit" disabled={savingCoords} className="btn-primary" style={{ padding: '8px 16px', fontSize: '12px', borderRadius: '8px', backgroundColor: '#2563eb' }}>{savingCoords ? 'Đang lưu...' : 'Lưu tọa độ'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Global Style overrides */}
      <style>{`
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(-6px); }
          to { opacity: 1; transform: translateY(0); }
        }

        @keyframes grabRouteDash {
          from {
            stroke-dashoffset: 44;
          }
          to {
            stroke-dashoffset: 0;
          }
        }

        .grab-route-flow {
          animation: grabRouteDash 1.2s linear infinite !important;
        }

        @media (max-width: 1024px) {
          .map-page-grid {
            grid-template-columns: 1fr !important;
            height: auto !important;
          }
        }
      `}</style>

    </div>
  );
}
