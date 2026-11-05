# Araba Başlatıcı

Telefon arabanın Bluetooth'una bağlanınca **"Android Auto başlatılsın mı?"** diye soran ve
Android Auto'yu telefon ekranında ([PhoneAuto](https://github.com/rPlayAI/tarplay-public) ile)
kendiliğinden başlatan Android uygulaması.

## Ne yapıyor

- **Bluetooth ile algılama** — seçilen araba Bluetooth'u bağlanınca bildirim gösterir.
- **Başlat** — PhoneAuto'yu Android Auto'yu başlatan ekranında açar; PhoneAuto, Android Auto'nun
  ana birim sunucusunu (port 5277) kendisi başlatır.
- **Kendini onarma** — PhoneAuto son uygulamalardan kapatılınca erişilebilirlik servisi düşer;
  Başlat'a basınca servis yeniden açılır.
- **Sunucuyu kapatma** — Android Auto oturumu bitince (15 sn sonra) ya da araba Bluetooth'u
  kopunca sunucuyu kapatır.
- **Ayarları kapatma** — PhoneAuto'daki çarpıdan sonra arkada kalan Android Auto ayarlarını kapatır.

Geliştirme geçmişi, öğrenilenler ve kendi ekran uygulamamızın planı: [docs/REHBER.md](docs/REHBER.md).

## Kurulum

Gereken: Android 13+, PhoneAuto, Android Auto'da geliştirici modu açık.
Bazı izinler yalnızca adb ile verilebildiği için telefon bir kez bilgisayara bağlanmalı:

```bash
bash kur.sh
```

Betik GitHub'daki son derlemeyi indirir, kurar ve izinleri verir.

## Derleme

Her `main` push'unda GitHub Actions imzalı APK'yı derler (`araba-baslatici-apk` artifact'ı).
`v1.2.3` biçiminde etiket atılınca (versionName ile aynı olmalı) GitHub Release yayınlanır.

Yerelde: `./gradlew assembleRelease`. İmza anahtarı yoksa debug anahtarıyla imzalanır;
o APK GitHub derlemesinin üzerine kurulamaz.

İmza anahtarı Secret'ları: `ARABA_KEYSTORE_BASE64`, `ARABA_KEYSTORE_PASSWORD`.

## Bilinen sınırlar

- PhoneAuto, Android Auto menüsünde yalnızca İngilizce yazıları arıyor; bu yüzden Android Auto'nun
  dili İngilizceye ayarlanıyor (`kur.sh` yapar).
- Çalışma Xiaomi (crDroid, Android 16) üzerinde denendi.
