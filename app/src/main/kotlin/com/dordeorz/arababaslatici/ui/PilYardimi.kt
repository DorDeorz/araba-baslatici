package com.dordeorz.arababaslatici.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Marka bazlı pil/otomatik başlatma yardımı (Kronometre'deki OemProfile ile aynı tablo).
 * Araba Başlatıcı Bluetooth bağlanınca arka planda tetiklendiği için saldırgan ROM'larda
 * (HyperOS, ColorOS, One UI…) uygulama uyutulursa bildirim hiç gelmez.
 */
enum class Hedef { OtomatikBaslatma, PilMuafiyeti, UygulamaAyrintilari, Elle }

data class Adim(val metin: String, val hedef: Hedef)

private val PIL = Adim("Ayarlar → Uygulamalar → Araba Başlatıcı → Pil → Kısıtlama yok", Hedef.PilMuafiyeti)

enum class Marka(
    val ureticiler: List<String>,
    val romIzleri: List<String>,
    val saldirgan: Boolean,
    val otomatikBaslatma: List<ComponentName>,
    val adimlar: List<Adim>,
) {
    Xiaomi(
        listOf("xiaomi", "redmi", "poco"), listOf("miui", "hyperos"), true,
        listOf(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        listOf(
            Adim("Güvenlik → Otomatik başlatma → Araba Başlatıcı → Aç", Hedef.OtomatikBaslatma),
            PIL,
            Adim("Erişilebilirlik izni kapanırsa: uygulamayı bir kez açmak yeter, kendisi yeniden açar", Hedef.Elle),
        ),
    ),
    Huawei(
        listOf("huawei", "honor"), listOf("emui", "magicos", "harmonyos"), true,
        listOf(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        listOf(Adim("Pil → Uygulama başlatma → Araba Başlatıcı → Otomatik yönetimi kapat, üç izni de aç", Hedef.OtomatikBaslatma), PIL),
    ),
    Oppo(
        listOf("oppo", "realme", "oneplus"), listOf("coloros", "oxygenos", "realmeui"), true,
        listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        ),
        listOf(
            Adim("Pil → Arka plan etkinliği → Araba Başlatıcı: Arka planda çalıştırmaya izin ver", Hedef.UygulamaAyrintilari),
            Adim("Otomatik başlatma → Araba Başlatıcı → Aç", Hedef.OtomatikBaslatma),
            PIL,
        ),
    ),
    Vivo(
        listOf("vivo", "iqoo"), listOf("funtouch", "originos"), true,
        listOf(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        listOf(
            Adim("Pil → Arka plan yönetimi → Araba Başlatıcı'yı beyaz listeye al", Hedef.UygulamaAyrintilari),
            Adim("Otomatik başlatma → Araba Başlatıcı → Aç", Hedef.OtomatikBaslatma),
            PIL,
        ),
    ),
    Samsung(
        listOf("samsung"), emptyList(), true, emptyList(),
        listOf(
            Adim("Pil → Arka plan kullanımı → Kısıtlama yok", Hedef.PilMuafiyeti),
            Adim("Pil → Arka plan kullanımı sınırları → Hiç uykuya dalmayan uygulamalar → Araba Başlatıcı ekle", Hedef.UygulamaAyrintilari),
        ),
    ),
    Asus(
        listOf("asus"), emptyList(), true,
        listOf(ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")),
        listOf(Adim("Otomatik başlatma → Araba Başlatıcı'ya izin ver", Hedef.OtomatikBaslatma), PIL),
    ),
    Transsion(
        listOf("transsion", "infinix", "tecno", "itel"), emptyList(), true,
        listOf(
            ComponentName("com.cyin.himgr", "com.cyin.himgr.AutobootManageActivity"),
            ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
        ),
        listOf(Adim("Otomatik yönetim → Araba Başlatıcı → üç izni de aç", Hedef.OtomatikBaslatma), PIL),
    ),
    Diger(emptyList(), emptyList(), false, emptyList(), listOf(PIL));

    companion object {
        fun bu(): Marka {
            val uretici = listOf(Build.MANUFACTURER, Build.BRAND).joinToString(" ") { it.orEmpty().lowercase() }
            val rom = listOf(Build.DISPLAY, Build.FINGERPRINT).joinToString(" ") { it.orEmpty().lowercase() }
            return entries.firstOrNull { m -> m.ureticiler.any { uretici.contains(it) } }
                ?: entries.firstOrNull { m -> m.romIzleri.any { rom.contains(it) } }
                ?: Diger
        }
    }
}

object PilYardimi {
    fun muaf(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    fun ac(c: Context, m: Marka, h: Hedef): Boolean = when (h) {
        Hedef.OtomatikBaslatma -> m.otomatikBaslatma.any { dene(c, Intent().setComponent(it)) } ||
            ayrintilar(c) || pilListesi(c)
        Hedef.PilMuafiyeti -> muafiyetIste(c) || ayrintilar(c) || pilListesi(c)
        Hedef.UygulamaAyrintilari -> ayrintilar(c) || pilListesi(c)
        Hedef.Elle -> false
    }

    @SuppressLint("BatteryLife") // Uygulama Bluetooth'la arkadan tetikleniyor; muafiyet gerçekten gerekli.
    private fun muafiyetIste(c: Context) =
        dene(c, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, paket(c)))

    private fun ayrintilar(c: Context) = dene(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, paket(c)))
    private fun pilListesi(c: Context) = dene(c, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    private fun paket(c: Context) = Uri.parse("package:${c.packageName}")

    private fun dene(c: Context, i: Intent): Boolean = try {
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
}

@Composable
fun PilYardimiKarti(marka: Marka, kapat: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = LocalContext.current
    var yapilan by rememberSaveable { mutableStateOf(IntArray(0)) }
    var acilamayan by rememberSaveable { mutableStateOf(IntArray(0)) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (marka.saldirgan) "Arka planda uyutulmasın" else "Pil optimizasyonu muafiyeti",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Bu telefon arka plandaki uygulamaları kapatabilir. O zaman arabaya bağlanınca " +
                    "\"Başlatılsın mı?\" bildirimi gelmez. Aşağıdaki adımlar bunu önler.",
                style = MaterialTheme.typography.bodyMedium,
            )
            marka.adimlar.forEachIndexed { i, adim ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = i in yapilan, onCheckedChange = { v ->
                        yapilan = if (v) yapilan + i else yapilan.filterNot { it == i }.toIntArray()
                    })
                    Column(Modifier.weight(1f)) {
                        Text(adim.metin, style = MaterialTheme.typography.bodyMedium)
                        if (i in acilamayan) {
                            Text(
                                "Bu ekran bu telefonda açılamadı; adımı Ayarlar'dan elle yap.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (adim.hedef != Hedef.Elle) {
                        TextButton(onClick = {
                            acilamayan = if (PilYardimi.ac(c, marka, adim.hedef)) acilamayan.filterNot { it == i }.toIntArray()
                            else if (i in acilamayan) acilamayan else acilamayan + i
                        }) { Text("Aç") }
                    }
                }
            }
            if (kapat != null) {
                TextButton(onClick = kapat, modifier = Modifier.align(Alignment.End)) { Text("Bir daha gösterme") }
            }
        }
    }
}
