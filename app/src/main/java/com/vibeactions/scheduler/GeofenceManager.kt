package com.vibeactions.scheduler

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.vibeactions.domain.model.GeofenceTransition
import com.vibeactions.domain.model.Macro
import com.vibeactions.notifications.MacroNotificationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Registers/removes geofences for LOCATION macros via Play Services. No-ops without permission. */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifications: MacroNotificationManager
) {
    private val client = LocationServices.getGeofencingClient(context)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * (Re)registers the macro's geofence. [notifyOnFailure] posts a warning notification when the
     * fence can't be armed (permission off / Play Services error) so a LOCATION macro doesn't
     * silently stop firing; it is true for explicit user actions (save/enable) and false for the
     * bulk app-start/boot re-registration, which would otherwise notify on every launch.
     */
    fun register(macro: Macro, notifyOnFailure: Boolean = false) {
        val lat = macro.latitude ?: return
        val lng = macro.longitude ?: return
        val radius = macro.radiusMeters ?: return
        if (!hasLocationPermission()) {
            if (notifyOnFailure) notifications.notifyGeofenceError(macro, "location permission is off")
            return
        }
        val transition = macro.geofenceTransition ?: GeofenceTransition.ENTER
        val geofence = Geofence.Builder()
            .setRequestId(macro.id)
            .setCircularRegion(lat, lng, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transition)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()
        try {
            client.addGeofences(request, pendingIntent())
                .addOnFailureListener { e ->
                    // A Play Services failure (e.g. location off at OS level) is always worth
                    // surfacing — otherwise the macro appears active but never fires.
                    notifications.notifyGeofenceError(macro, e.localizedMessage)
                }
        } catch (e: SecurityException) {
            if (notifyOnFailure) notifications.notifyGeofenceError(macro, "location permission is off")
        }
    }

    fun remove(macro: Macro) {
        client.removeGeofences(listOf(macro.id))
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
