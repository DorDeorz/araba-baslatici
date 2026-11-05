# CLAUDE.md

Bu depo: **Araba Başlatıcı** — Android Auto'yu telefonda otomatik başlatan uygulama; sıradaki hedef
PhoneAuto'nun yerine geçecek kendi Android Auto ekran uygulamamız.

**İşe başlamadan önce `docs/REHBER.md`'yi baştan sona oku.** Cihaz bilgileri, Android Auto'nun
sunucu mekanizması, PhoneAuto'dan öğrenilenler, bu telefondaki tuzaklar, mevcut mimari ve
aşama aşama plan orada.

Kısa kurallar:
- Kullanıcıyla Türkçe konuş.
- Kod dili **Kotlin** (`app/src/main/kotlin/`); Java kullanma.
- Derleme GitHub Actions'ta (`.github/workflows/android.yml`); telefona `bash kur.sh` ile kur.
- Yerel derleme: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleRelease`.
- Kullanıcının uygulamalarını kaldırmadan, telefon ayarlarını değiştirmeden önce sor;
  `com.tarplay.phonescreen`'i force-stop etme.
- İmza anahtarı/şifresi asla ekrana yazılmaz.
