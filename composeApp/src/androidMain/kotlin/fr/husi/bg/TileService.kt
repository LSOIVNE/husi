package fr.husi.bg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import fr.husi.compose.theme.getPrimaryColor
import fr.husi.lib.R
import fr.husi.database.DataStore
import fr.husi.database.SagerDatabase
import fr.husi.ktx.onIoDispatcher
import fr.husi.repository.repo
import fr.husi.resources.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import android.service.quicksettings.TileService as BaseTileService

@RequiresApi(24)
class TileService : BaseTileService() {
    private val iconRest by lazy { Icon.createWithResource(this, R.drawable.ic_service_rest) }
    private var iconConnectedColor: Int? = null
    private var iconConnected: Icon? = null

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    override fun attachBaseContext(newBase: Context) {
        val languageContext = ContextCompat.getContextForLanguage(newBase)
        super.attachBaseContext(languageContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(this::toggle) else toggle()
    }

    private fun updateTile(serviceState: ServiceState, profileName: String?) {
        qsTile?.apply {
            label = null
            when (serviceState) {
                ServiceState.Connecting -> {
                    icon = iconRest
                    state = Tile.STATE_ACTIVE
                }

                ServiceState.Connected -> {
                    icon = getConnectedIcon()
                    label = profileName
                    state = Tile.STATE_ACTIVE
                }

                ServiceState.Stopping -> {
                    icon = iconRest
                    state = Tile.STATE_UNAVAILABLE
                }

                // Stopped
                else -> {
                    icon = iconRest
                    state = Tile.STATE_INACTIVE
                }
            }
            label = label ?: runBlocking {
                repo.getString(Res.string.app_name)
            }
            updateTile()
        }
    }

    private fun getConnectedIcon(): Icon {
        val color = getPrimaryColor()
        val cachedIcon = iconConnected
        if (cachedIcon != null && iconConnectedColor == color) return cachedIcon

        val drawable = requireNotNull(ContextCompat.getDrawable(this, R.drawable.ic_service_active)).mutate()
        drawable.setTint(color)
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return Icon.createWithBitmap(bitmap).also {
            iconConnected = it
            iconConnectedColor = color
        }
    }

    private fun toggle() {
        scope.launch {
            val state = DataStore.serviceState
            when {
                state.canStop -> {
                    updateTile(ServiceState.Stopping, null)
                    repo.stopService()
                }

                state == ServiceState.Stopped || state == ServiceState.Idle -> {
                    updateTile(ServiceState.Connecting, null)
                    repo.startService()
                }
            }
        }
    }

    private fun refreshTile() {
        scope.launch {
            val state = DataStore.serviceState
            val profileName = if (state.connected) {
                onIoDispatcher {
                    val profileId = DataStore.currentProfile
                    if (profileId <= 0L) {
                        null
                    } else {
                        SagerDatabase.proxyDao.getById(profileId)?.let {
                            it.displayNameForService()
                        }
                    }
                }
            } else {
                null
            }
            updateTile(state, profileName)
        }
    }
}
