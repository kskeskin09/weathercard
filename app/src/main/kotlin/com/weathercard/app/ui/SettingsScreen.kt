package com.weathercard.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weathercard.app.R

private val Muted = Color(0xFF9FAFC8)

@Composable
fun SettingsScreen(locationPage: Boolean, onLocationPageChange: (Boolean) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = Color(0xFF0B1324)) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Text(stringResource(R.string.settings_title), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = .07f), RoundedCornerShape(20.dp))
                    .toggleable(locationPage, role = Role.Switch, onValueChange = onLocationPageChange)
                    .padding(16.dp).testTag("location-page-setting"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.settings_location_page), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_location_page_note), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Switch(checked = locationPage, onCheckedChange = null)
            }
        }
    }
}
