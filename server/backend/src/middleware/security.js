'use strict';

const ALLOWED_ORIGINS = new Set([
    'http://localhost:5173',
    'http://localhost:3000',
    'http://127.0.0.1:5173',
    'http://127.0.0.1:3000'
]);

function isOriginAllowed(origin) {
    if (!origin) return false;
    if (ALLOWED_ORIGINS.has(origin)) return true;
    
    // Allow custom configured production domains from env
    if (process.env.ALLOWED_ORIGINS) {
        const customOrigins = process.env.ALLOWED_ORIGINS.split(',').map(s => s.trim());
        if (customOrigins.includes(origin)) return true;
    }
    
    // In local development, permit loopback and private LAN subnet IPs for mobile testing
    if (process.env.NODE_ENV !== 'production') {
        try {
            const url = new URL(origin);
            if (url.hostname === 'localhost' || url.hostname === '127.0.0.1' || /^192\.168\.\d{1,3}\.\d{1,3}$/.test(url.hostname) || /^10\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(url.hostname)) {
                return true;
            }
        } catch (_) {}
    }
    return false;
}

function securityHeaders(req, res, next) {
    const origin = req.headers.origin;
    if (origin && isOriginAllowed(origin)) {
        res.setHeader('Access-Control-Allow-Origin', origin);
        res.setHeader('Access-Control-Allow-Credentials', 'true');
        res.setHeader('Vary', 'Origin');
    }
    
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS, PATCH');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With, Accept');
    res.setHeader('Permissions-Policy', 'geolocation=(self)');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');

    if (req.method === 'OPTIONS') {
        return res.sendStatus(isOriginAllowed(origin) || !origin ? 204 : 403);
    }

    if (req.path === '/' || /\.(?:html|css|js)$/.test(req.path)) {
        res.setHeader('Cache-Control', 'no-store, max-age=0');
    }
    next();
}

module.exports = {
    securityHeaders
};

