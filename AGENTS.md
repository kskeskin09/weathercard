# WeatherCard çalışma kuralları

- Android uygulamasını Kotlin ve Jetpack Compose ile geliştir.
- Kullanıcının tercihi: **bütün derlemeler GitHub Actions üzerinde çalışır**. Yerelde Gradle/Android derlemesi veya yerel APK üretimi çalıştırma. Test, lint, APK ve emülatör kontrolleri için repodaki workflow'ları kullan ve sonuçlarını izle.
- GitHub reposu private kalır. Açık kaynak/public yayın izni verilmiş değildir.
- `main` üzerindeki başarılı sürümler tek `nightly` prerelease'i günceller. Her commit veya tarih için ayrı release/tag üretme. Stable release kullanıcı ayrıca istediğinde oluşturulur.
- Taban SemVer `gradle.properties` içindedir; nightly sürüm adı ve artan Android `versionCode` CI'da üretilir. Android imzalama anahtarını değiştirme/yeniden üretme; mevcut GitHub Secrets değerlerini kullan. Anahtar/parolaları commit etme veya loglama.
- Normal yenileme mevcut saat dahil **toplam 6 saatlik** tahmini ister ve sadece bu saatlerin önbelleğini değiştirir. Diğer saatler ve günlük veriler tekrar istenmez/değiştirilmez. Hata halinde kayıtlı tahmin korunur; otomatik tam yenilemeye geçilmez.
- Tam 7 günlük veri yalnızca önbelleksiz yeni konumda veya açıkça seçilen tam güncellemede indirilir. Eski günlük verinin tarihini kullanıcıya göster.
- Önbellek diskte ve konuma göre ayrılmış olmalı. Yaklaşık konumla çalışmayı destekle; kullanıcı kesin konum izni verirse tek seferlik GPS de kullanılabilir. İzin yalnızca kullanım sırasındadır; arka plan takibi ekleme.
- WMO kodlarının tamamına uygun, çevrimdışı çalışan vektör ikonları ve erişilebilir açıklamalarını koru.
- Modern tam ekran arka planı mevcut saat/hava koşulu ve gündüz/geceye göre değişir. Üst bölümde saat, hava durumu, derece, hissedilen, yağış ihtimali, rüzgâr, nem ve UV görünür. Kota sayacı yalnızca bu cihazın tahminidir; sağlayıcının kesin kalan kotası gibi sunma.
- Open-Meteo atfını ve ticari olmayan ücretsiz kullanım koşulunu koru. Her kullanıcıya ayrı ücretsiz kota garantisi verme.
