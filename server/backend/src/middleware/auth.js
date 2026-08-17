'use strict';

const crypto = require('crypto');
const env = require('../config/env');
const { SESSION_COOKIE_NAME, SESSION_MAX_AGE } = require('../config/constants');
const { callRpc } = require('../core/supabase');

function parseCookies(header = '') {
    return header.split(';').reduce((cookies, part) => {
        const separator = part.indexOf('=');
        if (separator < 0) return cookies;
        const key = part.slice(0, separator).trim();
        const value = part.slice(separator + 1).trim();
        if (key) cookies[key] = decodeURIComponent(value);
        return cookies;
    }, {});
}

function hashToken(token) {
    return crypto.createHash('sha256').update(token).digest('hex');
}

function generateRawToken() {
    return crypto.randomBytes(32).toString('hex');
}

function sessionCookie(token, maxAge = SESSION_MAX_AGE) {
    const secure = env.IS_PROD ? '; Secure' : '';
    return `${SESSION_COOKIE_NAME}=${encodeURIComponent(token)}; HttpOnly; SameSite=Strict; Path=/; Max-Age=${maxAge}${secure}`;
}

async function getSessionUser(rawToken) {
    if (!/^[a-f0-9]{64}$/.test(String(rawToken || ''))) return null;
    const rows = await callRpc('employee_current', { p_token_hash: hashToken(rawToken) });
    return Array.isArray(rows) && rows.length ? rows[0] : null;
}

async function requireAuth(request, response, next) {
    try {
        const rawToken = parseCookies(request.headers.cookie)[SESSION_COOKIE_NAME];
        const user = await getSessionUser(rawToken);
        if (!user) {
            return response.status(401).json({ error: 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn.' });
        }
        request.auth = { user, rawToken, tokenHash: hashToken(rawToken) };
        next();
    } catch (error) {
        response.status(503).json({ error: 'Chưa cài đặt bảng tài khoản. Hãy chạy lại file supabase_schema.sql.' });
    }
}

function requireAdmin(request, response, next) {
    if (request.auth.user.role !== 'admin') {
        return response.status(403).json({ error: 'Chỉ quản trị viên được thực hiện thao tác này.' });
    }
    next();
}

module.exports = {
    parseCookies,
    hashToken,
    generateRawToken,
    sessionCookie,
    getSessionUser,
    requireAuth,
    requireAdmin
};
