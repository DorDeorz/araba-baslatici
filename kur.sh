#!/usr/bin/env bash
# Araba Başlatıcı'yı kabloyla bağlı telefona kurar ve gereken tüm izinleri verir.
#
#   bash kur.sh                 # GitHub'daki son başarılı derlemeyi indirip kurar
#   bash kur.sh yol/uygulama.apk  # verilen APK'yı kurar
#
# Gerekenler: adb (Android platform-tools) ve APK indirmek için `gh` (GitHub CLI, oturum açık).
# Telefonda USB hata ayıklama açık olmalı. Wi-Fi ile: ADB_SERIAL=192.168.1.49:PORT bash kur.sh
set -euo pipefail
cd "$(dirname "$0")"

PKG=com.dordeorz.arababaslatici
ADB=${ADB:-adb}
command -v "$ADB" >/dev/null 2>&1 || ADB=/c/Users/oguzh/AppData/Local/Android/Sdk/platform-tools/adb.exe
SERI=${ADB_SERIAL:-}
if [ -n "$SERI" ] && [[ "$SERI" == *:* ]]; then "$ADB" connect "$SERI" >/dev/null || true; fi
adb_() { "$ADB" ${SERI:+-s "$SERI"} "$@"; }

APK=${1:-}
if [ -z "$APK" ]; then
  GECICI=$(mktemp -d)
  RUN=$(gh run list --workflow android.yml --branch main --status success --limit 1 --json databaseId --jq '.[0].databaseId')
  [ -n "$RUN" ] || { echo "Başarılı derleme bulunamadı"; exit 1; }
  echo "GitHub derlemesi $RUN indiriliyor..."
  gh run download "$RUN" --name araba-baslatici-apk --dir "$GECICI"
  APK="$GECICI/ArabaBaslatici.apk"
fi

adb_ wait-for-device
adb_ install -r "$APK"

# Çalışma izinleri (normalde uygulama ilk açılışta sorar).
adb_ shell pm grant $PKG android.permission.BLUETOOTH_CONNECT
adb_ shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb_ shell pm grant $PKG android.permission.RECORD_AUDIO
# Erişilebilirlik servislerini (sunucuyu başlatan kendi servisimiz) kendisi açabilsin diye.
adb_ shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS
# Android Auto sunucusunu otomatik kapatmak için bildirim erişimi.
adb_ shell cmd notification allow_listener $PKG/$PKG.OturumIzleyici

# Uygulamayı bir kez aç: erişilebilirlik servislerini kendisi açar.
adb_ shell am start -n $PKG/.MainActivity >/dev/null
echo "Tamam. Telefonda Araba Başlatıcı ekranındaki satırların hepsi ✓ olmalı."
