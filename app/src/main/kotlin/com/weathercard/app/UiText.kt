package com.weathercard.app

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.IOException

/**
 * User-visible text kept as a resource id until it is drawn, so it always follows the current
 * system language. [locationHelp] adds shortcuts to the location and app-permission settings.
 */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList(), val locationHelp: Boolean = false) {
    fun resolve(context: Context): String = context.getString(id, *args.toTypedArray())
}

@Composable
fun UiText.resolve(): String = resolve(LocalContext.current)

/** An error whose message is meant for the user. Other exceptions get a generic message. */
class UserFacingException(val text: UiText) : IOException(text.id.toString())

/** Network failures keep the cache; anything else means the service answered with unusable data. */
internal fun Throwable.toUiText(): UiText = when (this) {
    is UserFacingException -> text
    is IOException -> UiText(R.string.error_connection)
    else -> UiText(R.string.error_bad_response)
}
