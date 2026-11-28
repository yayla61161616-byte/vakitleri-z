package com.example.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object QiblaCalculator {

    private const val KAABA_LATITUDE = 21.422487
    private const val KAABA_LONGITUDE = 39.826206
    private const val EARTH_RADIUS_KM = 6371.0

    fun calculateBearingDegrees(latitude: Double, longitude: Double): Double {
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = Math.toRadians(KAABA_LATITUDE)
        val lon2 = Math.toRadians(KAABA_LONGITUDE)

        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun calculateDistanceKm(latitude: Double, longitude: Double): Double {
        val dLat = Math.toRadians(KAABA_LATITUDE - latitude)
        val dLon = Math.toRadians(KAABA_LONGITUDE - longitude)
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(KAABA_LATITUDE)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Computes azimuth [0, 360) clockwise from North using a 3x3 row-major rotation matrix
     * (screen-remapped Device -> ENU World).
     *
     * Uses the screen's top edge (+Y_screen -> (R[1], R[4])) for flat and normally tilted
     * holding angles (up to ~72 deg pitch), which stays invariant under wrist roll around Y.
     * Smoothly falls back to the back-of-phone vector (-Z_screen -> (-R[2], -R[5])) when the
     * device is held nearly vertical so heading does not become noisy or flip near 90 deg pitch.
     */
    fun azimuthFromRotationMatrix(rotationMatrix: FloatArray): Double {
        if (rotationMatrix.size < 9) return 0.0
        val yEast = rotationMatrix[1].toDouble()
        val yNorth = rotationMatrix[4].toDouble()
        val rawRad = if (hypot(yEast, yNorth) >= 0.30) {
            atan2(yEast, yNorth)
        } else {
            atan2(-rotationMatrix[2].toDouble(), -rotationMatrix[5].toDouble())
        }
        return (Math.toDegrees(rawRad) + 360.0) % 360.0
    }

    fun shortestAngleDiffDegrees(fromDeg: Float, toDeg: Float): Float {
        var diff = (toDeg - fromDeg) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return diff
    }

    fun cardinalDirectionTurkish(bearingDegrees: Double): String {
        val normalized = ((bearingDegrees % 360.0) + 360.0) % 360.0
        val index = ((normalized + 22.5) / 45.0).toInt() % 8
        return when (index) {
            0 -> "Kuzey"
            1 -> "Kuzeydoğu"
            2 -> "Doğu"
            3 -> "Güneydoğu"
            4 -> "Güney"
            5 -> "Güneybatı"
            6 -> "Batı"
            else -> "Kuzeybatı"
        }
    }

    fun formatBearing(bearingDegrees: Double): String {
        val rounded = (bearingDegrees * 10.0).roundToInt() / 10.0
        return "$rounded° ${cardinalDirectionTurkish(bearingDegrees)}"
    }
}
