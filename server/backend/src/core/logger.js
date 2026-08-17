'use strict';

function timestamp() {
    return new Date().toLocaleTimeString('vi-VN', { hour12: false });
}

const logger = {
    info(tag, ...args) {
        console.log(`[${timestamp()}][${tag}]`, ...args);
    },
    warn(tag, ...args) {
        console.warn(`[${timestamp()}][${tag}]`, ...args);
    },
    error(tag, ...args) {
        console.error(`[${timestamp()}][${tag}]`, ...args);
    }
};

module.exports = logger;
