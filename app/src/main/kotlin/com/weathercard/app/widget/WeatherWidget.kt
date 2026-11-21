package com.weathercard.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.weathercard.app.data.SavedPlacesPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.weathercard.app.MainActivity
import com.weathercard.app.R
import com.weathercard.app.WeatherApplication
import java.time.Instant
import java.util.Calendar

private val White = ColorProvider(Color.White)
private val Muted = ColorProvider(Color(0xFFBCD0E5))
private val Accent = ColorProvider(Color(0xFFD8EDFF))
private val Compact = DpSize(110.dp, 48.dp)
private val Wide = DpSize(250.dp, 100.dp)

/**
 * Home-screen card for the place selected in the app. It only reads the disk cache: it never
 * downloads, never asks for location, and is refreshed when the app saves new data and at each
 * full hour so that the shown hour moves forward from the cached hourly forecast.
 */
class WeatherWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(Compact, Wide))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val forecast = loadForecast(context)
        provideContent { WidgetContent(forecast) }
    }

    private suspend fun loadForecast(context: Context): WidgetForecast? {
        val app = context.applicationContext as WeatherApplication
        // The widget shows the city last visible on the main screen.
        val place = runCatching { SavedPlacesPreferences.read(context.getSharedPreferences("settings", 0)).current }.getOrNull() ?: return null
        val snapshot = runCatching { app.forecastStore.read(place) }.getOrNull() ?: return null
        return WidgetForecast.from(snapshot, Instant.now())
    }

    companion object {
        /** Re-renders every placed widget from the cache. Safe to call when no widget exists. */
        suspend fun refresh(context: Context) {
            if (GlanceAppWidgetManager(context).getGlanceIds(WeatherWidget::class.java).isEmpty()) return
            WeatherWidget().updateAll(context)
        }
    }
}

@Composable
private fun WidgetContent(forecast: WidgetForecast?) {
    val context = LocalContext.current
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density.coerceAtMost(2f)
    val wide = size.width >= Wide.width && size.height >= Wide.height
    val background = remember(forecast?.weatherCode, forecast?.isDay, size) {
        WidgetArt.background(forecast?.weatherCode, forecast?.isDay ?: true,
            (size.width.value * density).toInt(), (size.height.value * density).toInt(), 22f * density)
    }
    val spoken = forecast?.let { "${it.placeName}, ${widgetDegrees(it.temperature)}, ${context.getString(it.descriptionRes)}" }
        ?: context.getString(R.string.app_name)
    Box(
        GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>())
            .semantics { contentDescription = spoken },
    ) {
        Image(ImageProvider(background), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize())
        when {
            forecast == null -> EmptyContent()
            wide -> WideContent(forecast, density)
            else -> CompactContent(forecast, density)
        }
    }
}

@Composable
private fun EmptyContent() {
    Column(GlanceModifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(LocalContext.current.getString(R.string.app_name), style = TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        Text(LocalContext.current.getString(R.string.widget_empty), maxLines = 2,
            style = TextStyle(color = Muted, fontSize = 11.sp))
    }
}

@Composable
private fun PlaceTitle(forecast: WidgetForecast, density: Float, fontSize: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (forecast.isCurrentLocation) {
            val mark = remember(density) { WidgetArt.locationMark((12 * density).toInt()) }
            Image(ImageProvider(mark), contentDescription = LocalContext.current.getString(R.string.current_location), modifier = GlanceModifier.size(11.dp))
            Spacer(GlanceModifier.width(5.dp))
        }
        Text(forecast.placeName, maxLines = 1, style = TextStyle(color = White, fontSize = fontSize.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun Glyph(forecast: WidgetForecast, density: Float, sizeDp: Int) {
    val bitmap = remember(forecast.weatherCode, forecast.isDay, density, sizeDp) {
        WidgetArt.glyph(forecast.weatherCode, forecast.isDay, (sizeDp * density).toInt())
    }
    Image(ImageProvider(bitmap), contentDescription = LocalContext.current.getString(forecast.descriptionRes), modifier = GlanceModifier.size(sizeDp.dp))
}

@Composable
private fun WideContent(forecast: WidgetForecast, density: Float) {
    Row(GlanceModifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            PlaceTitle(forecast, density, 14f)
            Text(widgetDegrees(forecast.temperature), style = TextStyle(color = White, fontSize = 46.sp, fontWeight = FontWeight.Bold))
            Text(LocalContext.current.getString(forecast.descriptionRes), maxLines = 1, style = TextStyle(color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(forecast, density, 64)
            if (forecast.maximum != null || forecast.minimum != null) {
                Spacer(GlanceModifier.height(4.dp))
                Text("${widgetDegrees(forecast.maximum)} / ${widgetDegrees(forecast.minimum)}",
                    style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun CompactContent(forecast: WidgetForecast, density: Float) {
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(forecast, density, 34)
            Spacer(GlanceModifier.width(6.dp))
            Text(widgetDegrees(forecast.temperature), style = TextStyle(color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold))
        }
        PlaceTitle(forecast, density, 11f)
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scheduleNextHour(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.getSystemService(AlarmManager::class.java)?.cancel(hourlyIntent(context))
    }

    private fun hourlyIntent(context: Context): PendingIntent {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetReceiver::class.java))
        val intent = Intent(context, WeatherWidgetReceiver::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Non-waking, inexact alarm just after the next full hour: no battery wake-ups, no network. */
    private fun scheduleNextHour(context: Context) {
        val next = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 5); set(Calendar.MILLISECOND, 0)
        }
        context.getSystemService(AlarmManager::class.java)?.set(AlarmManager.RTC, next.timeInMillis, hourlyIntent(context))
    }
}
