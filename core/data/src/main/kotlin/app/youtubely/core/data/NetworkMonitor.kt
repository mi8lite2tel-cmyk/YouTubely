// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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

        init {
            connectivity?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) = refresh(capabilities)

                    override fun onLost(network: Network) = refresh(null)
                },
            )
        }

        private fun refresh(capabilities: NetworkCapabilities?) {
            val metered =
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)?.not() ?: false
            _state.value =
                NetworkState(
                    metered = metered,
                    systemDataSaver =
                        metered &&
                            connectivity?.restrictBackgroundStatus ==
                            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
                    powerSave = context.getSystemService<PowerManager>()?.isPowerSaveMode == true,
                )
        }

        /** Re-reads the power-save mode, which changes without a network event. */
        fun refreshPower() {
            _state.value =
                _state.value.copy(powerSave = context.getSystemService<PowerManager>()?.isPowerSaveMode == true)
        }
    }
