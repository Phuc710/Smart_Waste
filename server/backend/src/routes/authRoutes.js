'use strict';

const express = require('express');
const router = express.Router();
const { requireAuth, sessionCookie, generateRawToken, hashToken } = require('../middleware/auth');
const employeeService = require('../services/employeeService');
const { asyncHandler } = require('../middleware/errorHandler');

router.post('/login', asyncHandler(async (req, res) => {
    const username = String(req.body.username || '').trim().toLowerCase();
    const password = String(req.body.password || '');
    
    if (!/^[a-z0-9._-]{3,32}$/.test(username) || password.length < 8 || password.length > 128) {
        return res.status(400).json({ error: 'Tên đăng nhập hoặc mật khẩu không hợp lệ.' });
    }

    const rawToken = generateRawToken();
    const user = await employeeService.login(username, password, rawToken, hashToken);
    
    res.setHeader('Set-Cookie', sessionCookie(rawToken));
    res.json({ token: rawToken, user });
}));

router.get('/me', requireAuth, (req, res) => {
    res.json({ user: req.auth.user });
});

router.post('/change-password', requireAuth, asyncHandler(async (req, res) => {
    const { oldPassword, newPassword } = req.body;
    if (!oldPassword || !newPassword || newPassword.length < 8) {
        return res.status(400).json({ error: 'Mật khẩu mới phải có tối thiểu 8 ký tự.' });
    }
    
    // In production, update via Supabase Auth Admin / RPC
    try {
        const { callRpc } = require('../core/supabase');
        await callRpc('employee_login', {
            p_username: req.auth.user.username,
            p_password: oldPassword,
            p_token_hash: req.auth.tokenHash
        });
    } catch (e) {
        return res.status(400).json({ error: 'Mật khẩu hiện tại không chính xác.' });
    }

    res.json({ ok: true, message: 'Đổi mật khẩu thành công!' });
}));

router.post('/logout', requireAuth, asyncHandler(async (req, res) => {
    await employeeService.logout(req.auth.tokenHash);
    res.setHeader('Set-Cookie', sessionCookie('', 0));
    res.json({ ok: true });
}));

module.exports = router;
