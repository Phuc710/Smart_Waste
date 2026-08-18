'use strict';

const env = require('../config/env');
const logger = require('./logger');

function supabaseHeaders(extra = {}) {
    const key = env.SUPABASE_SERVICE_ROLE_KEY || env.SUPABASE_ANON_KEY;
    return {
        apikey: key,
        Authorization: `Bearer ${key}`,
        'Content-Type': 'application/json',
        ...extra
    };
}

async function supabaseRequest(resource, options = {}, retries = 2) {
    for (let attempt = 0; attempt <= retries; attempt++) {
        try {
            const response = await fetch(`${env.SUPABASE_URL}/rest/v1/${resource}`, {
                ...options,
                signal: options.signal || AbortSignal.timeout(15000),
                headers: supabaseHeaders(options.headers)
            });
            if (!response.ok) {
                const detail = await response.text();
                let message = detail || response.statusText;
                try { 
                    const parsed = JSON.parse(detail);
                    message = parsed.message || parsed.msg || parsed.error || message; 
                } catch (_) { /* not json */ }
                throw new Error(`${response.status} ${message}`);
            }
            if (response.status === 204) return null;
            const body = await response.text();
            return body ? JSON.parse(body) : null;
        } catch (err) {
            const isTimeout = err.name === 'TimeoutError' || err.code === 'UND_ERR_CONNECT_TIMEOUT' || err.message?.includes('fetch failed');
            if (isTimeout && attempt < retries) {
                await new Promise(r => setTimeout(r, 1000 * (attempt + 1)));
                continue;
            }
            throw err;
        }
    }
}

async function supabaseServiceRequest(resource, options = {}) {
    if (!env.SUPABASE_SERVICE_ROLE_KEY) {
        throw new Error('Server chưa được cấu hình SUPABASE_SERVICE_ROLE_KEY.');
    }
    return supabaseRequest(resource, {
        ...options,
        headers: {
            apikey: env.SUPABASE_SERVICE_ROLE_KEY,
            Authorization: `Bearer ${env.SUPABASE_SERVICE_ROLE_KEY}`,
            ...(options.headers || {})
        }
    });
}

async function authAdminRequest(resource, options = {}) {
    if (!env.SUPABASE_SERVICE_ROLE_KEY) {
        throw new Error('Server chưa được cấu hình SUPABASE_SERVICE_ROLE_KEY.');
    }
    const response = await fetch(`${env.SUPABASE_URL}/auth/v1/admin/${resource}`, {
        ...options,
        signal: options.signal || AbortSignal.timeout(15000),
        headers: {
            apikey: env.SUPABASE_SERVICE_ROLE_KEY,
            Authorization: `Bearer ${env.SUPABASE_SERVICE_ROLE_KEY}`,
            'Content-Type': 'application/json',
            ...(options.headers || {})
        }
    });
    const text = await response.text();
    let data = {};
    try { data = text ? JSON.parse(text) : {}; } catch (_) { /* not json */ }
    if (!response.ok) {
        throw new Error(data.msg || data.message || data.error_description || 'Không thao tác được với Supabase Auth.');
    }
    return data;
}

async function storageServiceRequest(resource, options = {}) {
    if (!env.SUPABASE_SERVICE_ROLE_KEY) {
        throw new Error('Server chưa được cấu hình SUPABASE_SERVICE_ROLE_KEY.');
    }
    const result = await fetch(`${env.SUPABASE_URL}/storage/v1/${resource}`, {
        ...options,
        signal: options.signal || AbortSignal.timeout(8000),
        headers: {
            apikey: env.SUPABASE_SERVICE_ROLE_KEY,
            Authorization: `Bearer ${env.SUPABASE_SERVICE_ROLE_KEY}`,
            'Content-Type': 'application/json',
            ...(options.headers || {})
        }
    });
    const text = await result.text();
    let data = {};
    try { data = text ? JSON.parse(text) : {}; } catch (_) { /* not json */ }
    if (!result.ok) {
        throw new Error(data.message || data.error || data.statusCode || 'Không đọc được ảnh từ Supabase Storage.');
    }
    return data;
}

async function callRpc(name, parameters = {}) {
    return supabaseRequest(`rpc/${name}`, {
        method: 'POST',
        body: JSON.stringify(parameters)
    });
}

async function callServiceRpc(name, parameters = {}) {
    return supabaseServiceRequest(`rpc/${name}`, {
        method: 'POST',
        headers: { Prefer: 'return=representation' },
        body: JSON.stringify(parameters)
    });
}

module.exports = {
    supabaseHeaders,
    supabaseRequest,
    supabaseServiceRequest,
    authAdminRequest,
    storageServiceRequest,
    callRpc,
    callServiceRpc
};
