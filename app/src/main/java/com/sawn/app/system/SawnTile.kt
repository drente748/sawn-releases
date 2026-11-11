package com.sawn.app.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sawn.app.MainActivity
import com.sawn.app.R
import com.sawn.domain.block.StrictPolicy
import com.sawn.app.vpn.VpnController
import com.sawn.app.sawnContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: one tap to toggle Sawn blocking from the notification shade.
 *
 * - Turning ON needs the VPN to be consented. If it isn't yet, we send the user into the
 *   app to grant it once; afterwards the tile toggles directly.
 * - Turning OFF while a PIN is set is routed through the app so the PIN gate still applies —
 *   the tile can't be used to bypass strict mode.
 */
class SawnTile : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val enabled = applicationContext.sawnContainer.settings.masterEnabled.first()
            if (enabled) turnOff() else turnOn()
            refreshTile()
        }
    }

    private suspend fun turnOn() {
        if (VpnController.consentIntent(this) != null) {
            openApp() // consent not granted yet; enable once from the app
            return
        }
        VpnController.start(this)
        applicationContext.sawnContainer.settings.setMasterEnabled(true)
    }

    private suspend fun turnOff() {
        val settings = applicationContext.sawnContainer.settings
        // Any PIN means the in-app gate must authorize the disable.
        if (settings.protectionSnapshot().hasPin) {
            openApp()
            return
        }
        // The lock check and the write happen in one DataStore transaction.
        val outcome = settings.disableProtection(settings.trustedNow())
        if (!outcome.disabled) {
            openApp() // strict mode or a strict focus session is holding the switch
            return
        }
        // A live focus session keeps forcing blocking on; leave its tunnel up.
        if (!outcome.focusActive) VpnController.stop(this)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // the pre-34 branch has no PendingIntent overload
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private suspend fun refreshTile() {
        val settings = applicationContext.sawnContainer.settings
        val snapshot = settings.protectionSnapshot()
        val now = settings.peekNow()
        val focusActive = StrictPolicy.isFocusActive(snapshot.focusEndsAt, now)
        val lockdownActive = settings.isLockdownActiveNow()
        qsTile?.apply {
            state = if (snapshot.masterEnabled || focusActive || lockdownActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(
                    when {
                        lockdownActive -> R.string.tile_sub_lockdown
                        focusActive && snapshot.focusStrict -> R.string.tile_sub_strict_focus
                        focusActive -> R.string.tile_sub_focus
                        snapshot.strictMode && snapshot.masterEnabled -> R.string.tile_sub_strict_lock
                        snapshot.masterEnabled -> R.string.tile_sub_blocking
                        else -> R.string.tile_sub_off
                    },
                )
            }
            updateTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
