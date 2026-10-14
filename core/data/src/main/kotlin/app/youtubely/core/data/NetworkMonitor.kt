// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the current connection and the device's saving modes look like. */
data class NetworkState(
    /** Mobile data or a Wi-Fi marked as metered. */
    val metered: Boolean = false,
    /** Android's Data Saver restricts this app's background data. */
    val systemDataSaver: Boolean = false,
    val powerSave: Boolean = false,
)

/** Follows the active network; one place for everything that needs to know "is this mobile data?". */
@Singleton
class NetworkMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val _state = MutableStateFlow(NetworkState())
        val state: StateFlow<NetworkState> = _state.asStateFlow()

        /** Read without a flow; used from the traffic counter on every response chunk. */
        val isMetered: Boolean get() = _state.value.metered

        private val connectivity = context.getSystemService<ConnectivityManager>()
        private val power = context.getSystemService<PowerManager>()

        @Volatile
        private var metered = false

        init {
            connectivity?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                        refresh()
                    }

                    override fun onLost(network: Network) {
                        metered = false
                        refresh()
                    }
                },
            )
            // Battery Saver and Android's Data Saver change without any network event.
            val modes =
                IntentFilter().apply {
                    addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                    addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
                }
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) = refresh()
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, modes, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag") // Only system broadcasts; the flag exists from 33.
                context.registerReceiver(receiver, modes)
            }
            refresh()
        }

        private fun refresh() {
            val restricted =
                connectivity?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            _state.value =
                NetworkState(
                    metered = metered,
                    systemDataSaver = metered && restricted,
                    powerSave = power?.isPowerSaveMode == true,
                )
        }
    }
