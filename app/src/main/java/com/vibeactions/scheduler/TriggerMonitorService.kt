package com.vibeactions.scheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.util.stateTriggerMatches
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that watches device-state changes (power, Bluetooth, Wi-Fi) and fires matching
 * CHARGING/BLUETOOTH/WIFI macros. It only runs while at least one such macro is enabled (see
 * [TriggerMonitor]); users who don't use these triggers never see its notification. A foreground
 * service is required because these broadcasts aren't delivered to manifest receivers on API 26+.
 */
@AndroidEntryPoint
class TriggerMonitorService : android.app.Service() {
    @Inject lateinit var repo: MacroRepository
    @Inject lateinit var firer: MacroFirer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiversRegistered = false
    private var networkCallbackRegistered = false

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> fireFor(TriggerType.CHARGING, connected = true, target = null)
                Intent.ACTION_POWER_DISCONNECTED -> fireFor(TriggerType.CHARGING, connected = false, target = null)
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(
                intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
            )
            // Reading the address needs BLUETOOTH_CONNECT on API 31+; without it, only "any device"
            // macros match (address comes back null/redacted).
            val address = runCatching { device?.address }.getOrNull()
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> fireFor(TriggerType.BLUETOOTH, connected = true, target = address)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> fireFor(TriggerType.BLUETOOTH, connected = false, target = address)
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) =
            fireFor(TriggerType.WIFI, connected = true, target = currentWifiSsid())

        // A dropped network doesn't tell us which SSID was lost, so only "any network" macros match.
        override fun onLost(network: Network) =
            fireFor(TriggerType.WIFI, connected = false, target = null)
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        registerMonitors()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure foreground state even if the process was recreated by the system.
        startInForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (receiversRegistered) {
            runCatching { unregisterReceiver(powerReceiver) }
            runCatching { unregisterReceiver(bluetoothReceiver) }
            receiversRegistered = false
        }
        if (networkCallbackRegistered) {
            runCatching {
                getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback)
            }
            networkCallbackRegistered = false
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun registerMonitors() {
        if (receiversRegistered) return
        val powerFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val btFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        // System broadcasts → NOT_EXPORTED (required flag on API 34 for dynamic receivers).
        ContextCompat.registerReceiver(this, powerReceiver, powerFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, bluetoothReceiver, btFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiversRegistered = true

        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm?.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistered = true
        }
    }

    @Suppress("DEPRECATION")
    private fun currentWifiSsid(): String? = runCatching {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifi?.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }.getOrNull()

    private fun fireFor(type: TriggerType, connected: Boolean, target: String?) {
        scope.launch {
            repo.getEnabledByTrigger(type).forEach { macro ->
                if (stateTriggerMatches(macro.triggerTarget, macro.triggerOnConnect, target, connected)) {
                    firer.fire(macro.id, enforceOncePerDay = false)
                }
            }
        }
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Trigger monitoring", NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Runs while a charging/Bluetooth/Wi-Fi macro is active"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Automatiq is watching for triggers")
            .setContentText("Charging / Bluetooth / Wi-Fi macros")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "trigger_monitor"
        private const val NOTIFICATION_ID = 42
    }
}
