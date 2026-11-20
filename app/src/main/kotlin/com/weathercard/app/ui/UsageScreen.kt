package com.weathercard.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weathercard.core.UsageSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Muted = Color(0xFF9FAFC8)

@Composable
fun UsageScreen(summary: UsageSummary?, error: String?, onBack: () -> Unit, onReload: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = Color(0xFF0B1324)) {
        LazyColumn(Modifier.statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹ Geri") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onReload) { Text("Sayacı yenile") }
                }
                Text("API kullanımı", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("Bu cihazın tahmini Open-Meteo tüketimi", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF253851))) {
                    Text("Bu sayaç sağlayıcının gerçek kalan kotası değildir. Diğer cihazları ve ortak IP trafiğini göremez. Gönderilmeye çalışılan isteklerden hesaplanır; başarısız istekler de dahildir.", Modifier.padding(16.dp), fontSize = 12.sp)
                }
            }
            error?.let { item { Text(it, color = Color(0xFFFFB8B8)) } }
            if (summary == null) {
                item { if (error == null) CircularProgressIndicator() }
            } else {
                item { QuotaCard("Bu saat", summary.hour.quotaTenths, 5_000, summary.hour.weatherRequests) }
                item { QuotaCard("Bugün", summary.day.quotaTenths, 10_000, summary.day.weatherRequests) }
                item { QuotaCard("Bu ay", summary.month.quotaTenths, 300_000, summary.month.weatherRequests) }
                item {
                    Text("Dönemler UTC takvimine göredir. Yüzdeler ücretsiz planın yayımlanmış limitlerine göre yaklaşık karşılaştırmadır; anlık kota erişimini garanti etmez.", color = Muted, fontSize = 11.sp)
                }
                item {
                    Column(Modifier.fillMaxWidth().background(Color(0xFF142239), RoundedCornerShape(22.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Bu ayın ayrıntıları", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Detail("Tam tahmin isteği", summary.month.full.toString())
                        Detail("6 saatlik yenileme", summary.month.partial.toString())
                        Detail("Şehir araması", summary.month.searches.toString())
                        HorizontalDivider(color = Color.White.copy(alpha = .1f))
                        Detail("Tüm zamanlar · hava durumu", "${units(summary.lifetime.quotaTenths)} birim")
                        Text("Şehir araması farklı bir uç noktadır; yukarıdaki hava durumu kota tahminine eklenmez.", color = Muted, fontSize = 11.sp)
                    }
                }
                item {
                    Text("Nasıl hesaplanıyor?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("7 günlük tam tahmin: 2,3 birim\n6 saatlik yenileme: 1,4 birim\nÖnbellekten gösterim: 0 birim", color = Muted, fontSize = 13.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("Kayıt başlangıcı: ${Instant.ofEpochSecond(summary.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.forLanguageTag("tr-TR")))}\nKayıt cihazda saklanır. Uygulama verisini silmek veya uygulamayı kaldırmak bu sayacı da siler.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
            item {
                val uri = LocalUriHandler.current
                TextButton(onClick = { uri.openUri("https://open-meteo.com/en/pricing") }) { Text("Open-Meteo kota açıklaması ↗", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun QuotaCard(title: String, usedTenths: Long, limit: Long, requests: Long) {
    val ratio = usedTenths.toDouble() / (limit * 10)
    Column(Modifier.fillMaxWidth().background(Color(0xFF142239), RoundedCornerShape(22.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(String.format(Locale.forLanguageTag("tr-TR"), "%%%.2f", ratio * 100), color = Muted, fontSize = 12.sp)
        }
        Text("${units(usedTenths)} / ${String.format(Locale.forLanguageTag("tr-TR"), "%,d", limit)} birim", fontSize = 23.sp)
        LinearProgressIndicator(progress = { ratio.coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth(), color = if (ratio >= .8) Color(0xFFFFCE86) else Color(0xFFA6CCFF))
        Text("$requests hava durumu isteği · tahmini tüketim", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, fontSize = 12.sp)
    }
}

private fun units(tenths: Long) = String.format(Locale.forLanguageTag("tr-TR"), "%,.1f", tenths / 10.0)

