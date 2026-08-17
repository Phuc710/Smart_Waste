import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { 
  Users, 
  UserPlus, 
  Trash2, 
  Shield, 
  Camera, 
  Image as ImageIcon, 
  Check, 
  X, 
  Lock, 
  Unlock,
  User,
  Search,
  CheckCircle2,
  AlertTriangle,
  Clock,
  MapPin,
  RefreshCw,
  Eye,
  Radio,
  Wifi,
  WifiOff,
  UserCheck,
  ChevronLeft,
  ChevronRight,
  Edit3
} from 'lucide-react';
import { api } from '../services/api';
import { getSocket } from '../services/socket';
import { formatVietnamDateTime } from '../utils/dateTime';

let employeesCache = {
  employees: [],
  onlineStaffMap: new Map(),
  allIncidents: [],
  lastFetched: 0
};

export default function EmployeesPage({ currentUser, onNotify, onUpdateCurrentUser }) {
  // Navigation View Tab: 'directory' | 'incidents'
  const [activeTab, setActiveTab] = useState('directory');

  // Employee Data (Instant from Cache)
  const [employees, setEmployees] = useState(employeesCache.employees);
  const [onlineStaffMap, setOnlineStaffMap] = useState(employeesCache.onlineStaffMap);
  const [loading, setLoading] = useState(employeesCache.lastFetched === 0);
  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('all'); // 'all' | 'admin' | 'staff' | 'online' | 'locked'
  
  // Create Employee Form Modal
  const [showAddModal, setShowAddModal] = useState(false);
  const [fullName, setFullName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('staff');
  const [creating, setCreating] = useState(false);

  // Edit Employee Form Modal
  const [editModalEmployee, setEditModalEmployee] = useState(null);
  const [editFullName, setEditFullName] = useState('');
  const [editPassword, setEditPassword] = useState('');
  const [editRole, setEditRole] = useState('staff');
  const [updating, setUpdating] = useState(false);

  // Incidents Data (Instant from Cache)
  const [allIncidents, setAllIncidents] = useState(employeesCache.allIncidents);
  const [loadingIncidents, setLoadingIncidents] = useState(false);
  const [incidentStatusFilter, setIncidentStatusFilter] = useState('all'); // 'all' | 'new' | 'resolved'
  const [viewingStaffIncidents, setViewingStaffIncidents] = useState(null);
  const [selectedProofImage, setSelectedProofImage] = useState(null);
  const [imageCaption, setImageCaption] = useState('');
  const [resolvingId, setResolvingId] = useState(null);

  // Fetch Employees List & Live Map Locations (SWR)
  const fetchEmployees = useCallback(async (isSilent = false) => {
    if (!isSilent && employeesCache.lastFetched === 0) setLoading(true);
    try {
      const [empData, locData] = await Promise.all([
        api.getEmployees().catch(() => []),
        api.getMapLocations().catch(() => [])
      ]);

      const validEmps = Array.isArray(empData) ? empData : [];
      setEmployees(validEmps);

      const m = new Map();
      if (Array.isArray(locData)) {
        locData.forEach(loc => {
          const id = String(loc.employee_id || loc.id || '').toLowerCase();
          if (id) m.set(id, loc);
        });
        setOnlineStaffMap(m);
      }

      employeesCache = {
        ...employeesCache,
        employees: validEmps,
        onlineStaffMap: m,
        lastFetched: Date.now()
      };
    } catch (err) {
      if (onNotify) onNotify(`Không thể tải danh sách nhân sự: ${err.message}`, 'error');
    } finally {
      setLoading(false);
    }
  }, [onNotify]);

  // Fetch Global Incidents (SWR)
  const fetchIncidents = useCallback(async (isSilent = false) => {
    if (!isSilent && employeesCache.allIncidents.length === 0) setLoadingIncidents(true);
    try {
      const res = await api.getAllIncidents();
      if (res && Array.isArray(res.reports)) {
        setAllIncidents(res.reports);
        employeesCache = {
          ...employeesCache,
          allIncidents: res.reports
        };
      }
    } catch (err) {
      console.warn('[EmployeesPage] Error loading incidents:', err);
    } finally {
      setLoadingIncidents(false);
    }
  }, []);

  useEffect(() => {
    fetchEmployees(employeesCache.lastFetched > 0);
    fetchIncidents(employeesCache.allIncidents.length > 0);
  }, [fetchEmployees, fetchIncidents]);

  // Live Socket Listener for Mobile App Online status
  useEffect(() => {
    const socket = getSocket();
    if (!socket) return;

    const onEmployeeLocation = (loc) => {
      if (!loc || (!loc.employee_id && !loc.id)) return;
      const id = String(loc.employee_id || loc.id).toLowerCase();
      setOnlineStaffMap(prev => {
        const next = new Map(prev);
        next.set(id, { ...loc, recorded_at: new Date().toISOString() });
        return next;
      });
    };

    socket.on('employeeLocation', onEmployeeLocation);
    return () => {
      socket.off('employeeLocation', onEmployeeLocation);
    };
  }, []);

  // Check if staff is currently online on Mobile App (has active GPS within configured timeout)
  const isStaffOnline = (emp) => {
    if (!emp) return false;
    const id = String(emp.id || '').toLowerCase();
    const loc = onlineStaffMap.get(id);
    if (loc && loc.is_online !== undefined) return Boolean(loc.is_online);
    if (emp.is_online !== undefined) return Boolean(emp.is_online);
    if (!loc || !loc.recorded_at) return false;
    const diffSec = (Date.now() - new Date(loc.recorded_at).getTime()) / 1000;
    return diffSec < 120; // 120s timeout standard
  };

  // Toggle Account Active / Locked State
  const handleToggleActive = async (emp) => {
    const updatedStatus = !emp.is_active;
    try {
      await api.setEmployeeActive(emp.id, updatedStatus);
      if (onNotify) {
        onNotify(`Đã ${updatedStatus ? 'kích hoạt' : 'khóa'} tài khoản ${emp.username}.`, 'success');
      }
      setEmployees(prev => prev.map(item => item.id === emp.id ? { ...item, is_active: updatedStatus } : item));
    } catch (err) {
      if (onNotify) onNotify(`Lỗi cập nhật trạng thái: ${err.message}`, 'error');
    }
  };

  // Delete Employee
  const handleDelete = async (emp) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa tài khoản "${emp.full_name || emp.username}"?`)) {
      return;
    }

    try {
      await api.deleteEmployee(emp.id);
      if (onNotify) onNotify(`Đã xóa tài khoản ${emp.username}.`, 'success');
      setEmployees(prev => prev.filter(item => item.id !== emp.id));
    } catch (err) {
      if (onNotify) onNotify(`Lỗi khi xóa: ${err.message}`, 'error');
    }
  };

  // Create Employee
  const handleCreateEmployee = async (e) => {
    e.preventDefault();
    setCreating(true);
    try {
      const generatedEmail = `${username.trim().toLowerCase()}@smartwaste.vn`;
      await api.createEmployee({ 
        fullName: fullName.trim(), 
        username: username.trim().toLowerCase(), 
        email: generatedEmail, 
        password, 
        role 
      });
      if (onNotify) onNotify(`Đã tạo tài khoản ${username} thành công!`, 'success');
      setShowAddModal(false);
      setFullName('');
      setUsername('');
      setPassword('');
      setRole('staff');
      fetchEmployees();
    } catch (err) {
      if (onNotify) onNotify(`Không tạo được nhân viên: ${err.message}`, 'error');
    } finally {
      setCreating(false);
    }
  };

  // Open Edit Modal
  const handleOpenEdit = (emp) => {
    setEditModalEmployee(emp);
    setEditFullName(emp.full_name || '');
    setEditPassword('');
    setEditRole(emp.role || 'staff');
  };

  // Save Edit Employee
  const handleSaveEdit = async (e) => {
    e.preventDefault();
    if (!editModalEmployee) return;
    if (!editFullName.trim()) {
      if (onNotify) onNotify('Họ và tên không được để trống.', 'error');
      return;
    }
    setUpdating(true);
    try {
      const payload = {
        fullName: editFullName.trim(),
        role: editRole
      };
      if (editPassword.trim()) {
        payload.password = editPassword.trim();
      }
      await api.updateEmployee(editModalEmployee.id, payload);
      if (onNotify) onNotify(`Đã cập nhật thông tin "${editFullName.trim()}" thành công!`, 'success');

      setEmployees(prev => prev.map(item => item.id === editModalEmployee.id ? { 
        ...item, 
        full_name: editFullName.trim(), 
        role: editRole 
      } : item));

      if (editModalEmployee.id === currentUser?.id || editModalEmployee.username === currentUser?.username) {
        if (onUpdateCurrentUser) {
          onUpdateCurrentUser({ full_name: editFullName.trim(), role: editRole });
        }
      }

      setEditModalEmployee(null);
    } catch (err) {
      if (onNotify) onNotify(`Lỗi cập nhật: ${err.message}`, 'error');
    } finally {
      setUpdating(false);
    }
  };

  // Update Incident Status
  const handleToggleIncidentStatus = async (report) => {
    const isCurrentlyResolved = String(report.status || '').toUpperCase() === 'RESOLVED';
    const nextStatus = isCurrentlyResolved ? 'NEW' : 'RESOLVED';
    setResolvingId(report.id);
    try {
      await api.updateIncidentStatus(report.id, nextStatus);
      if (onNotify) {
        onNotify(nextStatus === 'RESOLVED' ? 'Đã giải quyết sự cố thành công!' : 'Đã mở lại báo cáo sự cố.', 'success');
      }
      setAllIncidents(prev => prev.map(r => r.id === report.id ? { 
        ...r, 
        status: nextStatus, 
        resolved_at: nextStatus === 'RESOLVED' ? new Date().toISOString() : null 
      } : r));
    } catch (err) {
      if (onNotify) onNotify(`Lỗi cập nhật: ${err.message}`, 'error');
    } finally {
      setResolvingId(null);
    }
  };

  // Metrics
  const totalStaffCount = employees.length;
  const onlineCount = employees.filter(e => isStaffOnline(e)).length;
  const adminCount = employees.filter(e => e.role === 'admin').length;
  const staffCount = employees.filter(e => e.role === 'staff').length;

  const totalIncidentsCount = allIncidents.length;
  const pendingIncidentsCount = allIncidents.filter(r => String(r.status || '').toUpperCase() !== 'RESOLVED').length;
  const resolvedIncidentsCount = allIncidents.filter(r => String(r.status || '').toUpperCase() === 'RESOLVED').length;

  // Filtered Employees List
  const filteredEmployees = useMemo(() => {
    return employees.filter(emp => {
      const q = searchQuery.toLowerCase().trim();
      const matchSearch = !q || 
        (emp.full_name && emp.full_name.toLowerCase().includes(q)) ||
        (emp.username && emp.username.toLowerCase().includes(q));

      if (!matchSearch) return false;

      if (roleFilter === 'admin') return emp.role === 'admin';
      if (roleFilter === 'staff') return emp.role === 'staff';
      if (roleFilter === 'online') return isStaffOnline(emp);
      if (roleFilter === 'locked') return emp.is_active === false;

      return true;
    });
  }, [employees, searchQuery, roleFilter, onlineStaffMap]);

  // Filtered Incidents List
  const filteredIncidents = useMemo(() => {
    return allIncidents.filter(rep => {
      const q = searchQuery.toLowerCase().trim();
      const matchSearch = !q ||
        (rep.device_id && rep.device_id.toLowerCase().includes(q)) ||
        (rep.bin_name && rep.bin_name.toLowerCase().includes(q)) ||
        (rep.employee_name && rep.employee_name.toLowerCase().includes(q)) ||
        (rep.reason && rep.reason.toLowerCase().includes(q)) ||
        (rep.description && rep.description.toLowerCase().includes(q));

      if (!matchSearch) return false;

      if (viewingStaffIncidents) {
        if (rep.employee_id !== viewingStaffIncidents.id) return false;
      }

      if (incidentStatusFilter === 'new') return String(rep.status || '').toUpperCase() !== 'RESOLVED';
      if (incidentStatusFilter === 'resolved') return String(rep.status || '').toUpperCase() === 'RESOLVED';

      return true;
    });
  }, [allIncidents, searchQuery, viewingStaffIncidents, incidentStatusFilter]);

  // Pagination State for Employees Tab
  const [staffPage, setStaffPage] = useState(1);
  const [staffPageSize, setStaffPageSize] = useState(10);

  // Pagination State for Incidents Tab
  const [incidentPage, setIncidentPage] = useState(1);
  const [incidentPageSize, setIncidentPageSize] = useState(6);

  // Reset pagination on filter changes
  useEffect(() => {
    setStaffPage(1);
  }, [searchQuery, roleFilter]);

  useEffect(() => {
    setIncidentPage(1);
  }, [searchQuery, incidentStatusFilter, viewingStaffIncidents]);

  const staffTotalPages = Math.max(1, Math.ceil(filteredEmployees.length / staffPageSize));
  const paginatedEmployees = useMemo(() => {
    return filteredEmployees.slice((staffPage - 1) * staffPageSize, staffPage * staffPageSize);
  }, [filteredEmployees, staffPage, staffPageSize]);

  const incidentTotalPages = Math.max(1, Math.ceil(filteredIncidents.length / incidentPageSize));
  const paginatedIncidents = useMemo(() => {
    return filteredIncidents.slice((incidentPage - 1) * incidentPageSize, incidentPage * incidentPageSize);
  }, [filteredIncidents, incidentPage, incidentPageSize]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px', paddingBottom: '36px' }}>
      
      {/* 1. Page Header & Action Controls */}
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
            <span>Quản lý Nhân sự & Báo cáo Sự cố</span>
            <span style={{ fontSize: '18px' }}>🛡️</span>
          </h1>
          <p style={{ fontSize: '13px', color: '#64748b', margin: 0 }}>
            Giám sát trạng thái Online ứng dụng, tài khoản nhân viên và xử lý báo cáo hình ảnh từ hiện trường.
          </p>
        </div>

        {/* Action Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button
            onClick={() => {
              fetchEmployees();
              fetchIncidents();
            }}
            title="Làm mới dữ liệu"
            style={{
              padding: '8px 14px',
              borderRadius: '10px',
              backgroundColor: '#ffffff',
              border: '1px solid #e2e8f0',
              color: '#334155',
              fontSize: '12.5px',
              fontWeight: 600,
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              cursor: 'pointer',
              boxShadow: '0 1px 3px rgba(0,0,0,0.03)'
            }}
          >
            <RefreshCw size={13} className={loading || loadingIncidents ? 'spin-animation' : ''} color="#64748b" />
            <span>Làm mới</span>
          </button>

          <button
            onClick={() => setShowAddModal(true)}
            style={{
              padding: '8px 16px',
              borderRadius: '10px',
              backgroundColor: '#10b981',
              color: '#ffffff',
              border: 'none',
              fontSize: '13px',
              fontWeight: 700,
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              cursor: 'pointer',
              boxShadow: '0 2px 6px rgba(16, 185, 129, 0.25)',
              transition: 'background-color 150ms ease'
            }}
            onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#059669'; }}
            onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#10b981'; }}
          >
            <UserPlus size={15} />
            <span>Thêm nhân viên mới</span>
          </button>
        </div>
      </div>

      {/* 2. Top Summary KPI Cards (Centered & Balanced) */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
        gap: '14px'
      }}>
        
        {/* Card 1: Tổng Nhân sự & Online */}
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
            <Users size={20} color="#10b981" />
          </div>
          <div>
            <span style={{ fontSize: '11.5px', fontWeight: 600, color: '#64748b' }}>Tổng nhân sự</span>
            <div style={{ fontSize: '22px', fontWeight: 800, color: '#111a4a', lineHeight: 1.1 }}>
              {totalStaffCount}
            </div>
            <div style={{ fontSize: '11px', color: '#10b981', fontWeight: 700, marginTop: '2px', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <span style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#10b981' }} />
              <span>{onlineCount} Online App</span>
            </div>
          </div>
        </div>

        {/* Card 2: Quản trị viên */}
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
            backgroundColor: '#fef2f2',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0
          }}>
            <Shield size={20} color="#ef4444" />
          </div>
          <div>
            <span style={{ fontSize: '11.5px', fontWeight: 600, color: '#64748b' }}>Quản trị viên (Admin)</span>
            <div style={{ fontSize: '22px', fontWeight: 800, color: '#111a4a', lineHeight: 1.1 }}>
              {adminCount}
            </div>
            <div style={{ fontSize: '11px', color: '#64748b', marginTop: '2px' }}>
              Quản trị toàn quyền
            </div>
          </div>
        </div>

        {/* Card 3: Nhân viên Thu gom */}
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
            <UserCheck size={20} color="#3b82f6" />
          </div>
          <div>
            <span style={{ fontSize: '11.5px', fontWeight: 600, color: '#64748b' }}>Nhân viên Thu gom</span>
            <div style={{ fontSize: '22px', fontWeight: 800, color: '#111a4a', lineHeight: 1.1 }}>
              {staffCount}
            </div>
            <div style={{ fontSize: '11px', color: '#3b82f6', fontWeight: 600, marginTop: '2px' }}>
              Tài xế & Thu gom
            </div>
          </div>
        </div>

        {/* Card 4: Báo cáo Sự cố */}
        <div style={{
          backgroundColor: '#ffffff',
          borderRadius: '14px',
          padding: '16px 20px',
          border: `1px solid ${pendingIncidentsCount > 0 ? '#fecaca' : '#e2e8f0'}`,
          boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
          display: 'flex',
          alignItems: 'center',
          gap: '14px'
        }}>
          <div style={{
            width: '42px',
            height: '42px',
            borderRadius: '12px',
            backgroundColor: pendingIncidentsCount > 0 ? '#fff7ed' : '#f8fafc',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0
          }}>
            <Camera size={20} color={pendingIncidentsCount > 0 ? '#f97316' : '#64748b'} />
          </div>
          <div>
            <span style={{ fontSize: '11.5px', fontWeight: 600, color: '#64748b' }}>Báo cáo sự cố thực địa</span>
            <div style={{ fontSize: '22px', fontWeight: 800, color: pendingIncidentsCount > 0 ? '#ea580c' : '#111a4a', lineHeight: 1.1 }}>
              {totalIncidentsCount}
            </div>
            <div style={{ fontSize: '11px', color: pendingIncidentsCount > 0 ? '#dc2626' : '#10b981', fontWeight: 700, marginTop: '2px' }}>
              {pendingIncidentsCount > 0 ? `${pendingIncidentsCount} cần xử lý` : 'Đã xử lý xong'}
            </div>
          </div>
        </div>

      </div>

      {/* 3. Navigation View Switcher (Dual Workflow Tabs) */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '12px',
        borderBottom: '1px solid #e2e8f0',
        paddingBottom: '2px'
      }}>
        {/* Tabs */}
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            onClick={() => {
              setActiveTab('directory');
              setViewingStaffIncidents(null);
            }}
            style={{
              padding: '10px 18px',
              borderRadius: '10px 10px 0 0',
              border: 'none',
              borderBottom: activeTab === 'directory' ? '3px solid #10b981' : '3px solid transparent',
              backgroundColor: activeTab === 'directory' ? '#ffffff' : 'transparent',
              color: activeTab === 'directory' ? '#111a4a' : '#64748b',
              fontSize: '13.5px',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              transition: 'all 150ms ease'
            }}
          >
            <Users size={16} color={activeTab === 'directory' ? '#10b981' : '#64748b'} />
            <span>Danh sách Nhân sự</span>
            <span style={{
              fontSize: '11px',
              padding: '1px 7px',
              borderRadius: '99px',
              backgroundColor: activeTab === 'directory' ? '#ecfdf5' : '#f1f5f9',
              color: activeTab === 'directory' ? '#059669' : '#64748b',
              fontWeight: 700
            }}>
              {totalStaffCount}
            </span>
          </button>

          <button
            onClick={() => setActiveTab('incidents')}
            style={{
              padding: '10px 18px',
              borderRadius: '10px 10px 0 0',
              border: 'none',
              borderBottom: activeTab === 'incidents' ? '3px solid #10b981' : '3px solid transparent',
              backgroundColor: activeTab === 'incidents' ? '#ffffff' : 'transparent',
              color: activeTab === 'incidents' ? '#111a4a' : '#64748b',
              fontSize: '13.5px',
              fontWeight: 700,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              transition: 'all 150ms ease'
            }}
          >
            <Camera size={16} color={activeTab === 'incidents' ? '#10b981' : '#64748b'} />
            <span>Báo cáo Sự cố & Minh chứng Ảnh</span>
            {pendingIncidentsCount > 0 ? (
              <span style={{
                fontSize: '11px',
                padding: '1px 7px',
                borderRadius: '99px',
                backgroundColor: '#fef2f2',
                color: '#dc2626',
                fontWeight: 700,
                border: '1px solid #fecaca'
              }}>
                {pendingIncidentsCount} mới
              </span>
            ) : (
              <span style={{
                fontSize: '11px',
                padding: '1px 7px',
                borderRadius: '99px',
                backgroundColor: '#f1f5f9',
                color: '#64748b',
                fontWeight: 700
              }}>
                {totalIncidentsCount}
              </span>
            )}
          </button>
        </div>

        {/* Global Search Input */}
        <div style={{
          position: 'relative',
          minWidth: '240px',
          marginBottom: '6px'
        }}>
          <Search size={14} color="#94a3b8" style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)' }} />
          <input
            type="text"
            placeholder={activeTab === 'directory' ? "Tìm họ tên, tên đăng nhập..." : "Tìm mã thùng, nhân viên, sự cố..."}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              width: '100%',
              padding: '7px 12px 7px 32px',
              borderRadius: '10px',
              border: '1px solid #e2e8f0',
              backgroundColor: '#ffffff',
              fontSize: '12.5px',
              color: '#1e293b',
              outline: 'none'
            }}
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              style={{
                position: 'absolute',
                right: '8px',
                top: '50%',
                transform: 'translateY(-50%)',
                background: 'none',
                border: 'none',
                color: '#94a3b8',
                cursor: 'pointer'
              }}
            >
              <X size={13} />
            </button>
          )}
        </div>
      </div>

      {/* 4. TAB 1: STAFF DIRECTORY TABLE (ALL CENTERED, NO @, NO ID, LIVE APP ONLINE) */}
      {activeTab === 'directory' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          
          {/* Filter Pills */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '10px' }}>
            <div style={{ display: 'inline-flex', padding: '3px', borderRadius: '10px', backgroundColor: '#f1f5f9' }}>
              {[
                { id: 'all', label: 'Tất cả' },
                { id: 'online', label: '🟢 Đang Online App' },
                { id: 'admin', label: 'Quản trị viên' },
                { id: 'staff', label: 'Nhân viên thu gom' },
                { id: 'locked', label: 'Tài khoản khóa' }
              ].map(pill => (
                <button
                  key={pill.id}
                  onClick={() => setRoleFilter(pill.id)}
                  style={{
                    padding: '5px 12px',
                    borderRadius: '8px',
                    fontSize: '12px',
                    fontWeight: 600,
                    border: 'none',
                    cursor: 'pointer',
                    backgroundColor: roleFilter === pill.id ? '#ffffff' : 'transparent',
                    color: roleFilter === pill.id ? '#111a4a' : '#64748b',
                    boxShadow: roleFilter === pill.id ? '0 1px 3px rgba(0,0,0,0.08)' : 'none',
                    transition: 'all 120ms ease'
                  }}
                >
                  {pill.label}
                </button>
              ))}
            </div>

            <span style={{ fontSize: '12px', color: '#64748b' }}>
              Hiển thị <strong>{filteredEmployees.length}</strong> / {totalStaffCount} nhân sự
            </span>
          </div>

          {/* Table Container (CENTER ALIGNED) */}
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            border: '1px solid #e2e8f0',
            boxShadow: '0 1px 4px rgba(0,0,0,0.03)',
            overflow: 'hidden'
          }}>
            <div style={{ overflowX: 'auto', width: '100%', WebkitOverflowScrolling: 'touch' }}>
              <table style={{ width: '100%', minWidth: '880px', borderCollapse: 'collapse', textAlign: 'center', fontSize: '12.5px' }}>
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
                    <th style={{ padding: '12px 16px', textAlign: 'left', whiteSpace: 'nowrap' }}>Nhân viên</th>
                    <th style={{ padding: '12px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>Tên đăng nhập</th>
                    <th style={{ padding: '12px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>Vai trò</th>
                    <th style={{ padding: '12px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>Trạng thái App</th>
                    <th style={{ padding: '12px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>Sự cố báo cáo</th>
                    <th style={{ padding: '12px 16px', textAlign: 'center', whiteSpace: 'nowrap' }}>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {paginatedEmployees.length > 0 ? (
                    paginatedEmployees.map((emp) => {
                      const isSelf = emp.id === currentUser?.id;
                      const empIncidents = allIncidents.filter(r => r.employee_id === emp.id);
                      const online = isStaffOnline(emp);
                      const initial = emp.full_name ? emp.full_name.charAt(0).toUpperCase() : emp.username.charAt(0).toUpperCase();

                      return (
                        <tr 
                          key={emp.id} 
                          style={{ 
                            borderBottom: '1px solid #f1f5f9',
                            transition: 'background-color 120ms ease'
                          }}
                          onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#fafafa'; }}
                          onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'transparent'; }}
                        >
                          {/* Name & Avatar (Left Aligned) */}
                          <td style={{ padding: '14px 16px', textAlign: 'left' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                              <div style={{
                                width: '34px',
                                height: '34px',
                                borderRadius: '50%',
                                backgroundColor: emp.role === 'admin' ? '#ef4444' : '#2563eb',
                                color: '#ffffff',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                fontWeight: 800,
                                fontSize: '13px',
                                flexShrink: 0
                              }}>
                                {initial}
                              </div>
                              <div>
                                <div style={{ fontWeight: 700, color: '#111a4a', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                                  <span>{emp.full_name || emp.username}</span>
                                  {isSelf && (
                                    <span style={{
                                      fontSize: '9.5px',
                                      padding: '1px 5px',
                                      borderRadius: '99px',
                                      backgroundColor: '#ecfdf5',
                                      color: '#059669',
                                      fontWeight: 700,
                                      border: '1px solid #a7f3d0'
                                    }}>
                                      Bạn
                                    </span>
                                  )}
                                </div>
                              </div>
                            </div>
                          </td>

                          {/* Clean Username (NO @) */}
                          <td style={{ padding: '14px 16px', textAlign: 'center', fontFamily: 'monospace', fontWeight: 600, color: '#334155' }}>
                            {emp.username}
                          </td>

                          {/* Role Badge (Centered) */}
                          <td style={{ padding: '14px 16px', textAlign: 'center' }}>
                            <span style={{
                              padding: '3px 10px',
                              borderRadius: '999px',
                              fontSize: '11px',
                              fontWeight: 700,
                              backgroundColor: emp.role === 'admin' ? '#fef2f2' : '#eff6ff',
                              color: emp.role === 'admin' ? '#dc2626' : '#2563eb',
                              border: emp.role === 'admin' ? '1px solid #fecaca' : '1px solid #bfdbfe',
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '4px'
                            }}>
                              {emp.role === 'admin' ? <Shield size={11} /> : <User size={11} />}
                              <span>{emp.role === 'admin' ? 'Quản trị viên' : 'Nhân viên gom'}</span>
                            </span>
                          </td>

                          {/* App Online Status (ONLY for Staff Collector, Admin displays '—') */}
                          <td style={{ padding: '14px 16px', textAlign: 'center' }}>
                            {emp.role === 'admin' ? (
                              <span style={{ color: '#94a3b8', fontSize: '14px', fontWeight: 600 }}>—</span>
                            ) : (
                              <span style={{
                                padding: '3px 10px',
                                borderRadius: '999px',
                                fontSize: '11px',
                                fontWeight: 700,
                                backgroundColor: online ? '#ecfdf5' : '#f1f5f9',
                                color: online ? '#059669' : '#64748b',
                                border: online ? '1px solid #a7f3d0' : '1px solid #e2e8f0',
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '5px'
                              }}>
                                <span style={{
                                  width: '6px',
                                  height: '6px',
                                  borderRadius: '50%',
                                  backgroundColor: online ? '#10b981' : '#94a3b8'
                                }} />
                                <span>{online ? 'Online App' : 'Ngoại tuyến'}</span>
                              </span>
                            )}
                          </td>

                          {/* Incident Count Badge (Centered) */}
                          <td style={{ padding: '14px 16px', textAlign: 'center' }}>
                            <button
                              onClick={() => {
                                setViewingStaffIncidents(emp);
                                setActiveTab('incidents');
                              }}
                              style={{
                                padding: '4px 10px',
                                borderRadius: '8px',
                                backgroundColor: empIncidents.length > 0 ? '#fff7ed' : '#f8fafc',
                                color: empIncidents.length > 0 ? '#c2410c' : '#64748b',
                                border: empIncidents.length > 0 ? '1px solid #fed7aa' : '1px solid #e2e8f0',
                                fontSize: '11px',
                                fontWeight: 700,
                                cursor: 'pointer',
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '4px'
                              }}
                            >
                              <Camera size={12} color={empIncidents.length > 0 ? '#f97316' : '#64748b'} />
                              <span>{empIncidents.length} sự cố</span>
                            </button>
                          </td>

                          {/* Actions: Edit Icon + Lock Icon + Delete Icon (Centered) */}
                          <td style={{ padding: '14px 16px', textAlign: 'center' }}>
                            <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                              
                              {/* Edit Button (Available for all accounts including Admin) */}
                              <button
                                onClick={() => handleOpenEdit(emp)}
                                style={{
                                  padding: '5px 9px',
                                  borderRadius: '8px',
                                  backgroundColor: '#eff6ff',
                                  color: '#2563eb',
                                  border: '1px solid #bfdbfe',
                                  cursor: 'pointer',
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  gap: '4px',
                                  fontSize: '11.5px',
                                  fontWeight: 700,
                                  transition: 'all 120ms ease'
                                }}
                                title="Chỉnh sửa họ tên & thông tin tài khoản"
                              >
                                <Edit3 size={13} />
                                <span>Sửa</span>
                              </button>

                              {/* Lock / Unlock Icon Button */}
                              {!isSelf ? (
                                <button
                                  onClick={() => handleToggleActive(emp)}
                                  style={{
                                    padding: '5px 8px',
                                    borderRadius: '8px',
                                    backgroundColor: emp.is_active ? '#f8fafc' : '#fef2f2',
                                    color: emp.is_active ? '#64748b' : '#dc2626',
                                    border: emp.is_active ? '1px solid #e2e8f0' : '1px solid #fecaca',
                                    cursor: 'pointer',
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    transition: 'all 120ms ease'
                                  }}
                                  title={emp.is_active ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}
                                >
                                  {emp.is_active ? (
                                    <Unlock size={14} color="#64748b" />
                                  ) : (
                                    <Lock size={14} color="#dc2626" />
                                  )}
                                </button>
                              ) : null}

                              {/* Delete Icon Button */}
                              {!isSelf ? (
                                <button
                                  onClick={() => handleDelete(emp)}
                                  style={{
                                    padding: '5px 8px',
                                    borderRadius: '8px',
                                    backgroundColor: '#fef2f2',
                                    color: '#ef4444',
                                    border: '1px solid #fee2e2',
                                    cursor: 'pointer',
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    transition: 'all 120ms ease'
                                  }}
                                  title="Xóa tài khoản nhân viên"
                                >
                                  <Trash2 size={14} />
                                </button>
                              ) : null}

                              {isSelf && (
                                <span style={{ fontSize: '11px', color: '#94a3b8' }}>—</span>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })
                  ) : (
                    <tr>
                      <td colSpan={6} style={{ padding: '36px', textAlign: 'center', color: '#94a3b8' }}>
                        {loading ? 'Đang tải dữ liệu...' : 'Không tìm thấy nhân viên.'}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination Bar (Matching Universal Design) */}
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
                Hiển thị {filteredEmployees.length === 0 ? 0 : (staffPage - 1) * staffPageSize + 1} - {Math.min(staffPage * staffPageSize, filteredEmployees.length)} / {filteredEmployees.length} nhân sự
              </div>

              {/* Center/Right: Numbered pagination & Page size selector */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  {/* Prev Button */}
                  <button
                    onClick={() => setStaffPage(p => Math.max(1, p - 1))}
                    disabled={staffPage === 1}
                    style={{
                      width: '32px',
                      height: '32px',
                      borderRadius: '8px',
                      border: '1px solid #e2e8f0',
                      backgroundColor: '#ffffff',
                      color: staffPage === 1 ? '#cbd5e1' : '#334155',
                      cursor: staffPage === 1 ? 'not-allowed' : 'pointer',
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
                    {staffPage}
                  </div>

                  {/* Next Button */}
                  <button
                    onClick={() => setStaffPage(p => Math.min(staffTotalPages, p + 1))}
                    disabled={staffPage === staffTotalPages}
                    style={{
                      width: '32px',
                      height: '32px',
                      borderRadius: '8px',
                      border: '1px solid #e2e8f0',
                      backgroundColor: '#ffffff',
                      color: staffPage === staffTotalPages ? '#cbd5e1' : '#334155',
                      cursor: staffPage === staffTotalPages ? 'not-allowed' : 'pointer',
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
                  value={staffPageSize}
                  onChange={(e) => { setStaffPageSize(Number(e.target.value)); setStaffPage(1); }}
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

        </div>
      )}

      {/* 5. TAB 2: INCIDENTS & PHOTO EVIDENCE (PURE SUPABASE STORAGE) */}
      {activeTab === 'incidents' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          
          {/* Toolbar & Filter */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
              
              {/* Status Filter Pills */}
              <div style={{ display: 'inline-flex', padding: '3px', borderRadius: '10px', backgroundColor: '#f1f5f9' }}>
                <button
                  onClick={() => setIncidentStatusFilter('all')}
                  style={{
                    padding: '5px 12px',
                    borderRadius: '8px',
                    fontSize: '12px',
                    fontWeight: 600,
                    border: 'none',
                    cursor: 'pointer',
                    backgroundColor: incidentStatusFilter === 'all' ? '#ffffff' : 'transparent',
                    color: incidentStatusFilter === 'all' ? '#111a4a' : '#64748b',
                    boxShadow: incidentStatusFilter === 'all' ? '0 1px 3px rgba(0,0,0,0.08)' : 'none'
                  }}
                >
                  Tất cả ({allIncidents.length})
                </button>
                <button
                  onClick={() => setIncidentStatusFilter('new')}
                  style={{
                    padding: '5px 12px',
                    borderRadius: '8px',
                    fontSize: '12px',
                    fontWeight: 600,
                    border: 'none',
                    cursor: 'pointer',
                    backgroundColor: incidentStatusFilter === 'new' ? '#ffffff' : 'transparent',
                    color: incidentStatusFilter === 'new' ? '#dc2626' : '#64748b',
                    boxShadow: incidentStatusFilter === 'new' ? '0 1px 3px rgba(0,0,0,0.08)' : 'none'
                  }}
                >
                  Cần xử lý ({pendingIncidentsCount})
                </button>
                <button
                  onClick={() => setIncidentStatusFilter('resolved')}
                  style={{
                    padding: '5px 12px',
                    borderRadius: '8px',
                    fontSize: '12px',
                    fontWeight: 600,
                    border: 'none',
                    cursor: 'pointer',
                    backgroundColor: incidentStatusFilter === 'resolved' ? '#ffffff' : 'transparent',
                    color: incidentStatusFilter === 'resolved' ? '#059669' : '#64748b',
                    boxShadow: incidentStatusFilter === 'resolved' ? '0 1px 3px rgba(0,0,0,0.08)' : 'none'
                  }}
                >
                  Đã giải quyết ({resolvedIncidentsCount})
                </button>
              </div>

              {/* Staff filter badge */}
              {viewingStaffIncidents && (
                <div style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '4px 10px',
                  borderRadius: '99px',
                  backgroundColor: '#eff6ff',
                  color: '#2563eb',
                  border: '1px solid #bfdbfe',
                  fontSize: '12px',
                  fontWeight: 600
                }}>
                  <span>Nhân viên: <strong>{viewingStaffIncidents.full_name || viewingStaffIncidents.username}</strong></span>
                  <button
                    onClick={() => setViewingStaffIncidents(null)}
                    style={{ background: 'none', border: 'none', color: '#2563eb', cursor: 'pointer', display: 'flex' }}
                  >
                    <X size={13} />
                  </button>
                </div>
              )}
            </div>

            <span style={{ fontSize: '12px', color: '#64748b' }}>
              Hiển thị <strong>{filteredIncidents.length}</strong> báo cáo
            </span>
          </div>

          {/* Grid of Incident Cards */}
          {paginatedIncidents.length > 0 ? (
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
              gap: '16px'
            }}>
              {paginatedIncidents.map((rep) => {
                const isResolved = String(rep.status || '').toUpperCase() === 'RESOLVED';
                const createdDate = formatVietnamDateTime(rep.created_at);
                const displayImageUrl = rep.image_url || (rep.proof_image_url && /^https?:\/\//i.test(rep.proof_image_url) ? rep.proof_image_url : null);

                return (
                  <div
                    key={rep.id}
                    style={{
                      backgroundColor: '#ffffff',
                      borderRadius: '16px',
                      border: `1px solid ${isResolved ? '#e2e8f0' : '#fed7aa'}`,
                      boxShadow: '0 1px 4px rgba(0,0,0,0.04)',
                      display: 'flex',
                      flexDirection: 'column',
                      overflow: 'hidden'
                    }}
                  >
                    {/* Top Photo Thumbnail if available in Supabase Storage or URL */}
                    {displayImageUrl ? (
                      <div
                        onClick={() => {
                          setSelectedProofImage(displayImageUrl);
                          setImageCaption(`${rep.reason || 'Sự cố'} tại #${rep.device_id} - ${rep.employee_name}`);
                        }}
                        style={{
                          position: 'relative',
                          height: '170px',
                          width: '100%',
                          backgroundColor: '#0f172a',
                          cursor: 'pointer',
                          overflow: 'hidden'
                        }}
                      >
                        <img
                          src={displayImageUrl}
                          alt="Ảnh sự cố thực tế"
                          style={{
                            width: '100%',
                            height: '100%',
                            objectFit: 'cover'
                          }}
                        />
                        <div style={{
                          position: 'absolute',
                          bottom: '8px',
                          right: '8px',
                          padding: '3px 8px',
                          borderRadius: '6px',
                          backgroundColor: 'rgba(0,0,0,0.7)',
                          color: '#ffffff',
                          fontSize: '11px',
                          fontWeight: 600,
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px'
                        }}>
                          <Eye size={12} />
                          <span>Xem ảnh lớn</span>
                        </div>
                      </div>
                    ) : (
                      <div style={{
                        height: '70px',
                        backgroundColor: '#f8fafc',
                        borderBottom: '1px solid #f1f5f9',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '6px',
                        color: '#94a3b8',
                        fontSize: '11.5px',
                        fontWeight: 500
                      }}>
                        <Camera size={15} color="#cbd5e1" />
                        <span>Không có ảnh minh chứng đính kèm</span>
                      </div>
                    )}

                    {/* Body */}
                    <div style={{ padding: '16px', display: 'flex', flexDirection: 'column', flex: 1, gap: '10px' }}>
                      
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <span style={{
                          padding: '3px 8px',
                          borderRadius: '99px',
                          fontSize: '11px',
                          fontWeight: 700,
                          backgroundColor: isResolved ? '#ecfdf5' : '#fef2f2',
                          color: isResolved ? '#059669' : '#dc2626',
                          border: isResolved ? '1px solid #bbf7d0' : '1px solid #fecaca',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px'
                        }}>
                          {isResolved ? <CheckCircle2 size={12} /> : <AlertTriangle size={12} />}
                          <span>{isResolved ? 'Đã giải quyết' : 'Cần xử lý'}</span>
                        </span>

                        <span style={{
                          fontSize: '11px',
                          fontWeight: 800,
                          color: '#111a4a',
                          fontFamily: 'monospace',
                          backgroundColor: '#f1f5f9',
                          padding: '2px 8px',
                          borderRadius: '6px'
                        }}>
                          #{rep.device_id}
                        </span>
                      </div>

                      <div>
                        <h4 style={{ fontSize: '13.5px', fontWeight: 700, color: '#111a4a', margin: '0 0 4px 0' }}>
                          {rep.reason || 'Báo cáo sự cố'}
                        </h4>
                        <p style={{ fontSize: '12px', color: '#475569', margin: 0, lineHeight: 1.4 }}>
                          {rep.description || 'Không có ghi chú thêm.'}
                        </p>
                      </div>

                      <div style={{
                        marginTop: 'auto',
                        paddingTop: '10px',
                        borderTop: '1px solid #f1f5f9',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '3px',
                        fontSize: '11px',
                        color: '#64748b'
                      }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                          <MapPin size={12} color="#94a3b8" />
                          <span style={{ color: '#334155', fontWeight: 600 }}>{rep.bin_name || rep.device_id}</span>
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '2px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                            <User size={12} color="#94a3b8" />
                            <span>{rep.employee_name || 'Nhân viên'}</span>
                          </div>
                          <span>{createdDate}</span>
                        </div>
                      </div>

                      <div style={{ display: 'flex', justifyContent: 'flex-end', paddingTop: '6px' }}>
                        <button
                          onClick={() => handleToggleIncidentStatus(rep)}
                          disabled={resolvingId === rep.id}
                          style={{
                            padding: '6px 12px',
                            borderRadius: '8px',
                            backgroundColor: isResolved ? '#f1f5f9' : '#10b981',
                            color: isResolved ? '#475569' : '#ffffff',
                            border: isResolved ? '1px solid #cbd5e1' : 'none',
                            fontSize: '11.5px',
                            fontWeight: 700,
                            cursor: 'pointer',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '4px'
                          }}
                        >
                          {isResolved ? (
                            <>
                              <RefreshCw size={12} />
                              <span>Mở lại</span>
                            </>
                          ) : (
                            <>
                              <Check size={12} />
                              <span>Đã xử lý xong</span>
                            </>
                          )}
                        </button>
                      </div>

                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div style={{
              backgroundColor: '#ffffff',
              borderRadius: '16px',
              border: '1px solid #e2e8f0',
              padding: '48px 20px',
              textAlign: 'center',
              color: '#94a3b8'
            }}>
              <CheckCircle2 size={36} color="#10b981" style={{ margin: '0 auto 10px auto' }} />
              <div style={{ fontSize: '15px', fontWeight: 700, color: '#111a4a' }}>
                Không có báo cáo sự cố nào
              </div>
              <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>
                Hệ thống đang vận hành an toàn.
              </div>
            </div>
          )}

          {/* Incidents Pagination Bar */}
          {filteredIncidents.length > 0 && (
            <div style={{
              backgroundColor: '#ffffff',
              borderRadius: '14px',
              border: '1px solid #e2e8f0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '12px 18px',
              flexWrap: 'wrap',
              gap: '12px'
            }}>
              {/* Left: Records info */}
              <div style={{ fontSize: '13px', color: '#475569', fontWeight: 500 }}>
                Hiển thị {filteredIncidents.length === 0 ? 0 : (incidentPage - 1) * incidentPageSize + 1} - {Math.min(incidentPage * incidentPageSize, filteredIncidents.length)} / {filteredIncidents.length} báo cáo sự cố
              </div>

              {/* Center/Right: Numbered pagination & Page size selector */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  {/* Prev Button */}
                  <button
                    onClick={() => setIncidentPage(p => Math.max(1, p - 1))}
                    disabled={incidentPage === 1}
                    style={{
                      width: '32px',
                      height: '32px',
                      borderRadius: '8px',
                      border: '1px solid #e2e8f0',
                      backgroundColor: '#ffffff',
                      color: incidentPage === 1 ? '#cbd5e1' : '#334155',
                      cursor: incidentPage === 1 ? 'not-allowed' : 'pointer',
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
                    {incidentPage}
                  </div>

                  {/* Next Button */}
                  <button
                    onClick={() => setIncidentPage(p => Math.min(incidentTotalPages, p + 1))}
                    disabled={incidentPage === incidentTotalPages}
                    style={{
                      width: '32px',
                      height: '32px',
                      borderRadius: '8px',
                      border: '1px solid #e2e8f0',
                      backgroundColor: '#ffffff',
                      color: incidentPage === incidentTotalPages ? '#cbd5e1' : '#334155',
                      cursor: incidentPage === incidentTotalPages ? 'not-allowed' : 'pointer',
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
                  value={incidentPageSize}
                  onChange={(e) => { setIncidentPageSize(Number(e.target.value)); setIncidentPage(1); }}
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
                  <option value={6}>6 / trang</option>
                  <option value={12}>12 / trang</option>
                  <option value={24}>24 / trang</option>
                  <option value={48}>48 / trang</option>
                </select>
              </div>
            </div>
          )}

        </div>
      )}

      {/* 6. MODAL: THÊM TÀI KHOẢN NHÂN VIÊN MỚI (NO GMAIL NEEDED) */}
      {showAddModal && (
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
          zIndex: 1000,
          padding: '20px'
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '18px',
            width: '420px',
            maxWidth: '100%',
            boxShadow: '0 20px 40px rgba(0,0,0,0.15)',
            border: '1px solid #e2e8f0',
            overflow: 'hidden',
            animation: 'fadeIn 180ms ease-out'
          }}>
            {/* Header */}
            <div style={{
              padding: '18px 22px',
              borderBottom: '1px solid #f1f5f9',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div style={{
                  width: '34px',
                  height: '34px',
                  borderRadius: '10px',
                  backgroundColor: '#ecfdf5',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}>
                  <UserPlus size={18} color="#10b981" />
                </div>
                <div>
                  <h3 style={{ fontSize: '16px', fontWeight: 800, color: '#111a4a', margin: 0 }}>
                    Thêm nhân viên mới
                  </h3>
                  <span style={{ fontSize: '11.5px', color: '#64748b' }}>Cấp quyền đăng nhập hệ thống</span>
                </div>
              </div>
              <button
                onClick={() => setShowAddModal(false)}
                style={{ background: 'none', border: 'none', color: '#94a3b8', cursor: 'pointer', padding: '4px' }}
              >
                <X size={18} />
              </button>
            </div>

            {/* Form */}
            <form onSubmit={handleCreateEmployee} style={{ padding: '22px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
              
              <div>
                <label style={{ fontSize: '12px', fontWeight: 700, color: '#334155', display: 'block', marginBottom: '6px' }}>
                  Họ và tên <span style={{ color: '#ef4444' }}>*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="VD: Nguyễn Văn An"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '9px 12px',
                    borderRadius: '10px',
                    border: '1px solid #cbd5e1',
                    fontSize: '13px',
                    outline: 'none',
                    boxSizing: 'border-box'
                  }}
                />
              </div>

              <div>
                <label style={{ fontSize: '12px', fontWeight: 700, color: '#334155', display: 'block', marginBottom: '6px' }}>
                  Tên đăng nhập <span style={{ color: '#ef4444' }}>*</span>
                </label>
                <input
                  type="text"
                  required
                  minLength={3}
                  placeholder="VD: nva_collector"
                  value={username}
                  onChange={(e) => setUsername(e.target.value.toLowerCase().replace(/[^a-z0-9._-]/g, ''))}
                  style={{
                    width: '100%',
                    padding: '9px 12px',
                    borderRadius: '10px',
                    border: '1px solid #cbd5e1',
                    fontSize: '13px',
                    fontFamily: 'monospace',
                    outline: 'none',
                    boxSizing: 'border-box'
                  }}
                />
              </div>

              <div>
                <label style={{ fontSize: '12px', fontWeight: 700, color: '#334155', display: 'block', marginBottom: '6px' }}>
                  Mật khẩu khởi tạo <span style={{ color: '#ef4444' }}>*</span>
                </label>
                <input
                  type="password"
                  required
                  minLength={8}
                  placeholder="Tối thiểu 8 ký tự"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '9px 12px',
                    borderRadius: '10px',
                    border: '1px solid #cbd5e1',
                    fontSize: '13px',
                    outline: 'none',
                    boxSizing: 'border-box'
                  }}
                />
              </div>

              <div>
                <label style={{ fontSize: '12px', fontWeight: 700, color: '#334155', display: 'block', marginBottom: '6px' }}>
                  Vai trò phân quyền <span style={{ color: '#ef4444' }}>*</span>
                </label>
                <select
                  value={role}
                  onChange={(e) => setRole(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '9px 12px',
                    borderRadius: '10px',
                    border: '1px solid #cbd5e1',
                    fontSize: '13px',
                    outline: 'none',
                    backgroundColor: '#ffffff',
                    boxSizing: 'border-box'
                  }}
                >
                  <option value="staff">Nhân viên thu gom (Staff App)</option>
                  <option value="admin">Quản trị viên (Admin Web)</option>
                </select>
              </div>

              {/* Action Buttons */}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '8px' }}>
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  style={{
                    padding: '9px 16px',
                    borderRadius: '10px',
                    backgroundColor: '#f1f5f9',
                    color: '#475569',
                    border: 'none',
                    fontSize: '13px',
                    fontWeight: 600,
                    cursor: 'pointer'
                  }}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={creating}
                  style={{
                    padding: '9px 20px',
                    borderRadius: '10px',
                    backgroundColor: '#10b981',
                    color: '#ffffff',
                    border: 'none',
                    fontSize: '13px',
                    fontWeight: 700,
                    cursor: 'pointer',
                    boxShadow: '0 2px 6px rgba(16, 185, 129, 0.25)'
                  }}
                >
                  {creating ? 'Đang tạo...' : 'Tạo tài khoản'}
                </button>
              </div>

            </form>
          </div>
        </div>
      )}

      {/* 6.5. MODAL: CHỈNH SỬA THÔNG TIN NHÂN SỰ */}
      {editModalEmployee && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.65)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1100,
          padding: '20px'
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '16px',
            width: '100%',
            maxWidth: '480px',
            boxShadow: '0 20px 40px rgba(0,0,0,0.15)',
            border: '1px solid #e2e8f0',
            overflow: 'hidden'
          }}>
            {/* Modal Header */}
            <div style={{
              padding: '18px 24px',
              borderBottom: '1px solid #f1f5f9',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              backgroundColor: '#fafbfc'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <div style={{
                  width: '36px',
                  height: '36px',
                  borderRadius: '10px',
                  backgroundColor: '#eff6ff',
                  color: '#2563eb',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}>
                  <Edit3 size={18} />
                </div>
                <div>
                  <h3 style={{ fontSize: '15px', fontWeight: 800, color: '#0f172a', margin: 0 }}>
                    Chỉnh sửa Thông tin Nhân sự
                  </h3>
                  <p style={{ fontSize: '12px', color: '#64748b', margin: 0, marginTop: '2px' }}>
                    Tài khoản: <strong style={{ fontFamily: 'monospace', color: '#2563eb' }}>{editModalEmployee.username}</strong>
                  </p>
                </div>
              </div>
              <button
                onClick={() => setEditModalEmployee(null)}
                style={{
                  background: 'none',
                  border: 'none',
                  color: '#94a3b8',
                  cursor: 'pointer',
                  padding: '6px',
                  borderRadius: '8px',
                  display: 'flex'
                }}
              >
                <X size={18} />
              </button>
            </div>

            {/* Modal Form */}
            <form onSubmit={handleSaveEdit} style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div>
                <label style={{ fontSize: '12.5px', fontWeight: 700, color: '#334155', display: 'block', marginBottom: '6px' }}>
                  Họ và tên hiển thị <span style={{ color: '#ef4444' }}>*</span>
                </label>
                <input
                  type="text"
                  required
                  placeholder="VD: Nguyễn Phúc, Phúc Admin..."
                  value={editFullName}
                  onChange={(e) => setEditFullName(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '10px 14px',
                    borderRadius: '10px',
                    border: '1px solid #cbd5e1',
                    fontSize: '13.5px',
                    outline: 'none',
                    backgroundColor: '#ffffff',
                    boxSizing: 'border-box'
                  }}
                />
              </div>

              <div>
                <label style={{ fontSize: '12.5px', fontWeight: 700, color: '#334155', display: 'block', marginBottom: '6px' }}>
                  Vai trò phân quyền
                </label>
                <select
                  value={editRole}
                  onChange={(e) => setEditRole(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '10px 14px',
                    borderRadius: '10px',
                    border: '1px solid #cbd5e1',
                    fontSize: '13.5px',
                    outline: 'none',
                    backgroundColor: '#ffffff',
                    boxSizing: 'border-box'
                  }}
                >
                  <option value="admin">Quản trị viên (Admin Web)</option>
                  <option value="staff">Nhân viên thu gom (Staff App)</option>
                </select>
              </div>

              <div>
                <label style={{ fontSize: '12.5px', fontWeight: 700, color: '#334155', display: 'block', marginBottom: '6px' }}>
                  Mật khẩu mới <span style={{ fontSize: '11px', color: '#94a3b8', fontWeight: 500 }}>(Để trống nếu không đổi mật khẩu)</span>
                </label>
                <input
                  type="password"
                  placeholder="Nhập mật khẩu mới (tối thiểu 6 ký tự)..."
                  value={editPassword}
                  onChange={(e) => setEditPassword(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '10px 14px',
                    borderRadius: '10px',
                    border: '1px solid #cbd5e1',
                    fontSize: '13.5px',
                    outline: 'none',
                    backgroundColor: '#ffffff',
                    boxSizing: 'border-box'
                  }}
                />
              </div>

              {/* Action Buttons */}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
                <button
                  type="button"
                  onClick={() => setEditModalEmployee(null)}
                  style={{
                    padding: '9px 18px',
                    borderRadius: '10px',
                    backgroundColor: '#f1f5f9',
                    color: '#475569',
                    border: 'none',
                    fontSize: '13px',
                    fontWeight: 600,
                    cursor: 'pointer'
                  }}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={updating}
                  style={{
                    padding: '9px 22px',
                    borderRadius: '10px',
                    backgroundColor: '#2563eb',
                    color: '#ffffff',
                    border: 'none',
                    fontSize: '13px',
                    fontWeight: 700,
                    cursor: 'pointer',
                    boxShadow: '0 2px 6px rgba(37, 99, 235, 0.25)'
                  }}
                >
                  {updating ? 'Đang lưu...' : 'Lưu thay đổi'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 7. MODAL: HIGH-RESOLUTION LIGHTBOX PROOF PHOTO */}
      {selectedProofImage && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(5, 15, 25, 0.88)',
          backdropFilter: 'blur(8px)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1200,
          padding: '20px'
        }}>
          {/* Top Bar */}
          <div style={{
            width: '100%',
            maxWidth: '900px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '12px',
            color: '#ffffff'
          }}>
            <div style={{ fontSize: '13.5px', fontWeight: 600 }}>
              📸 {imageCaption || 'Ảnh chụp sự cố hiện trường từ Supabase Storage'}
            </div>
            <button
              onClick={() => setSelectedProofImage(null)}
              style={{
                backgroundColor: 'rgba(255, 255, 255, 0.15)',
                color: '#ffffff',
                border: 'none',
                borderRadius: '50%',
                width: '34px',
                height: '34px',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              <X size={18} />
            </button>
          </div>

          {/* Image Container */}
          <div style={{
            maxWidth: '900px',
            maxHeight: '80vh',
            borderRadius: '14px',
            overflow: 'hidden',
            boxShadow: '0 25px 50px rgba(0,0,0,0.5)',
            border: '1px solid rgba(255,255,255,0.1)'
          }}>
            <img
              src={selectedProofImage}
              alt="Ảnh minh chứng"
              style={{
                width: '100%',
                maxHeight: '80vh',
                objectFit: 'contain',
                display: 'block'
              }}
            />
          </div>
        </div>
      )}

    </div>
  );
}
