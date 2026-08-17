'use strict';

const logger = require('../core/logger');

async function calculateOsrmRoute(coordinates) {
    if (!coordinates || coordinates.length < 2) return null;
    
    const coordinatePath = coordinates.map((point) => `${Number(point[0])},${Number(point[1])}`).join(';');
    const tripUrl = `https://router.project-osrm.org/trip/v1/driving/${coordinatePath}?source=first&destination=last&roundtrip=false&geometries=geojson&overview=full&steps=false`;
    const routeUrl = `https://router.project-osrm.org/route/v1/driving/${coordinatePath}?geometries=geojson&overview=full&steps=false`;

    try {
        const routeResponse = await fetch(coordinates.length > 2 ? tripUrl : routeUrl, { 
            signal: AbortSignal.timeout(15000) 
        });
        const data = await routeResponse.json();
        if (data.code === 'Ok' && (data.trips?.length || data.routes?.length)) {
            const trip = data.trips ? data.trips[0] : data.routes[0];
            return {
                provider: 'osrm',
                distanceMeters: trip.distance,
                durationSeconds: trip.duration,
                coordinates: trip.geometry.coordinates,
                optimizedOrder: data.waypoints?.map((waypoint) => waypoint.waypoint_index) || []
            };
        }
    } catch (err) {
        logger.warn('Routing', 'OSRM server unreachable, using fallback route estimation:', err.message);
    }

    // Fallback: Haversine distance with 1.35 urban winding factor
    const straightLineCoords = coordinates.map(c => [Number(c[0]), Number(c[1])]);
    let totalDistMeters = 0;
    for (let i = 0; i < straightLineCoords.length - 1; i++) {
        const dLat = (straightLineCoords[i + 1][1] - straightLineCoords[i][1]) * 111320;
        const dLng = (straightLineCoords[i + 1][0] - straightLineCoords[i][0]) * 111320 * Math.cos(straightLineCoords[i][1] * Math.PI / 180);
        totalDistMeters += Math.sqrt(dLat * dLat + dLng * dLng) * 1.35;
    }

    return {
        provider: 'fallback_estimate',
        distanceMeters: Math.round(totalDistMeters),
        durationSeconds: Math.round((totalDistMeters / 30000) * 3600),
        coordinates: straightLineCoords,
        optimizedOrder: coordinates.map((_, idx) => idx)
    };
}

module.exports = {
    calculateOsrmRoute
};
