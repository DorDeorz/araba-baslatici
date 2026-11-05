#!/usr/bin/env bash
# Araba Başlatıcı'yı kabloyla bağlı telefona kurar ve gereken tüm izinleri verir.
#
#   bash kur.sh                 # GitHub'daki son başarılı derlemeyi indirip kurar
#   bash kur.sh yol/uygulama.apk  # verilen APK'yı kurar
#
# Gerekenler: adb (Android platform-tools) ve APK indirmek için `gh` (GitHub CLI, oturum açık).
# Telefonda USB hata ayıklama açık olmalı.
set -euo pipefail
cd "$(dirname "$0")"

PKG=com.dordeorz.arababaslatici
ADB=${ADB:-adb}
command -v "$ADB" >/dev/null 2>&1 || ADB=/c/Users/oguzh/AppData/Local/Android/Sdk/platform-tools/adb.exe

APK=${1:-}
if [ -z "$APK" ]; then
  GECICI=$(mktemp -d)
  RUN=$(gh run list --workflow android.yml --branch main --status success --limit 1 --json databaseId --jq '.[0].databaseId')
  [ -n "$RUN" ] || { echo "Başarılı derleme bulunamadı"; exit 1; }
  echo "GitHub derlemesi $RUN indiriliyor..."
  gh run download "$RUN" --name araba-baslatici-apk --dir "$GECICI"
  APK="$GECICI/ArabaBaslatici.apk"
fi

"$ADB" wait-for-device
"$ADB" install -r "$APK"

# Çalışma izinleri (normalde uygulama ilk açılışta sorar).
"$ADB" shell pm grant $PKG android.permission.BLUETOOTH_CONNECT
"$ADB" shell pm grant $PKG android.permission.POST_NOTIFICATIONS
"$ADB" shell pm grant $PKG android.permission.RECORD_AUDIO
# Erişilebilirlik servislerini (PhoneAuto'nunki ve kendi servisimiz) kendisi açabilsin diye.
"$ADB" shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS
# Android Auto sunucusunu otomatik kapatmak için bildirim erişimi.
"$ADB" shell cmd notification allow_listener $PKG/$PKG.OturumIzleyici
# PhoneAuto, Android Auto menüsünde yalnızca İngilizce yazıları bulabiliyor.
"$ADB" shell cmd locale set-app-locales com.google.android.projection.gearhead --locales en-US

# Uygulamayı bir kez aç: erişilebilirlik servislerini kendisi açar.
"$ADB" shell am start -n $PKG/.MainActivity >/dev/null
echo "Tamam. Telefonda Araba Başlatıcı ekranındaki satırların hepsi ✓ olmalı."
