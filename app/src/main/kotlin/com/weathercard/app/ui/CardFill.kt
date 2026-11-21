package com.weathercard.app.ui

import androidx.compose.ui.graphics.Color

/**
 * Shared card fill. A muted slate glass that fades the animated sky behind every card
 * (darker under the bright noon sky, slightly lifted at night) so card text stays calm and readable.
 */
internal val CardFill = Color(0xFF2B3B52).copy(alpha = .45f)
