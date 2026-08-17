'use strict';

const path = require('path');
const fs = require('fs');

// Set Vietnam standard timezone (UTC+7) for entire server process
process.env.TZ = 'Asia/Ho_Chi_Minh';

// Load .env file manually if exists
const localEnvPath = path.resolve(__dirname, '../../.env');
if (fs.existsSync(localEnvPath)) {
    for (const line of fs.readFileSync(localEnvPath, 'utf8').split(/\r?\n/)) {
        const match = line.match(/^\s*([A-Z][A-Z0-9_]*)\s*=\s*(.*)\s*$/);
        if (!match || process.env[match[1]] !== undefined) continue;
        process.env[match[1]] = match[2].replace(/^['"]|['"]$/g, '');
    }
}

const env = {
    NODE_ENV: process.env.NODE_ENV || 'development',
    IS_PROD: process.env.NODE_ENV === 'production',
    HTTP_PORT: Number(process.env.HTTP_PORT || 3000),
    MQTT_PORT: Number(process.env.MQTT_PORT || 1883),
    SUPABASE_URL: process.env.SUPABASE_URL || 'https://zwrapaqlozdkbkblohcq.supabase.co',
    SUPABASE_ANON_KEY: process.env.SUPABASE_ANON_KEY || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp3cmFwYXFsb3pka2JrYmxvaGNxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODY1NDUwMjksImV4cCI6MjEwMjEyMTAyOX0.SoUONgNnMtlTL0dTzenRya_VKPlOg2zcp1dfwotq4K0',
    SUPABASE_SERVICE_ROLE_KEY: process.env.SUPABASE_SERVICE_ROLE_KEY || ''
};

module.exports = env;
