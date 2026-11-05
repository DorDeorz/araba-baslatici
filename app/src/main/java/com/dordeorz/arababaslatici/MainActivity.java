package com.dordeorz.arababaslatici;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.Set;

/** Hangi araba Bluetooth'unda bildirim gösterileceğini seçme ekranı. */
public class MainActivity extends Activity {
    private LinearLayout kok;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        kok = new LinearLayout(this);
        kok.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        kok.setPadding(pad, pad, pad, pad);
        sv.addView(kok);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        OturumIzleyici.yenidenBagla(this);
        if (!Erisilebilirlik.kendiServisiCalisiyor(this)) {
            new Thread(() -> {
                if (Erisilebilirlik.kendiServisiniAc(this)) runOnUiThread(this::ciz);
            }).start();
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        ciz();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        ciz();
    }

    private void ciz() {
        kok.removeAllViews();
        baslik("Araba Başlatıcı");
        yazi("Telefon seçtiğin araba Bluetooth'una bağlanınca \"Android Auto başlatılsın mı?\" "
                + "bildirimi gelir. \"Başlat\"a basınca PhoneAuto açılır ve Android Auto kendiliğinden başlar.");

        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            yazi("⚠ Yakındaki cihazlar (Bluetooth) izni gerekli. Uygulama ayarlarından izin ver.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            yazi("⚠ Bildirim izni kapalı, bildirim gösterilemez.");
        }
        try {
            getPackageManager().getPackageInfo(Bildirim.PHONEAUTO, 0);
        } catch (PackageManager.NameNotFoundException e) {
            yazi("⚠ PhoneAuto yüklü değil.");
        }

        baslik("PhoneAuto servisi");
        boolean calisiyor = Erisilebilirlik.calisiyor(this);
        yazi(calisiyor ? "✓ Çalışıyor" : "✗ Çalışmıyor (PhoneAuto kapatılmış olabilir)");
        yazi(Erisilebilirlik.yetkiVar(this)
                ? "✓ Otomatik onarma açık: Başlat'a basınca gerekirse servis kendiliğinden yeniden açılır."
                : "⚠ Otomatik onarma izni yok. Bilgisayardan bir kez şu komutu çalıştır:\n"
                  + "adb shell pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS");
        if (!calisiyor && Erisilebilirlik.yetkiVar(this)) {
            Button onar = new Button(this);
            onar.setText("Şimdi onar");
            onar.setOnClickListener(v -> {
                v.setEnabled(false);
                new Thread(() -> {
                    Erisilebilirlik.onar(this);
                    runOnUiThread(this::ciz);
                }).start();
            });
            kok.addView(onar);
        }

        yazi(Erisilebilirlik.kendiServisiCalisiyor(this)
                ? "✓ PhoneAuto kapanınca arkada kalan Android Auto ayarları kapatılır."
                : "✗ Ayarları kapatma servisi çalışmıyor.");

        baslik("Otomatik kapatma");
        if (OturumIzleyici.izinVar(this)) {
            yazi("✓ Açık: Android Auto oturumu bitince (" + OturumIzleyici.BEKLEME_MS / 1000
                    + " sn sonra) ya da araba Bluetooth'u kopunca ana birim sunucusu kapatılır.");
        } else {
            yazi("⚠ Bildirim erişimi kapalı, sunucu otomatik kapatılamaz.");
            Button ac = new Button(this);
            ac.setText("Bildirim erişimini aç");
            ac.setOnClickListener(v -> startActivity(new android.content.Intent(
                    android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            OturumIzleyici.bilesen(this).flattenToString())));
            kok.addView(ac);
        }

        BluetoothAdapter ad = getSystemService(BluetoothManager.class).getAdapter();
        Set<BluetoothDevice> eslesmis = ad != null ? ad.getBondedDevices() : null;
        Set<String> secili = Ayarlar.seciliCihazlar(this);

        // İlk açılışta adında "car"/"araba" geçen cihazları önceden seç.
        if (!Ayarlar.ilkKurulumYapildi(this) && eslesmis != null) {
            for (BluetoothDevice d : eslesmis) {
                String n = d.getName() == null ? "" : d.getName().toLowerCase(Locale.ROOT);
                if (n.contains("car") || n.contains("araba") || n.contains("araç")) secili.add(d.getAddress());
            }
            Ayarlar.kaydet(this, secili);
        }

        baslik("Araba olarak sayılacak cihazlar");
        if (eslesmis == null || eslesmis.isEmpty()) {
            yazi("Eşleşmiş Bluetooth cihazı yok.");
        } else {
            for (BluetoothDevice d : eslesmis) {
                CheckBox cb = new CheckBox(this);
                cb.setText((d.getName() != null ? d.getName() : "Adsız cihaz") + "\n" + d.getAddress());
                cb.setChecked(secili.contains(d.getAddress()));
                cb.setPadding(0, dp(8), 0, dp(8));
                String adres = d.getAddress();
                cb.setOnCheckedChangeListener((v, isChecked) -> {
                    Set<String> s = Ayarlar.seciliCihazlar(this);
                    if (isChecked) s.add(adres); else s.remove(adres);
                    Ayarlar.kaydet(this, s);
                });
                kok.addView(cb);
            }
        }

        Button dene = new Button(this);
        dene.setText("Bildirimi şimdi dene");
        dene.setOnClickListener(v -> Bildirim.goster(this, null));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        kok.addView(dene, lp);

    }

    private void baslik(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(16), 0, dp(8));
        kok.addView(t);
    }

    private void yazi(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setPadding(0, dp(4), 0, dp(4));
        kok.addView(t);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
