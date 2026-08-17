'use strict';

const logger = require('../core/logger');

function asyncHandler(fn) {
    return (req, res, next) => {
        Promise.resolve(fn(req, res, next)).catch(next);
    };
}

function errorHandler(err, req, res, _next) {
    logger.error('API Error', `${req.method} ${req.originalUrl}:`, err.message || err);
    
    const statusCode = err.status || err.statusCode || (err.message && err.message.includes('Chưa cài đặt bảng') ? 503 : 500);
    const message = err.message ? err.message.replace(/^\d+\s*/, '') : 'Đã xảy ra lỗi máy chủ.';
    
    if (res.headersSent) return;
    res.status(statusCode).json({ error: message });
}

module.exports = {
    asyncHandler,
    errorHandler
};
