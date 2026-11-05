package com.dordeorz.arababaslatici;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * Bildirimdeki "Başlat": önce PhoneAuto'nun erişilebilirlik servisini gerekirse onarır,
 * sonra PhoneAuto'yu Android Auto'yu başlatan ekranında açar. Görünmez bir ara ekrandır.
 */
public class BaslatActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Bildirim.iptal(this);
        OturumIzleyici.yenidenBagla(this);
        Handler ana = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Erisilebilirlik.kendiServisiniAc(this);
            boolean zatenCalisiyor = Erisilebilirlik.calisiyor(this);
            boolean tamam = zatenCalisiyor || Erisilebilirlik.onar(this);
            ana.post(() -> {
                if (!zatenCalisiyor) {
                    Toast.makeText(this, tamam
                            ? "PhoneAuto servisi yeniden açıldı"
                            : "PhoneAuto servisi açılamadı; Erişilebilirlik'ten aç",
                            Toast.LENGTH_LONG).show();
                }
                startActivity(Bildirim.phoneAutoIntent());
                finish();
            });
        }).start();
    }
}
