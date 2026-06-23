package org.screenlite.webkiosk.components

import android.app.Activity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.screenlite.webkiosk.data.KioskSettingsFactory

@Composable
fun MainScreen(
    activity: Activity,
    modifier: Modifier,
    onDismissSplash: () -> Unit,
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf<String?>(null) }
    val kioskSettings = remember { KioskSettingsFactory.get(context) }

    LaunchedEffect(Unit) {
        kioskSettings.getStartUrl().collect { newUrl ->
            if (newUrl.isNotBlank()) {
                url = newUrl
            }
        }
    }

    url?.let { startUrl ->
        key(startUrl) {
            WebViewComponent(
                url = startUrl,
                activity = activity,
                modifier = modifier,
                onDismissSplash = onDismissSplash,
            )
        }
    }
}