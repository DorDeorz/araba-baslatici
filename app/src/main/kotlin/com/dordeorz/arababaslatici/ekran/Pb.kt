package com.dordeorz.arababaslatici.ekran

import java.io.ByteArrayOutputStream

/**
 * AAP mesajları için en küçük protobuf (proto2) yazıcı/okuyucu. Protobuf kütüphanesi ve Gradle
 * eklentisi yerine; kullandığımız alanlar az ve basit (varint, metin, iç mesaj).
 */
object Pb {

    /** Mesaj yazıcı. Alanları sırayla ekle, [bayt] ile al. */
    class Yaz {
        private val o = ByteArrayOutputStream()

        fun varint(alan: Int, deger: Long): Yaz { anahtar(alan, 0); ham(deger); return this }
        fun varint(alan: Int, deger: Int): Yaz = varint(alan, deger.toLong())
        fun bool(alan: Int, deger: Boolean): Yaz = varint(alan, if (deger) 1L else 0L)
        fun metin(alan: Int, deger: String): Yaz = bayt(alan, deger.toByteArray(Charsets.UTF_8))
        fun mesaj(alan: Int, ic: Yaz): Yaz = bayt(alan, ic.bayt())
        fun mesaj(alan: Int, kur: Yaz.() -> Unit): Yaz = mesaj(alan, Yaz().apply(kur))

        fun bayt(alan: Int, deger: ByteArray): Yaz {
            anahtar(alan, 2); ham(deger.size.toLong()); o.write(deger); return this
        }

        fun bayt(): ByteArray = o.toByteArray()

        private fun anahtar(alan: Int, tur: Int) = ham(((alan shl 3) or tur).toLong())

        private fun ham(d: Long) {
            var v = d
            while (true) {
                if (v and 0x7FL.inv() == 0L) { o.write(v.toInt()); return }
                o.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }
    }

    fun yaz(kur: Yaz.() -> Unit): ByteArray = Yaz().apply(kur).bayt()

    /** Çözülmüş mesaj: alan numarası → değerler (varint için Long, uzunluklu için ByteArray). */
    class Okunan(private val alanlar: Map<Int, List<Any>>) {
        fun sayi(alan: Int, varsayilan: Long = 0): Long = alanlar[alan]?.firstOrNull() as? Long ?: varsayilan
        fun int(alan: Int, varsayilan: Int = 0): Int = sayi(alan, varsayilan.toLong()).toInt()
        fun bool(alan: Int): Boolean = sayi(alan) != 0L
        fun varMi(alan: Int): Boolean = alanlar.containsKey(alan)
        fun ic(alan: Int): Okunan? = (alanlar[alan]?.firstOrNull() as? ByteArray)?.let { oku(it) }

        /** sint32/sint64 (zigzag) alan. */
        fun isaretli(alan: Int): Long { val v = sayi(alan); return (v ushr 1) xor -(v and 1) }

        override fun toString() = alanlar.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            "$k=" + v.joinToString(",") { if (it is ByteArray) "[${it.size} B]" else it.toString() }
        }
    }

    fun oku(b: ByteArray, bas: Int = 0, son: Int = b.size): Okunan {
        val alanlar = HashMap<Int, MutableList<Any>>()
        var i = bas
        fun ham(): Long {
            var sonuc = 0L
            var kayma = 0
            while (true) {
                val c = b[i++].toInt() and 0xFF
                sonuc = sonuc or ((c and 0x7F).toLong() shl kayma)
                if (c and 0x80 == 0) return sonuc
                kayma += 7
            }
        }
        while (i < son) {
            val anahtar = ham()
            val alan = (anahtar ushr 3).toInt()
            val deger: Any = when ((anahtar and 7).toInt()) {
                0 -> ham()
                1 -> { var v = 0L; for (k in 0 until 8) v = v or ((b[i + k].toLong() and 0xFF) shl (8 * k)); i += 8; v }
                2 -> { val n = ham().toInt(); b.copyOfRange(i, i + n).also { i += n } }
                5 -> { var v = 0L; for (k in 0 until 4) v = v or ((b[i + k].toLong() and 0xFF) shl (8 * k)); i += 4; v }
                else -> return Okunan(alanlar) // tanımadığımız tür: kalanı bırak
            }
            alanlar.getOrPut(alan) { ArrayList(1) }.add(deger)
        }
        return Okunan(alanlar)
    }
}
