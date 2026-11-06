package com.dordeorz.arababaslatici

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.dordeorz.arababaslatici.ui.AnaEkran
import com.dordeorz.arababaslatici.ui.AyarlarEkrani
import com.dordeorz.arababaslatici.ui.Karsilama
import com.dordeorz.arababaslatici.ui.Tema
import kotlin.concurrent.thread

/** Ana ekran, ilk açılış karşılaması ve ayarlar (Compose + Material 3). */
class MainActivity : ComponentActivity() {
    /** Ekrana dönülünce ya da izin verilince hazırlık durumu yeniden okunsun diye artar. */
    private var yenile by mutableIntStateOf(0)

    override fun onCreate(b: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(b)
        setContent {
            var tema by remember { mutableIntStateOf(Ayarlar.tema(this)) }
            var dinamik by remember { mutableStateOf(Ayarlar.dinamikRenk(this)) }
            var ayarlar by rememberSaveable { mutableStateOf(false) }
            var karsilama by remember { mutableStateOf(!Ayarlar.karsilamaGoruldu(this)) }
            val koyu = when (tema) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            val gorunum = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, gorunum).apply {
                    isAppearanceLightStatusBars = !koyu
                    isAppearanceLightNavigationBars = !koyu
                }
            }
            Tema(koyu, dinamik) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val ayarlariAc = { ayarlar = true }
                    when {
                        ayarlar -> {
                            BackHandler { ayarlar = false; yenile++ }
                            AyarlarEkrani(
                                geri = { ayarlar = false; yenile++ },
                                tema = tema,
                                temaDegisti = { tema = it; Ayarlar.tema(this, it) },
                                dinamik = dinamik,
                                dinamikDegisti = { dinamik = it; Ayarlar.dinamikRenk(this, it) },
                            )
                        }
                        karsilama -> Karsilama(yenile, ayarlariAc) {
                            karsilama = false
                            Ayarlar.karsilamaGoruldu(this, true)
                        }
                        else -> AnaEkran(yenile, ayarlariAc) {
                            Ayarlar.pilYardimiGizli(this, true)
                            yenile++
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        OturumIzleyici.yenidenBagla(this)
        if (!Erisilebilirlik.calisiyor(this)) {
            thread { if (Erisilebilirlik.ac(this)) runOnUiThread { yenile++ } }
        }
        val eksik = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECORD_AUDIO).filterNot { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (eksik.isNotEmpty()) requestPermissions(eksik.toTypedArray(), 1)
        yenile++
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        yenile++
    }
}
