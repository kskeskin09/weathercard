package com.weathercard.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.weathercard.app.R

@Composable
fun LocationMark(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.current_location)
    Canvas(modifier.semantics { contentDescription = description }) {
        val arrow = Path().apply {
            moveTo(size.width * .93f, size.height * .07f)
            lineTo(size.width * .62f, size.height * .95f)
            lineTo(size.width * .42f, size.height * .57f)
            lineTo(size.width * .05f, size.height * .39f)
            close()
        }
        drawPath(arrow, Color.White)
    }
}
