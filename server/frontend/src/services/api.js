/**
 * API Service Layer for SmartWaste Admin
 */

const API_BASE = '/api';

async function request(endpoint, options = {}) {
  const config = {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    }
  };

  const response = await fetch(`${API_BASE}${endpoint}`, config);
  
  if (response.status === 401) {
    // Trả về đối tượng unauthorized để App xử lý redirect đăng nhập
    return { ok: false, status: 401, error: 'Chưa đăng nhập hoặc phiên đã hết hạn' };
  }

  const text = await response.text();
  let data = {};
  try {
    data = text ? JSON.parse(text) : {};
  } catch (_) {
    data = { message: text };
  }

  if (!response.ok) {
    throw new Error(data.error || data.message || `Lỗi yêu cầu HTTP ${response.status}`);
  }

  return data;
}

export const api = {
  // Auth
  login: (username, password) => request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  }),
  getMe: () => request('/auth/me'),
  logout: () => request('/auth/logout', { method: 'POST' }),

  // Smart Bins
  getBins: () => request('/bins'),
  sendCommand: (binId, action) => request(`/bins/${encodeURIComponent(binId)}/command`, {
    method: 'POST',
    body: JSON.stringify({ action })
  }),
  updateBin: (binId, data) => request(`/bins/${encodeURIComponent(binId)}`, {
    method: 'PATCH',
    body: JSON.stringify(data)
  }),
  updateCoordinates: (binId, latitude, longitude) => request(`/bins/${encodeURIComponent(binId)}/coordinates`, {
    method: 'PATCH',
    body: JSON.stringify({ latitude, longitude })
  }),
  getDashboardStats: () => request('/dashboard/stats'),

  // Events & Telemetry
  getEvents: (params = {}) => {
    const search = new URLSearchParams();
    if (params.limit) search.set('limit', params.limit);
    if (params.deviceId) search.set('deviceId', params.deviceId);
    return request(`/events?${search.toString()}`);
  },

  // Employees (Admin Only)
  getEmployees: () => request('/employees'),
  createEmployee: (data) => request('/employees', {
    method: 'POST',
    body: JSON.stringify(data)
  }),
  setEmployeeActive: (id, isActive) => request(`/employees/${encodeURIComponent(id)}/active`, {
    method: 'PATCH',
    body: JSON.stringify({ isActive })
  }),
  deleteEmployee: (id) => request(`/employees/${encodeURIComponent(id)}`, {
    method: 'DELETE'
  }),
  getEmployeeIncidents: (employeeId) => request(`/employees/${encodeURIComponent(employeeId)}/incidents`),
  getAllIncidents: () => request('/incidents'),
  updateIncidentStatus: (id, status) => request(`/incidents/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  }),

  // Map & Routing
  getMapLocations: () => request('/map/locations'),
  getMapConfig: () => request('/map/config'),
  calculateRoute: (coordinates) => request('/map/route', {
    method: 'POST',
    body: JSON.stringify({ coordinates })
  }),

  // Dispatching & Collection Jobs
  getActiveDispatchJobs: () => request('/dispatch/active-jobs'),
  getDispatchHistory: (limit = 100) => request(`/dispatch/history?limit=${limit}`),
  assignDispatchJob: (employeeId, employeeName, binIds) => {
    const payload = typeof employeeId === 'object' && employeeId !== null
      ? employeeId
      : { employeeId, employeeName, binIds };
    return request('/dispatch/assign', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },
  reassignDispatchJob: (jobId, employeeId, employeeName) => {
    const payload = typeof employeeId === 'object' && employeeId !== null
      ? employeeId
      : { employeeId, employeeName };
    return request(`/dispatch/jobs/${encodeURIComponent(jobId)}/reassign`, {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },
  cancelDispatchJob: (jobId) => request(`/dispatch/jobs/${encodeURIComponent(jobId)}/cancel`, {
    method: 'POST'
  }),

  // Health
  getHealth: () => request('/health'),

  // Dynamic System Settings
  getSettings: () => request('/settings'),
  updateSettings: (data) => request('/settings', {
    method: 'PATCH',
    body: JSON.stringify(data)
  }),
  resetSettings: () => request('/settings/reset', {
    method: 'POST'
  })
};
