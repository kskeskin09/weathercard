# WeatherCard

Kotlin ve Jetpack Compose ile geliştirilen Android hava durumu uygulaması.
Android 8.0 (API 26) ve üzeri cihazları hedefler. Open-Meteo ücretsiz uç noktasını kullanır; API anahtarı ve kart gerektirmez.

## İlk sürüm

- Güncel saate ait tahmin, önbellekteki tüm saatler için yatay kaydırılabilen sıcaklık eğrisi ve 7 günlük tahmin.
- Rüzgâr yönü/hızı ve pusula; gün doğumu/batımı saatleri ile güneşin gün içindeki konumunu gösteren eğri.
- Grafik, rüzgâr ve güneş kartları yuvarlatılmış, yarı saydam yüzeyler kullanır.
- Uygulamaya gömülü Inter yazı tipi; kalın başlıklar/değerler ve okunabilir küçük metinler. Font çevrimdışı çalışır; SIL Open Font License uygulama paketinde yer alır.
- Şehir/ilçe araması; yaklaşık konum ve kullanıcının izin vermesi halinde tek seferlik kesin GPS konumu.
- Referansa yakın mavi/gri tam ekran tema; üstte ortalı konum adı, mevcut konum işareti, büyük merkezî sıcaklık ve hemen altında hava durumu.
- Sıcaklığın altında yatay saatlik eğri; aşağı kaydırınca solda rüzgâr/güneş, sağda nem/hissedilen/UV/basınç/yağış ayrıntıları.
- Google konum servisi boş sonuç verdiğinde Android ağ/GPS sağlayıcılarıyla devam etme, güncel kayıtlı konumu kullanma ve açık ayar yönlendirmeleri.
- Open-Meteo'nun 28 WMO koduna göre özgün vektör ikonlar; gündüz/gece, yağış şiddeti, sis, donan yağış, kar ve dolu ayrımı.
- Diskte atomik olarak saklanan, koordinat bazında ayrılmış tahmin önbelleği.
- Çevrimdışıyken kayıtlı tahmini gösterme; başarısız yenilemede verileri koruma.
- Saatlik/günlük/aylık tahmini API kota kullanımı ve cihazda kalıcı istek sayacı.

## Kota sayfası

Üç nokta menüsündeki **Kota** sayfası bu cihazın gönderdiği istekleri saklar. Tam tahmin 2,3 ve kısmi yenileme 1,4 kota birimi olarak tahmin edilir.
Önbellekten gösterim 0 birimdir; şehir aramaları ayrıca sayılır. Başarısız/offline istek denemeleri de ihtiyatlı biçimde sayılır.
Sayaç UTC saat/gün/ay dönemlerini gösterir. Aylık saatlik gruplama ve toplam geçmiş kaydı, uygulama güncellemelerinde korunur.
Bu, Open-Meteo'nun gerçek kalan kota bilgisi değildir: ücretsiz uç noktanın hesap bazlı kullanım raporu yoktur, diğer cihazlar ve ortak IP/uygulama trafiği bilinemez.
Sayaç eklenmeden önceki kullanımı geriye dönük hesaplamaz. Uygulama verileri silinirse sayaç da silinir.

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

## Telefona indirme ve nightly sürümleri

[Tek nightly indirme sayfası](https://github.com/kskeskin09/weathercard/releases/tag/nightly).
Private repoya erişimi olan GitHub hesabınla telefonda giriş yap, **weathercard-nightly.apk** dosyasını indir ve aç.
Android isterse kullandığın tarayıcı için bilinmeyen kaynaklardan uygulama yükleme izni ver.
APK'lar aynı imzalama anahtarını kullanır; sonraki nightly sürümleri mevcut uygulamanın üzerine kurulabilir.

Her `main` push'u test/derleme yapıp imzalı aday APK üretir; **otomatik release yayımlamaz**. Önce gerçek ekran görüntüleri kullanıcıya gösterilir. Kullanıcı onayından sonra workflow elle `publish_nightly=true` ile çalıştırılır ve aynı `nightly` prerelease ile iki sabit dosya güncellenir.
Her commit için yeni release oluşturulmaz. Nightly yalnızca **deneme sürümleri** içindir; kullanıcı bir sürümü onayladığında ayrıca SemVer etiketli ana/stable release hazırlanır. Stable release otomatik yayımlanmaz. Otomatik gece takvimi tanımlı değildir.
Kullanıcı doğrudan nightly istediğinde `publish-nightly.yml`, başarılı kontrol koşusunun imzalı aday APK'sını yeniden derlemeden yayımlar. Böylece görsel üretiminden sonra ikinci bir Android derlemesi gerekmez.
Sürüm adı `0.1.0-nightly.YYYYMMDD.N`, Android sürüm kodu `100000 + GITHUB_RUN_NUMBER` şeklindedir.
Taban SemVer sürümü `gradle.properties` içindedir; sürüm adı uygulamanın alt kısmında da gösterilir.

Derleme, test, lint, imza doğrulaması ve yayınlama **GitHub Actions** içinde yapılır.
İmzalama anahtarı/parolası `ANDROID_KEYSTORE_BASE64` ve `ANDROID_SIGNING_PASSWORD` GitHub Secrets alanındadır; kodda tutulmaz.
PR'lar ve push'lar test edilir ancak nightly yayımlamaz. Yayın için görsel onayı ve elle başlatılan yayın seçeneği gerekir.
UI smoke testi ayrı workflow ile Android emülatöründe çalıştırılır; ikon ve ekran görüntülerini artifact olarak saklar.

## Geliştirme

Android Studio ile kök klasörü düzenleyebilirsin. Derleme araçları GitHub Actions üzerinde JDK 17, Android SDK 35 ve Gradle Wrapper 8.11.1 olarak sabitlenmiştir.
Derlemeler, testler ve APK üretimi yerelde çalıştırılmaz.

```sh
gh workflow run android.yml --ref main --repo kskeskin09/weathercard
gh workflow run ui-smoke.yml --ref main --repo kskeskin09/weathercard
```

Telefona kurulacak çıktı nightly sayfasındaki imzalı release APK'sıdır.

## Yapı

- `core`: platformdan bağımsız modeller, 6 saatlik birleştirme, testler, WMO eşlemesi.
- `app`: Open-Meteo bağlantısı, atomik disk kaydı, konum, şehir arama ve Compose ekranları/vektör ikonlar.

Mevcut konumun mahalle/ilçe adı Android'in isteğe bağlı ters geocoding servisiyle bulunur. Servis yoksa veya sonuç vermezse hava durumu yüklenir ve “Mevcut konum” etiketi kullanılır. Bu işlem Open-Meteo tahmin kotasını tüketmez.
Google Play Services bulunmayan cihazlarda Android konum sağlayıcıları kullanılır. GPS için kesin konum izni ve açık cihaz konumu gerekir; konum elde edilemezse şehir araması kullanılabilir.
Konum ve önbellek uygulamanın özel alanında saklanır; hesap/analitik/reklam SDK'sı yoktur.
Hava durumu isteklerinde koordinat Open-Meteo'ya iletilir. Bulut yedeği devre dışıdır.
