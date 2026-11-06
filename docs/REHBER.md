# Kendi Android Auto ekran uygulamamız — proje rehberi

Bu belge, yeni bir Claude oturumunun (Claude Code ya da claude.ai Projesi) hiçbir şey
bilmeden işe başlayabilmesi için yazıldı. 5 Kasım 2026'da tek bir oturumda yapılan her şeyi,
öğrenilen her tuzağı ve sıradaki büyük işin planını içerir.

**Hedef:** PhoneAuto'ya bağımlı olmadan Android Auto'yu telefonun kendi ekranında çalıştıran,
arabanın Bluetooth'una bağlanınca "Android Auto başlatılsın mı?" diye soran ve Android Auto'nun
ana birim sunucusunu **telefon hangi dilde olursa olsun** kendisi başlatan tek bir uygulama.

---

## 1. Ortam ve cihazlar

| Şey | Değer |
|---|---|
| Telefon | Redmi Note 12 Pro 4G (kod adı sweet2), crDroid, Android 16 (BP4A.251205.006), MIUI launcher ve MIUI güç yönetimi parçaları var |
| adb seri no | `826aa1290013` (USB). Kablosuz: `adb mdns services` → `adb connect IP:port` (port değişir) |
| adb yolu (masaüstü PC) | `C:\Users\oguzh\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| Java (yerel derleme) | `C:\Program Files\Android\Android Studio\jbr` (`JAVA_HOME` olarak ver) |
| Android Auto | `com.google.android.projection.gearhead` 17.9.664044-release, **geliştirici modu açık** |
| PhoneAuto | `com.tarplay.phonescreen` 1.0 (rPlayAI/TarPlay, kaynağı kapalı) |
| GitHub | hesap `DorDeorz`, depo [DorDeorz/araba-baslatici](https://github.com/DorDeorz/araba-baslatici) (gizli) |
| Araba | Bluetooth adı `MY CAR`, MAC `FC:35:E6:48:1B:B5` |

Telefonun dili Türkçe. **Android Auto'nun uygulama dili şu an İngilizceye ayarlı**
(`cmd locale set-app-locales com.google.android.projection.gearhead --locales en-US`);
sebebi aşağıda. Geri almak: `--locales ""`.

---

## 2. Android Auto'nun "ana birim sunucusu" nasıl çalışıyor

- Android Auto 17.4'ten beri telefonda çalışan bir "head unit" (araba ekranı) uygulaması,
  Android Auto'ya **TCP 127.0.0.1:5277** üzerinden bağlanır. Bu portu Android Auto'nun
  geliştirici servisi `com.google.android.projection.gearhead/.companion.DeveloperHeadUnitNetworkService`
  açar (`:projection` işleminde, foreground service).
- Servis **dışa açık değil** (`exported=false`): adb'den ya da başka uygulamadan doğrudan
  başlatılamaz. Tek yol Android Auto ayarlarındaki menü:
  1. `com.google.android.projection.gearhead.SETTINGS` eylemiyle ayarları aç
     (`DefaultSettingsActivity` → `Material3SettingsActivity`'ye yönlendirir).
  2. Sağ üstteki taşma menüsüne (⋮) bas.
  3. "Start head unit server" öğesine bas.
- Android Auto yeniden başlayınca/öldürülünce sunucu kapanır; her seferinde yeniden başlatmak gerekir.
- Geliştirici modu: Android Auto ayarlarında "Version"a 10 kez dokun. Kapalıysa menü öğesi hiç yok.

### Menü yazılarının kaynak adları (dilden bağımsız eşleştirme için)

`aapt2 dump resources` ile gearhead `base.apk`'dan çıkarıldı (17.9.664044). Çeviriler
`split_config.<dil>.apk` içinde; `PackageManager.getResourcesForApplication(gearhead)` ile
cihazın o anki dilindeki metin alınabilir:

| Kaynak adı | İngilizce |
|---|---|
| `string/menu_action_developer_head_unit_network_server` | Start head unit server |
| `string/menu_action_developer_head_unit_network_server_stop` | Stop head unit server |
| `string/abc_action_menu_overflow_description` | More options (⋮ düğmesinin content-desc'i) |
| `string/developer_head_unit_server_notification_title` | Android Auto Developer |
| `string/developer_head_unit_server_notification_subtitle` | Head unit server running |
| `string/developer_head_unit_server_notification_stop` | Stop |
| `string/developer_head_unit_network_service_starting` / `_stopping` | Starting… / Stopping head unit server… |

Kaynak adları Android Auto güncellemelerinde değişebilir; kod, ad bulunamazsa İngilizce metne düşmeli.

### Bildirimler (sunucu ve oturum durumunu anlamak için)

| Bildirim | Paket / kanal / id | Anlamı |
|---|---|---|
| "Android Auto Developer — Head unit server running", **Stop** eylemli | gearhead, kanal `gearhead_connection_status`, id 2847442 | Sunucu açık. Stop eylemi bir `startService` PendingIntent'i; NotificationListener'dan `send()` edilince sunucu kapanır (doğrulandı). |
| "Android Auto — Aracınıza bağlandı" | gearhead, kanal `car.default_notification_channel`, id 2 | Bir head unit bağlı = oturum açık. Kalkınca oturum bitmiştir. Oturum başlarken bir kez kalkıp geri gelebiliyor. |

---

## 3. PhoneAuto hakkında öğrendiklerimiz (kaynak kapalı, APK incelendi)

- Depo: [rPlayAI/tarplay-public](https://github.com/rPlayAI/tarplay-public) — yalnızca APK, belge,
  issue. "All rights reserved": **kopyalanamaz/değiştirilemez**, sadece davranış referansı.
- Bileşenler:
  - `com.tarplay.aap.receiver.PhoneScreenActivity` — başlatıcı ekran ("Start Android Auto" düğmesi).
  - `com.google.android.projection.sink.ui.MainActivity` — **dışa açık**. Bu ekran açılınca
    Android Auto düğmeye basılmadan başlar; sunucu kapalıysa önce onu başlatmayı dener.
    `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` ile açılmalı, yoksa eski bir rehber ekranı öne gelir.
  - `com.google.android.projection.sink.ui.HeadUnitServerGuide` — sunucu yoksa gösterilen rehber; buradan otomatik başlatma tetiklenir.
  - `com.google.android.projection.sink.ui.HeadUnitServerAccessibilityService` — Android Auto
    ayarlarında "More options" ve "Start head unit server" **İngilizce metinlerini** arayıp basan
    erişilebilirlik servisi. Telefon Türkçe olunca bulamıyor → bu yüzden Android Auto İngilizceye alındı.
  - 5288 portunda TCP sunucusu, Bluetooth RFCOMM sunucusu, mDNS kaydı da açıyor (kablosuz senaryolar için).
- Logcat etiketleri: `AHU.MAIN_ACTIVITY`, `AHU.TCP_CONNECTION`, `HeadUnitServerGuide`,
  `HUServerA11y` ("Opened overflow menu", "Tapped Start head unit server"), Android Auto tarafı `GH.DHUService`.
- Bilinen PhoneAuto kusurları (bizim uygulamada olmamalı):
  1. Yalnızca İngilizce menü metinleri.
  2. Sunucuyu başlatmak için açtığı Android Auto ayarlarını kapatmıyor; çarpıya basınca o ekrana düşülüyor.
  3. Son uygulamalardan kaydırılınca erişilebilirlik servisi düşüyor (aşağıda).
  4. Açık issue'lar: dikey modda dokunma (#11, #8), ses kontrolü/klavye (#2), çarpı içeriği kapatıyor (#4, #9), açılınca otomatik başlatma isteği (#3).

---

## 4. Telefon/ROM tuzakları (hepsi bu cihazda yaşandı)

1. **Son uygulamalardan kaydırma = öldürme** (`ProcessSceneCleaner: SwipeUpClean`). Öldürülen
   uygulamanın erişilebilirlik servisi `dumpsys accessibility` içinde **"Crashed services"**e
   düşer ve Android onu **kendiliğinden yeniden bağlamaz**.
   Çözüm: `enabled_accessibility_services` ayarından o servisi çıkar, ~800 ms bekle, geri ekle.
2. **`am force-stop com.tarplay.phonescreen`** servisi listeden **tamamen siler**. Testte sunucuyu
   durdurmak için yalnızca gearhead'i force-stop et, PhoneAuto'yu değil.
3. **Bildirim dinleyicisi** (NotificationListenerService) uygulama kaydırılıp öldürülünce
   "binding died" olur ve yeniden bağlanmaz. `requestRebind()` burada **işe yaramıyor**.
   Çalışan çözüm: kendi dinleyici bileşenini `setComponentEnabledSetting` ile DISABLED → ENABLED yap.
4. `enabled_notification_listeners` ayarını yazmak dinleyiciyi **açmıyor**. Açmak için:
   `adb shell cmd notification allow_listener <paket>/<sınıf>` ya da kullanıcı Ayarlar'dan.
5. `WRITE_SECURE_SETTINGS` yalnızca adb ile verilir (`pm grant`), kalıcıdır; uygulama silinince gider.
6. Play dışı kurulan uygulamalarda Android 13+ "kısıtlanmış ayarlar" erişilebilirlik ve bildirim
   erişimini kilitler (Uygulama bilgisi → ⋮ → Kısıtlanmış ayarlara izin ver). adb ile verince sorun yok.
7. `uiautomator dump` sonrası dosyayı Git Bash'te `adb pull /sdcard/...` ile çekme: Git Bash yolu
   `C:/Program Files/Git/sdcard/...` yapar. `adb shell "uiautomator dump /sdcard/ui.xml; cat /sdcard/ui.xml; rm /sdcard/ui.xml"` kullan.
8. Proje yolu "Masaüstü" içerdiği için AGP derlemeyi reddediyor → `gradle.properties`'te
   `android.overridePathCheck=true`.
9. GitHub Secret'larını **Git Bash**'te ayarla; PowerShell'de `base64` yok ve `gh` boş değeri hatasız kaydediyor.
10. Ekran dökümü/dokunma öncesi her seferinde `dumpsys window | grep mCurrentFocus` ile doğru
    uygulamanın önde olduğunu kontrol et; heads-up bildirimler dökümde görünmez, ekran görüntüsüne bak.

---

## 5. Şu anki uygulama: Araba Başlatıcı (bu depo)

`com.dordeorz.arababaslatici`, sürüm 2.3.0, **Kotlin** (`app/src/main/kotlin/`), min SDK 33,
derleme SDK 37. Arayüz Jetpack Compose + Material 3 (Kronometre'nin yeşil teması, dinamik renk,
tema seçimi). PhoneAuto artık gerekmiyor: Android Auto ekranı bu uygulamanın içinde (`ekran/`).

| Sınıf | Görevi |
|---|---|
| `MainActivity` + `ui/` | Ana ekran (büyük Başlat düğmesi, hazırlık kartı, pil yardımı), ilk açılış karşılaması, Ayarlar sayfası. |
| `Baslatici` | Tek dokunuşla başlatma: sunucu açıksa ekranı açar, değilse `SunucuBaslatici` ile sunucuyu başlatır. Son çıkıştan 3 sn, son durdurmadan 6 sn geçmeden başlatmaz (AA "Already connected"da takılmasın). |
| `SunucuBaslatici` | Erişilebilirlik servisi. AA ayarlarında ⋮ → "Ana birim sunucusunu başlat"a gearhead kaynak yazılarıyla (dilden bağımsız) basar, sonra ayarları kapatır. |
| `Erisilebilirlik` | Servisin gerçekten bağlı olup olmadığını kontrol eder, gerekirse çıkar/ekle ile yeniden açar. |
| `BtAlici` | `ACL_CONNECTED/DISCONNECTED`. Seçili araba bağlanınca ayara göre: bildirimle sor / doğrudan başlat / hiçbir şey yapma. 2 dk içinde tekrarlamaz. Kopunca sunucuyu kapattırır. |
| `Bildirim`, `BaslatActivity` | "Başlatılsın mı?" bildirimi ve görünmez başlatma ekranı. |
| `BaslatKutucugu`, `BaslatWidget` | Hızlı ayarlar kutucuğu ve ana ekran widget'ı: ikisi de `BaslatActivity`'yi açar. |
| `OturumIzleyici` | NotificationListener. Oturum bitip dönmezse sunucunun Stop'una basar (oturum açıkken asla). |
| `Olaylar` | Kalıcı olay kaydı: `/sdcard/Android/data/com.dordeorz.arababaslatici/files/olaylar.txt`, Ayarlar → Geliştirici → Son olaylar. |
| `ekran/` | Kendi AAP alıcımız (aşağıda "Kendi ekranımız"). |

**Kurulum:** `bash kur.sh` — son başarılı CI derlemesini indirir, kurar, şu izinleri verir:
`BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`, `WRITE_SECURE_SETTINGS`, bildirim dinleyicisi
`RECORD_AUDIO` ve bildirim dinleyicisi (`cmd notification allow_listener`), uygulamayı açar.
Wi-Fi adb için `ADB_SERIAL=192.168.1.49:PORT bash kur.sh`. Android Auto'nun dili Türkçe kalabilir.

**Derleme:** Gradle 9.6, AGP 9.4 (dahili Kotlin desteği; Kotlin 2.4.10 kök `build.gradle.kts`'te
sabitli), R8 açık (R8 olmadan Kotlin kütüphanesiyle APK 2 MB, R8 ile ~50 KB).
GitHub Actions her `main` push'unda imzalı APK üretir (artifact `araba-baslatici-apk`);
`vX.Y.Z` etiketi (versionName ile aynı) Release yayınlar. Günlükte Secret uzunlukları yazılır (5728 / 32 beklenir).

**İmza anahtarı:** PKCS12, alias `arababaslatici`, `%USERPROFILE%\.android\arababaslatici.p12`,
şifresi yanındaki `arababaslatici-sifre.txt` (asla ekrana/sohbete yazma). GitHub Secret'ları:
`ARABA_KEYSTORE_BASE64`, `ARABA_KEYSTORE_PASSWORD`. Sertifika SHA-256: `ed50847b…32fb14`.
Anahtar kaybolursa uygulama güncellenemez → yedekli tut.

**Eski prototip:** `Masaüstü/DorDeorz/PhoneAutoTetik` (`com.dordeorz.phoneautotetik`, Gradle'sız `build.sh`). Telefondan kaldırıldı, artık kullanılmıyor.

---

## 6. Sıradaki büyük iş: kendi ekran uygulamamız

### Temel: Open Headunit

- [andreknieriem/headunit-revived](https://github.com/andreknieriem/headunit-revived) (Play'de
  "Open Headunit", eski adı Headunit Revived, `com.andrerinas.headunitrevived`). Masaüstünde
  `com.andrerinas.headunitrevived_2.1.1.apk` duruyor (eski sürüm; güncel 3.x).
- Telefonu Android Auto alıcısına çeviriyor; **Self-Mode** Android Auto'yu aynı telefonda çalıştırıyor
  ve AA 17.4+ için 5277'deki sunucuya bağlanıyor ya da sunucuyu başlatmak için AA ayarlarını açıyor.
- **Lisans AGPL-3.0.** Depo gizliyken sorun yok. Herkese açılırsa ya da APK başkasına verilirse
  bütün kod AGPL olmalı ve kaynak paylaşılmalı. Depoyu açmadan önce `LICENSE` ekle.
- GitHub'da herkese açık bir deponun fork'u gizli olamaz: kodu klonla, kendi depomuza `git subtree`
  ya da düz kopya ile al, kaynağı ve commit'ini README'de belirt.

### Aşamalar (her biri ayrı PR / dal)

**Aşama 0 — İnceleme (kod yazmadan)**
- Open Headunit'i klonla; Self-Mode akışını, 5277 bağlantısını, video (MediaCodec H.264),
  ses (AAC/PCM, AudioTrack), dokunma, mikrofon, gece modu ve sensör kodunu bul.
- Hangi Android Auto sürümleriyle test edildiğini, TLS sertifikası/el sıkışma kısmını ve
  bilinen sorunlarını (issues) not et.
- Kendi telefonda Play/GitHub sürümünü kurup Self-Mode'u dene: PhoneAuto kadar iyi mi?
  (görüntü akıcılığı, dokunma, ses gecikmesi, dikey/yatay, mikrofon). Sonucu bu belgeye yaz.

#### Aşama 0 bulguları (kod incelemesi, 5 Kasım 2026)

Bakılan sürüm: `ae74788d` (3 Kasım 2026), sürüm **4.0.0-beta2**. Depo GitHub'da
`andreknieriem/open-headunit` adına taşınmış (README böyle bağlantı veriyor); eski `headunit-revived` adresinden klonlama hâlâ çalışıyor.
Kotlin paket adı `com.andrerinas.openheadunit`, `applicationId` hâlâ `com.andrerinas.headunitrevived`.
Masaüstündeki 2.1.1 APK'nın önemi yok (Nisan 2026'da, bu projeden önce indirilmiş); göz ardı et.
Self-Mode'un 5277'ye bağlanması 3.x'te geldi. Telefonda Android Auto 17.9 olduğu için test
en az 3.x ile yapılmalı (Play sürümü ya da GitHub Releases). Open Headunit telefonda henüz kurulu değil.

**Boyut ve yapı.** Elle yazılmış ~124 bin satır Kotlin/Java (685 dosya). Buna ek olarak ~124 bin
satır üretilmiş protobuf (`aap/protocol/proto`), ~33 bin satır C/C++ (`app/src/main/cpp`: FFmpeg
yazılım çözücüsü, AAudio çıkışı, ses zaman esnetme, libusb) ve 7.8 MB hazır `.so` var
(`jniLibs`: libavcodec, libavutil, libswscale, libusb). Derleme için NDK 29 ve CMake gerekiyor.
AGP 8.13.2, Gradle 8.14.4, minSdk 16 (Play çeşidi için 21). Bizim proje ise AGP 9.4 ve Gradle 9.6
kullanıyor. Kodun büyük kısmı bize gerekmeyen yollar için: USB (AOA), kablosuz (Wi-Fi Direct,
hotspot, Wireless Helper, ZLink/HCT/FYT/ESP32 Bluetooth köprüleri), kendi ana ekranı, widget'lar,
otomasyon. 200'e yakın birim testi (Robolectric) var.

**Self-Mode akışı (AA 17.4+).** `connection/self/SelfLauncherManager.kt` sürümü okur
(`SelfLaunchRoutePolicy`: 17.4 ve üstü → `HEADUNIT_SERVER`). `launchers/SelfLauncherV17_4.kt`
yalnızca `CommManager.connect("127.0.0.1", 5277)` dener. **Sunucu kapalıysa kendisi başlatmıyor.**
"Lütfen Headunit Server'ı başlatın" diye bir Toast gösteriyor ve
`gearhead.companion.settings.DefaultSettingsActivity` ekranını açıyor. Menüye basma işini
kullanıcıya bırakıyor. Yani sunucuyu otomatik başlatma (Aşama 2) tamamen bizim katkımız olacak.
17.4 öncesi için 5288'de `WirelessServer` ve eski tetikleyiciler var. Bunlar bizi ilgilendirmiyor.

**Dışarıdan başlatma.** `main.MainActivity` dışa açık ve `headunit://selfmode` deep link'ini kabul
ediyor (`MainActivity.kt:1204`). Ayrıca `headunit://connect?ip=...` de var. Bu sayede Araba
Başlatıcı, kod almadan Open Headunit'i PhoneAuto yerine doğrudan açabilir. Sunucu ise bizim
erişilebilirlik servisimizle önceden başlatılmalı. Bu, Aşama 1'den önce denenebilecek ucuz bir ara adım.

**El sıkışma.** Sıra şöyle: sürüm isteği (1.2 duyuruluyor, telefon minor'ı seçiyor;
`AapVersionNegotiation.kt`), sonra TLS. İstemci sertifikası `res/raw/cert` + `res/raw/privkey`
dosyalarında; konu `O=Google-Android-Reference`, veren `O=Google Automotive Link`, 2014–2044
geçerli. Google'ın referans head unit sertifikası; açık kaynak head unit projeleri (Mike Reid'in headunit'inden beri) aynısını kullanıyor.
Sunucu sertifikası doğrulanmıyor (`ssl/SslContextFactory.kt`, trust-all). Conscrypt paketli
geliyor. Bu sertifika Google'a ait: depo herkese açılırsa ya da APK dağıtılırsa ayrıca risk.

**Video.** `decoder/video/VideoDecoder.kt` MediaCodec ile H.264/H.265 çözüyor. Bozuk donanım
çözücüler için FFmpeg yazılım yedeği var (`FfmpegVideoDecoder.kt`). Ekran boyu ve DPI
`ServiceDiscoveryResponse.kt` içinde duyuruluyor (otomatik DPI, yedek çözünürlük). Bilinen sorun:
dikeyde Google Maps dokunması bozuk; README DPI'yı 200'ün altına çekmeyi öneriyor. PhoneAuto'nun
#8/#11 sorunlarıyla aynı belirti olabilir.

**Ses.** `aap/AapAudio.kt` + `decoder/audio/`: AAC'yi MediaCodec ile, PCM'i AudioTrack ya da
AAudio (native) ile çalıyor. Medya, konuşma ve sistem için ayrı kanallar var. Ses odağı
`PlaybackFocusPolicy`, gecikme ayarı jitter buffer ve zaman esnetme ile yapılıyor.

**Dokunma.** `input/TouchCoordinateMapper.kt`, `RemoteTouchPolicy.kt`, iki parmak hareketleri
(`TwoFingerGestureTracker.kt`). Projeksiyon ekranı `aap/AapProjectionActivity.kt` (~2950 satır).

**Mikrofon.** `decoder/audio/MicRecorder.kt` (AudioRecord) ve `aap/MicSessionController.kt`.
`FOREGROUND_SERVICE_MICROPHONE` ve `RECORD_AUDIO` izinleri gerekiyor.

**Gece modu ve sensörler.** `ServiceDiscoveryResponse.kt`'de DRIVING_STATUS, LOCATION (isteğe
bağlı GPS) ve NIGHT sensörleri duyuruluyor. Gece olayı `aap/protocol/messages/NightModeEvent.kt`'de.
Bir de gece kaplaması (overlay) var.

**İzinler.** Manifest 31 izin istiyor: konum, Bluetooth, Wi-Fi, mikrofon, telefon durumu,
`SYSTEM_ALERT_WINDOW`, `READ_LOGS`, `WRITE_SETTINGS`, cihaz yöneticisi ve başka izinler.
Bize gerekenler bunların küçük bir kısmı.

**Issue'lar.** GitHub issue sayfası bu oturumdan okunamadı (robots engeli; depo bu oturuma bağlı
değil). Telefonda denemeden önce elle bakılmalı: "self mode", "17.4", "5277", "portrait",
"touch" araması.

**PhoneAuto'nun 5277 akışı (karşılaştırma için, logcat'ten).** `AHU.TCP_CONNECTION: Connecting to
head unit server 127.0.0.1:5277`; sunucu açıksa 0,3–1 sn içinde `Connected`. `GH.DHUService` sırası:
`Network server running on port 5277` → `Head unit connected` → `Head unit disconnected` →
`Shutting down service`. Sunucu kapalıysa 2 sn sonra yeniden dener, sonra otomatik başlatmaya geçer;
15 sn içinde bağlanamazsa rehber ekranında kalır ve aynı işlemde bir daha denemez. Ses: `AHU.AUDIO`
AAC ve PCM akışı açıyor (AudioTrack minbuffer 15376 / 4096). TLS hatası görülmedi ama ayrıca
bakılmadı. Telefon ekranı 1080×2400; AA'nın açıldığı çözünürlük/DPI not edilmedi.
**Kullanıcı yatay kullanıyor: öncelik yatay mod.** Dikey ve dikey dokunma sorunları ikinci planda.

**Telefonda Self-Mode test listesi (yalnızca kullanıcı başındayken).** Telefon kullanıcının
laptop'una USB ile bağlı; bulut ya da masaüstü oturumu telefona doğrudan erişemiyor.
Hazırlık:
- [ ] Open Headunit'in güncel sürümünü (en az 3.x) kur. Kurulum için kullanıcıya sor.
- [ ] PhoneAuto açık değilken dene: ikisi aynı 5277 sunucusuna bağlanır, aynı anda çalıştırılmaz.
      PhoneAuto'yu **force-stop etme** (bkz. 4.2); son uygulamalardan kapatmak yeterli.
- [ ] Araba Başlatıcı oturum bitince 15 sn sonra sunucuyu kapatır ve testi bozabilir. Gerekirse
      kullanıcıdan bildirim erişimini geçici olarak kapatmasını iste (telefon ayarı: önce sor).
Deneme:
- [ ] Sunucu kapalıyken Self-Mode: Open Headunit AA ayarlarını açıyor mu, Toast çıkıyor mu?
- [ ] Sunucuyu elle başlat (⋮ → Start head unit server), sonra Self-Mode: kaç saniyede bağlanıyor?
- [ ] `adb shell am start -a android.intent.action.VIEW -d headunit://selfmode` ile dışarıdan açılıyor mu?
- [ ] Yatay: görüntü akıcılığı, dokunma doğruluğu (Maps arama/kaydırma), tam ekran.
- [ ] Ses: müzik, navigasyon sesi, gecikme, ses odağı (müzik navigasyonda kısılıyor mu).
- [ ] Mikrofon / Asistan, klavye.
- [ ] Gece modu.
- [ ] Çıkış: Open Headunit'ten çıkınca ne oluyor, AA ayarları arkada kalıyor mu, sunucu kapanıyor mu?
- [ ] AA dili Türkçeye alınırsa (`--locales ""`, önce sor) akış değişiyor mu? Sonra İngilizceye geri al.
- [ ] Logcat: `adb logcat | grep -iE "SelfMode|AapTransport|CommManager|GH.DHUService"`; el sıkışma ve
      ekran çözünürlüğü/DPI satırlarını not et.
Sonuçları PhoneAuto ile yan yana bu bölüme yaz.

**Telefon testi sonuçları (5 Kasım 2026, Open Headunit 3.5.0 GitHub sürümü, AA 17.9).**

| Konu | Open Headunit 3.5.0 Self-Mode | PhoneAuto |
|---|---|---|
| Sunucu kapalıyken | 5277'ye bağlanamıyor, uyarı verip AA ayarlarını açıyor; menüye basmıyor, sunucu sonradan açılınca **yeniden denemiyor** (15 sn beklendi) | Erişilebilirlik servisiyle sunucuyu kendisi başlatıyor (yalnızca İngilizce) |
| Sunucu açıkken | Bağlantı 0,1 sn, TLS 0,7–1,0 sn (protokol 1.7, hata yok), ilk görüntü yatay 1,9 sn / dikey 2,75 sn | Bağlantı ~0,02 sn; pratikte aynı hız |
| `headunit://selfmode` | Çalışıyor: açılıp kendiliğinden AA'ya geçiyor, komuttan ilk görüntüye 2,3–3,5 sn | — |
| Görüntü (yatay) | 1920×1080, tam ekran, çözme 45–49 fps, ekranda ~33 fps, takılma yok | 1920×1080; kullanıcıya göre aynı |
| Dokunma / ses / mikrofon / gece | Hepsi çalışıyor; Self-Mode'da ses doğrudan telefondan çalıyor; gece modu kendiliğinden | Aynı |
| Klavye | AA'nın telefon klavyesi ayrı tam ekran açılıyor, yazılabiliyor | Yazmaya izin vermiyordu |
| Çıkış | Ekranda çıkış yok; son uygulamalardan kaydırmak oturumu bitirmiyor (`onTaskRemoved — attempting restart`), bildirimden Durdur da gerekiyor | Tek X (ama X içeriğin, ör. Spotify kartının üstünde duruyor) |
| Sunucu çıkışta | Açık kalıyor | Açık kalıyor (Araba Başlatıcı kapatıyor) |
| AA ayarları çıkışta | Arkada kalmıyor | Kalıyordu (AyarKapatici çözüyor) |
| Çentik (yatay, solda) | Sol şerit kenara yapışık, çentiğin altına giriyor | AA'nın sol şeridi ~85 px sağa kaydırılmış |
| DPI | 175/218/260/320 denendi; **260 iyi** | Loglamıyor; ekran karşılaştırmasına göre ~300–320 (tahmin) |
| Dikey | Görüntü ve dokunma çalışıyor (kısa deneme) | Denenmedi |
| Dayanıklılık | Radarbot kendi ekranını açınca projeksiyon arkaya düştü, OH kendiliğinden geri getirdi; kaydırılıp yeniden açılınca oturum kopmadı | — |

Türkçe AA menü metinleri (Aşama 2 için): ⋮ = "Diğer seçenekler", öğe = "Ana birim sunucusunu başlat".
OH'nin akışı dilden bağımsız (menüye kendisi basmadığı için).

**Doğru başlatma sırası (doğrulandı):** sunucuyu başlat → AA ayarlarını GERİ ile kapat →
`headunit://selfmode`. Türkçe AA ile bu sırayla sunucudan sonra 3,4 sn içinde bağlandı.
Ters sırada (önce deep link) OH ayarlarda takılı kalıyor.

**Kullanıcının kendi uygulamamızdan istekleri:**
- Tek dokunuşla çıkış, içeriğin üstünde durmayan; oturumu, 5277 sunucusunu ve servisi birlikte kapatsın.
- Yatayda çentik payı: görüntü tam ekran kalsın, yalnızca AA'nın arayüzü çentikten uzaklaşsın
  (PhoneAuto gibi). Çentik tarafına göre otomatik, ayrıca elle ayarlanabilir.
  (OH'deki `IMMERSIVE_WITH_NOTCH` bütün görüntüyü kaydırıyor, istenen bu değil.)
- Klavye aynı ekranda açılsın.
- Kapsamlı ayarlar ekranı: DPI (varsayılan ~260), çentik payı, yön, çıkış, erişilebilirlik servisi bölümü.

**Öneri (Aşama 1 için).** Bütün uygulamayı `:ekran` modülü olarak almak ağır olur: 250 bin
satırdan fazla kod, NDK, hazır `.so` dosyaları, AGP 8 ile 9 farkı ve ihtiyacımız olmayan pek çok
bağlantı yolu var. Bunun yerine iki adım öneriyorum:
1. Önce **Open Headunit'i olduğu gibi** kurup Araba Başlatıcı'dan `headunit://selfmode` ile açmak.
   Sunucuyu kendi erişilebilirlik servisimiz dilden bağımsız başlatır (Aşama 2 öne alınır).
   Telefonda PhoneAuto ile karşılaştırılır.
2. Self-Mode yeterince iyiyse, kendi uygulamamıza yalnızca TCP 5277 yolunu almak: `aap/`
   (taşıma, TLS, mesajlar), `aap/protocol/proto`, `decoder/video`, `decoder/audio` (yalnızca
   MediaCodec/AudioTrack yolu), `input/`, `ssl/`. USB, kablosuz, ana ekran ve FFmpeg dışarıda kalır.
   AGPL yükümlülükleri yine geçerli olur.

#### Aşama 1 için bağımlılık ölçümü ve önerilen yol (5 Kasım 2026)

Karar (kullanıcı): ara adım (Open Headunit'i dışarıdan açtırmak) **atlanıyor**; doğrudan tek uygulama.

Open Headunit'te `aap/AapTransport.kt` ve `aap/AapProjectionActivity.kt`'den başlayıp başvurulan
sınıfları izleyen bir betikle ölçüldü:
- Hiçbir şey kesilmeden: **377 dosya, ~200 bin satır**. Her şey `App`/`AppComponent` tekilleri ve
  `utils/Settings.kt` üzerinden USB, kablosuz, ana ekran ve otomasyon koduna bağlanıyor.
- USB/kablosuz/Bluetooth/ana ekran/otomasyon/konum ve `Settings` zorla kesilse bile: **~150 dosya,
  ~33 bin satır** (protobuf hariç), üstelik kesilen yerlere hâlâ başvuru var.
Yani "yalnızca 5277 yolunu kopyalamak" basit bir dosya seçimi değil, ağır bir ameliyat.

**Önerilen yol: kendi yalın AAP istemcimizi yazmak, Open Headunit'i başvuru kaynağı olarak kullanmak.**
- Protokol tanımları: OH'nin `app/src/main/proto/*.proto` dosyaları (1512 satır) alınır,
  `protobuf-lite` ile derlenir. Üretilmiş 124 bin satırlık Java depoya girmez.
- Yazılacak çekirdek (tahmini 3–5 bin satır Kotlin): TCP 127.0.0.1:5277, sürüm el sıkışması,
  TLS (`SSLEngine`, `res/raw/cert` + `privkey`), çerçeveleme ve parçalı mesaj birleştirme,
  kontrol kanalı (service discovery, kanal açma, ping, ses/video odağı, byebye), video kanalı →
  MediaCodec H.264 → SurfaceView, dokunma, mikrofon, sensörler (DRIVING_STATUS=UNRESTRICTED, NIGHT).
  Self-Mode'da medya sesi Android Auto'dan doğrudan çalıyor; ses sink'leri ilk sürümde gerekmeyebilir
  (OH Self-Mode'da medya/konuşma sink'lerini düşürüyor, doğrulanacak).
- **Çentik payı** bedava gibi: `media.proto`'daki `UiConfig.content_insets` (ya da
  `stable_content_insets`) AA'ya arayüzü içeriden kaydırmasını söyler, arka plan tam ekran kalır
  (PhoneAuto'nun yaptığı). OH yalnızca `margins` gönderiyor (`UpdateUiConfigRequest.kt`), bütün
  görüntüyü küçültüyor. Değer `WindowInsets.displayCutout` ile otomatik, ayarlardan elle.
- Lisans: OH'den alınan .proto'lar ve uyarlanan mantık nedeniyle uygulama AGPL-3.0 türevi sayılır;
  depo gizli kaldıkça sorun yok, `LICENSE` ve kaynak bilgisi Aşama 1'de eklenir.

Alternatif (önerilmiyor): OH'yi bütün olarak `:ekran` modülü yapmak. Hemen çalışır ama 200 bin satır,
NDK/FFmpeg `.so`, AGP 8→9 uyarlaması ve bize gerekmeyen her şey gelir; çentik, çıkış, klavye gibi
istekleri OH'nin karmaşık koduna işlemek gerekir.

**Performans hedefi (kullanıcı isteği: OH ve PhoneAuto'dan daha akıcı).** Karşılaştırma noktası
(5 Kasım, OH 3.5.0, yatay): ilk görüntü 1,9 sn, çözme 45–49 fps, ekranda ~33 fps. Denenecekler:
60 fps duyurmak (`VideoFrameRateType._60`, telefon kaldırmazsa 30), MediaCodec `low-latency`
(API 30+) ve doğrudan SurfaceView çıkışı, 1080p/720p ve H.264/H.265 ölçümü, sunucuyu önceden
başlatıp hızlı bağlanma. Her PR sonunda fps ve ilk görüntü süresi ölçülür. Tavan: kodlama ve
çözme aynı telefonda.

Aşama 1'in PR'ları (öneri):
1. `.proto` + protobuf-lite derlemesi, TCP + sürüm + TLS el sıkışması; "bağlandı" logu (telefonda test).
2. Kontrol kanalı + video → ekranda Android Auto görüntüsü (yatay, 1920×1080, DPI ayarlı).
3. Dokunma, gece/sürüş sensörleri, mikrofon; tek dokunuşla çıkış + sunucuyu kapatma.
4. Sunucuyu dilden bağımsız başlatma (Aşama 2) ve Bluetooth sorusundan bizim ekranımızı açma;
   PhoneAuto bağımlılığı kalkar.
5. Çentik payı (`content_insets`), ayarlar ekranı, klavye.

**Aşama 1 — Depoya alma ve derleme**
- Open Headunit kodunu bu depoya ayrı modül olarak al (ör. `:ekran`), CI'da derlensin.
- Paket adı `com.dordeorz.arababaslatici` altında tek uygulama mı, ayrı uygulama mı? Öneri: tek
  uygulama (izinler ve kurulum bir kez yapılır).
- Lisans kararı: depoya `LICENSE` (AGPL-3.0) ve kaynak bilgisi.

**Aşama 2 — Dilden bağımsız sunucu başlatma (kendi erişilebilirlik servisimizde)**
- Gearhead kaynaklarından `menu_action_developer_head_unit_network_server` ve
  `abc_action_menu_overflow_description` metinlerini o anki dilde al; düğümleri metne/content-desc'e
  göre bul. Bulunamazsa İngilizceye, o da olmazsa toolbar'daki son `ImageButton`'a düş.
- Sunucu açıldıktan sonra **ayarlar ekranını GERİ ile kapat** (PhoneAuto'nun kusuru #2'yi baştan önle),
  sonra kendi ekranımıza dön.
- Bu bitince `kur.sh`'daki `set-app-locales ... en-US` satırı kaldırılır ve Android Auto Türkçeye döner.

**Aşama 3 — Mevcut özellikleri taşıma**
- Bluetooth sorusu, sunucuyu otomatik kapatma (artık bildirim izlemeye gerek kalmayabilir:
  oturumun bittiğini kendi bağlantımızdan biliriz), kendini onarma.
- Bizim ekran sunucu bağlantısı kopunca kendini kapatıp ana ekrana dönmeli.

**Aşama 4 — PhoneAuto'yu geçmek**
- Dikey + yatay, tam ekran, dokunma doğruluğu, ses odak yönetimi, mikrofon/Asistan, klavye,
  gece modu, çarpının içerik üstünde durmaması.

### Kendi ekranımız: durum (2026-11-06, 2.3.0, `app/src/main/kotlin/.../ekran/`)

Telefonda ve arabada doğrulandı: bağlantı + TLS, servis keşfi, görüntü (H.264, donanım çözücü
`OMX.qcom.video.decoder.avc`), dokunma, ses, mikrofon/Asistan, tek adımda çıkış, Türkçe AA ile
sunucu başlatma.

| Ölçüm | Open Headunit 3.5.0 | Bizim |
|---|---|---|
| Bağlantı (TCP→TLS) | 0,73 sn | 0,9–1,7 sn |
| İlk görüntü | 1,86 sn | 1080p 1,7–2,6 sn, 720p 1,8 sn |
| Ekranda fps (hareketli ekran) | ~33 | 1080p 55–60, 720p 50–60 |

- Sabit ekranda düşük fps normal: AA yalnızca ekran değişince kare gönderir.
- **Çentik (PhoneAuto yöntemi):** çentik şeridine hiç çizilmez. Geometri `ekranG - pay` genişliğe
  göre hesaplanır, yüzey `solPay` kadar kaydırılır, şerit siyah kalır, dokunmada x'ten pay düşülür.
  AA'ya content_insets **gönderilmez**: gönderilirse AA o alana duvar kâğıdı çizip köşeleri yuvarlar
  (mavi şerit). 0x8009 gönderilecekse margins mutlaka eklenmeli, yoksa görüntü taşar.
- **AA tuzakları:** oturumdan hemen sonra sunucuyu durdurmak AA'yı çökertiyor (çıkışta 2 sn bekle);
  hızlı yeniden açılışta AA "Already connected"da takılıp 1–6 sn sonra kapanıyor. Önlem: başlatmadan
  önce bekleme + hiç kare gelmeden biterse bir kez otomatik yeniden deneme.
- **Klavye:** AA'nın klavyesi görüntünün içinde çiziliyor; harf boyutu Ayarlar → Yoğunluk (DPI)
  ile büyür (ör. 280–320). Klavyeye dokununca AA bazen telefonda kendi ekranını üste açıyor.
- Dosyalar: `Pb`, `Cerceve`/`AapTls`/`AapTasima`, `Kesif` (`Geometri`), `AapOturum`,
  `GoruntuCozucu`, `Mikrofon`, `EkranActivity`.
- Kalan: aynı ekranda klavye.

### Test listesi (her aşama sonunda telefonda)

- [x] Arabaya bağlanınca bildirim geliyor; Başlat'a basınca Android Auto açılıyor.
- [x] "Doğrudan başlat" seçiliyken arabaya bağlanınca bildirimsiz açılıyor.
- [x] Ana ekran widget'ı ve hızlı ayarlar kutucuğu başlatıyor.
- [x] Android Auto Türkçeyken de sunucu kendiliğinden başlıyor.
- [x] Sunucu başlatıldıktan sonra Android Auto ayarları arkada kalmıyor.
- [x] Çıkışta ana ekrana dönülüyor.
- [ ] Oturum bitince / Bluetooth kopunca sunucu kapanıyor.
- [ ] Uygulama son uygulamalardan kaydırılıp öldürülünce bir sonraki başlatmada her şey kendini toparlıyor.
- [x] Görüntü, dokunma, ses, mikrofon çalışıyor; çentik şeridi temiz.

---

## 7. Claude ile çalışma kuralları

- Kullanıcıyla **Türkçe** konuş; uygulama arayüzü Türkçe.
- **Kod dili Kotlin.** Yeni kodu Java ile yazma. (İlk prototip Gradle'sız derlendiği için Java'ydı;
  1.6.0'da Kotlin'e çevrildi.) Open Headunit de büyük ölçüde Kotlin, kullanıcının diğer projeleri
  (AdımSayar) Kotlin + Jetpack Compose. Arayüz Compose + Material 3 (2.2.0'dan beri).
- Değişiklikler bu depoda; derleme GitHub Actions'ta. Kurulum `bash kur.sh` ile.
- Telefona `adb install -r` ile kur. Kullanıcının uygulamalarını kaldırma, verisini silme,
  telefon ayarlarını değiştirme işlemlerini **önce sor**. PhoneAuto'yu force-stop etme.
- Dokunma/ekran dökümü öncesi önde hangi uygulama olduğunu kontrol et (bkz. 4.10).
- İmza şifresini ve anahtarı asla ekrana yazma. GitHub Secret'larını kullanıcı Git Bash'te ayarlar.
- Commit mesajlarının sonuna: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
