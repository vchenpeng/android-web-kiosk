package org.screenlite.webkiosk.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import org.screenlite.webkiosk.R
import java.io.File

@Composable
fun KioskSplashOverlay(
    cachedImageFile: File? = null,
    modifier: Modifier = Modifier,
) {
    val cachedBitmap = remember(cachedImageFile?.absolutePath) {
        cachedImageFile?.takeIf { it.exists() }?.let { file ->
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.splash_screen_background))
    ) {
        if (cachedBitmap != null) {
            Image(
                bitmap = cachedBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                alignment = Alignment.Center
            )
        } else {
            Image(
                painter = painterResource(R.drawable.welcome),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                alignment = Alignment.Center
            )
        }
    }
}
