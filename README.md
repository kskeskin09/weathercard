# WeatherCard

Kotlin ve Jetpack Compose ile geliştirilen Android hava durumu uygulaması.
Android 8.0 (API 26) ve üzeri cihazları hedefler. Open-Meteo ücretsiz uç noktasını kullanır; API anahtarı ve kart gerektirmez.

## İlk sürüm

- Güncel saate ait tahmin, 48 saatlik görünüm ve 7 günlük tahmin.
- Şehir/ilçe araması ve yalnızca kullanım sırasında yaklaşık cihaz konumu.
- Open-Meteo'nun 28 WMO koduna göre özgün vektör ikonlar; gündüz/gece, yağış şiddeti, sis, donan yağış, kar ve dolu ayrımı.
- Diskte atomik olarak saklanan, koordinat bazında ayrılmış tahmin önbelleği.
- Çevrimdışıyken kayıtlı tahmini gösterme; başarısız yenilemede verileri koruma.

## Yenileme sözleşmesi

1. Seçilen konumun önbelleği yoksa **bir kez** 7 günlük saatlik + günlük veri indirilir.
2. Uygulama yeniden açıldığında diskteki kayıt okunur; otomatik tam indirme yapılmaz.
3. Aşağı çekerek veya **Yenile** ile mevcut saat dahil **toplam 6 saat** istenir. Örneğin 14.35'te 14.00–19.00.
4. İstek `start_hour` ve `end_hour` kullanır; `daily`, `current` veya `forecast_days` içermez. Altı zaman damgası doğrulanır ve sadece bunlar değiştirilir.
5. Diğer saatlik veriler, günlük özetler ve tam tahminin indirilme tarihi korunur. Günlük min/maksimumlar kısmi yenilemeden yeniden hesaplanmaz.
6. Yeni 7 günlük tahmin yalnızca ayrı **Tümünü güncelle** işlemiyle indirilir. Eski veya azalan günlük kapsam ekranda belirtilir; gizli tam yenileme yoktur.
7. Başka konuma geçilirse o konumun önbelleği kullanılır; ilk kez seçiliyorsa tam veri gerekir.

Günlük veriyi hiç yenilemeden sürekli 7 gelecek gün göstermek mümkün değildir; bu yüzden tam güncelleme kullanıcıya açık ayrı bir işlemdir.
Saatlik tahmin, istasyon ölçümü anlamına gelmez. Şehir araması ayrı bir geocoding isteği tüketir.

## Kota

İlk yükleme 14 saatlik + 9 günlük alan seçer; tek konum/varsayılan model/en fazla 14 gün için yaklaşık **2,3 kota birimi**.
Kısmi yenileme 14 saatlik alan seçer ve yaklaşık **1,4 birim** tüketir; daha az saat istemek tek başına çağrıyı ücretsiz yapmaz.
Limitler IP ve uygulama bazlı olduğundan her cihaza bağımsız kota varsayılmaz. Otomatik polling/retry yapılmaz.

Ücretsiz Open-Meteo servisi **ticari olmayan kullanım** içindir; reklam/abonelik gibi ticari kullanımda farklı lisans/plan gerekir.
Veri kaynağı: [Open-Meteo](https://open-meteo.com/), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Uygulama ham tahminlerden görsel özetler üretir. [Koşullar](https://open-meteo.com/en/terms), [API dokümanı](https://open-meteo.com/en/docs).

## Geliştirme

Android Studio ile kök klasörü aç. JDK 17, Android SDK 35 ve Gradle Wrapper 8.11.1 kullanılır.
Yerel SDK yolu `local.properties` içinde tutulur; Git'e eklenmez.

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Windows: `gradlew.bat` kullan. Derlenen APK: `app/build/outputs/apk/debug/app-debug.apk`.
GitHub Actions her push'ta test/lint/derleme yapar ve debug APK'yı workflow artifact olarak saklar.
Bu bir mağaza/release imzalı üretim paketi değildir; imzalama anahtarı repoda tutulmaz.

## Yapı

- `core`: platformdan bağımsız modeller, 6 saatlik birleştirme, testler, WMO eşlemesi.
- `app`: Open-Meteo bağlantısı, atomik disk kaydı, konum, şehir arama ve Compose ekranları/vektör ikonlar.

İlk sürümde cihaz konumu “Mevcut konum” olarak gösterilir; ters geocoding yoktur.
Google Play Services bulunmayan cihazlarda konum alınamayabilir; elle şehir araması kullanılabilir.
Konum ve önbellek uygulamanın özel alanında saklanır; hesap/analitik/reklam SDK'sı yoktur.
Hava durumu isteklerinde koordinat Open-Meteo'ya iletilir. Bulut yedeği devre dışıdır.
