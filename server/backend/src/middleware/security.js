'use strict';

function securityHeaders(req, res, next) {
    const origin = req.headers.origin;
    if (origin) {
        res.setHeader('Access-Control-Allow-Origin', origin);
        res.setHeader('Access-Control-Allow-Credentials', 'true');
    } else {
        res.setHeader('Access-Control-Allow-Origin', '*');
    }
    
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS, PATCH');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With, Accept');
    res.setHeader('Permissions-Policy', 'geolocation=(self)');

    if (req.method === 'OPTIONS') {
        return res.sendStatus(204);
    }

    if (req.path === '/' || /\.(?:html|css|js)$/.test(req.path)) {
        res.setHeader('Cache-Control', 'no-store, max-age=0');
    }
    next();
}

module.exports = {
    securityHeaders
};
