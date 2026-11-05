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

`com.dordeorz.arababaslatici`, sürüm 1.6.0, **Kotlin** (`app/src/main/kotlin/`), min SDK 33, hedef 36.
1.5.0 (Java) arabada uçtan uca doğrulandı; 1.6.0 aynı davranışın Kotlin'e çevrilmiş hâli.

| Sınıf | Görevi |
|---|---|
| `BtAlici` | `ACL_CONNECTED/DISCONNECTED` dinler. Seçili araba bağlanınca bildirim; kopunca bildirimi kaldırır ve sunucuyu hemen kapattırır. 2 dk içinde tekrar bildirim göstermez. |
| `Bildirim` | "MY CAR bağlandı — Android Auto başlatılsın mı?" (Başlat / Hayır). PhoneAuto ekran sabitleri burada. |
| `BaslatActivity` | Görünmez ara ekran: kendi servislerini ve PhoneAuto'nun servisini gerekirse onarır, sonra PhoneAuto `MainActivity`'yi açar. |
| `Erisilebilirlik` | Erişilebilirlik servisinin gerçekten bağlı olup olmadığını kontrol eder (`getEnabledAccessibilityServiceList`), gerekirse çıkar/ekle ile yeniden açar. |
| `OturumIzleyici` | NotificationListener. "Aracınıza bağlandı" kalkınca 15 sn bekler, oturum dönmezse "Head unit server running" bildirimindeki Stop'a basar. Yeniden bağlanınca açık kalmış sunucuyu kapatır. Kendini bileşen aç/kapa ile yeniden bağlatır. |
| `AyarKapatici` | Kendi erişilebilirlik servisimiz. PhoneAuto'nun son ekranı `MainActivity` iken ve oturum açık/5 sn içinde bitmişken Android Auto ayarları öne gelirse GERİ'ye basar. Sunucu başlatılırken oturum olmadığı için karışmaz. |
| `MainActivity` | Durum ekranı (✓/✗), araba seçimi, "Bildirimi şimdi dene", "Şimdi onar". Son uygulamalarda görünmez (`excludeFromRecents`). |

**Kurulum:** `bash kur.sh` — son başarılı CI derlemesini indirir, kurar, şu izinleri verir:
`BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`, `WRITE_SECURE_SETTINGS`, bildirim dinleyicisi
(`cmd notification allow_listener`), Android Auto dilini İngilizce yapar, uygulamayı açar.

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

### Test listesi (her aşama sonunda telefonda)

- [ ] Arabaya bağlanınca bildirim geliyor; Başlat'a basınca Android Auto açılıyor.
- [ ] Android Auto Türkçeyken de sunucu kendiliğinden başlıyor.
- [ ] Sunucu başlatıldıktan sonra Android Auto ayarları arkada kalmıyor.
- [ ] Çıkışta ana ekrana dönülüyor.
- [ ] Oturum bitince / Bluetooth kopunca sunucu kapanıyor.
- [ ] Uygulama son uygulamalardan kaydırılıp öldürülünce bir sonraki başlatmada her şey kendini toparlıyor.
- [ ] Görüntü, dokunma, ses, mikrofon, dikey/yatay çalışıyor.

---

## 7. Claude ile çalışma kuralları

- Kullanıcıyla **Türkçe** konuş; uygulama arayüzü Türkçe.
- **Kod dili Kotlin.** Yeni kodu Java ile yazma. (İlk prototip Gradle'sız derlendiği için Java'ydı;
  1.6.0'da Kotlin'e çevrildi.) Open Headunit de büyük ölçüde Kotlin, kullanıcının diğer projeleri
  (AdımSayar) Kotlin + Jetpack Compose. Arayüz büyüdüğünde Compose'a geçilebilir.
- Değişiklikler bu depoda; derleme GitHub Actions'ta. Kurulum `bash kur.sh` ile.
- Telefona `adb install -r` ile kur. Kullanıcının uygulamalarını kaldırma, verisini silme,
  telefon ayarlarını değiştirme işlemlerini **önce sor**. PhoneAuto'yu force-stop etme.
- Dokunma/ekran dökümü öncesi önde hangi uygulama olduğunu kontrol et (bkz. 4.10).
- İmza şifresini ve anahtarı asla ekrana yazma. GitHub Secret'larını kullanıcı Git Bash'te ayarlar.
- Commit mesajlarının sonuna: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
