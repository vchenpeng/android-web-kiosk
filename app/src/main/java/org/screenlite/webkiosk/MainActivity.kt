package org.screenlite.webkiosk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.screenlite.webkiosk.app.FullScreenHelper
import org.screenlite.webkiosk.app.IdleBrightnessController
import org.screenlite.webkiosk.app.NotificationPermissionHelper
import org.screenlite.webkiosk.app.SplashController
import org.screenlite.webkiosk.app.StayOnTopServiceStarter
import org.screenlite.webkiosk.app.TapUnlockHandler
import org.screenlite.webkiosk.components.KioskSplashOverlay
import org.screenlite.webkiosk.components.MainScreen
import org.screenlite.webkiosk.components.TouchKioskInputOverlay
import org.screenlite.webkiosk.components.TvKioskInputOverlay
import org.screenlite.webkiosk.data.KioskSettingsFactory
import org.screenlite.webkiosk.ui.theme.ScreenliteWebKioskTheme
import org.screenlite.webkiosk.ui.theme.isTvDevice

class MainActivity : ComponentActivity() {
    private lateinit var unlockHandler: TapUnlockHandler
    lateinit var idleController: IdleBrightnessController
    lateinit var splashController: SplashController
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        splashController = SplashController()
        // 系统 Splash 在首帧绘制后立即退出，避免挡住 Compose 的 KioskSplashOverlay
        installSplashScreen()
        super.onCreate(savedInstanceState)

        FullScreenHelper.enableImmersiveMode(this.window)
        StayOnTopServiceStarter.ensureRunning(this)

        unlockHandler = TapUnlockHandler {
            openSettings()
        }

        val settings = KioskSettingsFactory.get(this)
        idleController = IdleBrightnessController(this, settings)

        setContent {
            ScreenliteWebKioskTheme {
                AppContent(unlockHandler, this)
            }
        }
    }

    fun dismissSplash() {
        splashController.dismiss()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        idleController.onUserInteraction()
        return super.dispatchTouchEvent(ev)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onPause() {
        super.onPause()
        idleController.stop()
    }

    override fun onResume() {
        super.onResume()
        idleController.start()
    }
}

@Composable
fun AppContent(unlockHandler: TapUnlockHandler, activity: MainActivity) {
    val context = LocalContext.current
    val idleController = remember { activity.idleController }
    val isIdleMode by idleController.isIdleMode.collectAsState()
    val splashVisible by activity.splashController.visible

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("MainActivity", "Notification permission granted: $isGranted")
    }

    LaunchedEffect(isIdleMode) {
        Log.d("IdleDebug", "Compose: isIdleMode state changed to: $isIdleMode")
    }

    LaunchedEffect(Unit) {
        if (!NotificationPermissionHelper.hasPermission(context)) {
            NotificationPermissionHelper.requestPermission(permissionLauncher)
        }
    }

    val isTv = isTvDevice()
    val dismissSplash = remember(activity) { { activity.dismissSplash() } }

    Box(Modifier.fillMaxSize()) {
        MainScreen(
            activity = activity,
            modifier = Modifier.fillMaxSize(),
            onDismissSplash = dismissSplash,
        )

        if (isTv) {
            TvKioskInputOverlay(onTap = {
                idleController.onUserInteraction()
                unlockHandler.registerTap()
            })
        } else {
            TouchKioskInputOverlay(
                onTap = { unlockHandler.registerTap() },
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        if (splashVisible) {
            KioskSplashOverlay()
        }

        val idleFocusRequester = remember { FocusRequester() }

        if (isIdleMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black)
                    .clickable {
                        idleController.onUserInteraction()
                    }
                    .focusable()
                    .focusRequester(idleFocusRequester)
                    .onKeyEvent {
                        if (it.key == Key.DirectionCenter &&
                            it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            idleController.onUserInteraction()
                            true
                        } else {
                            false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {}
        }
    }
}