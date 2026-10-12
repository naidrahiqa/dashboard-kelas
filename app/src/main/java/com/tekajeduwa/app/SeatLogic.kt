package com.tekajeduwa.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

// 4 barisan ke samping; barisan ke-3 punya 5 meja ke belakang
val COLUMN_SIZES = listOf(4, 4, 5, 4)
val MAX_DEPTH = COLUMN_SIZES.max()

// Berapa periode terakhir kursi yang diingat per siswa. Tiap siswa dilarang
// duduk di kursi yang pernah ia duduki dalam jangka waktu ini, jadi tempat
// duduknya terus berganti dan hasilnya tetap acak -- bukan jadi deterministik
// setelah semua kombinasi kursi habis.
const val JENDELA_RIWAYAT = 8

private val idCounter = java.util.concurrent.atomic.AtomicLong(1)

fun newId(): Long = idCounter.getAndIncrement()

data class Siswa(val id: Long, val nama: String)

typealias Seats = List<List<Siswa?>>

// Posisi kursi: kolom (B1..B4) to baris (meja 1 = paling depan)
typealias Posisi = Pair<Int, Int>

// Riwayat: per nama siswa, daftar kursi yang pernah ia duduki,
// urut dari yang paling lama ke paling baru
typealias Riwayat = Map<String, List<Posisi>>

fun emptySeats(): Seats = COLUMN_SIZES.map { n -> List(n) { null } }

fun emptyRiwayat(): Riwayat = emptyMap()

// daftar posisi urut dari depan ke belakang (untuk isi cepat)
fun semuaPosisi(): List<Posisi> = buildList {
    for (d in 0 until MAX_DEPTH) {
        for (c in COLUMN_SIZES.indices) {
            if (d < COLUMN_SIZES[c]) add(c to d)
        }
    }
}

fun pernahDuduk(riwayat: Riwayat, nama: String, pos: Posisi): Boolean =
    pos in (riwayat[nama] ?: emptyList())

// Catat semua penghuni kursi sekarang ke riwayat. Kursi yang sama tidak
// dicatat dua kali berturut-turut (misal karena menyimpan data lain), dan
// riwayat dibatasi JENDELA_RIWAYAT entri terakhir per siswa.
fun catatRiwayat(seats: Seats, riwayat: Riwayat): Riwayat {
    val hasil = riwayat.mapValues { it.value.toList() }.toMutableMap()
    seats.forEachIndexed { c, col ->
        col.forEachIndexed { d, s ->
            if (s != null) {
                val pos = c to d
                val lama = hasil[s.nama] ?: emptyList()
                if (lama.lastOrNull() != pos) {
                    hasil[s.nama] = (lama + pos).takeLast(JENDELA_RIWAYAT)
                }
            }
        }
    }
    return hasil
}

// Acak tempat duduk tanpa mengulang: tiap siswa hanya boleh duduk di kursi
// yang belum pernah ia duduki dalam jendela riwayatnya. Kursi yang sedang
// terisi sekarang pun dihitung sudah pernah diduduki, jadi siswa tidak pernah
// balik ke kursinya sendiri walau riwayat di prefs masih kosong.
//
// Padanan siswa-kursi dicari dengan algoritma Kuhn (pasangan maksimum)
// berurutan acak: kalau ada padanan valid pasti ketemu, dan hasilnya acak.
// Kalau jendela riwayat membuat padanan valid mustahil (papan kecil),
// riwayat paling lama siswa yang belum kebagian dilupakan lalu dicari lagi.
fun shuffleSeats(prev: Seats, riwayat: Riwayat): Seats {
    val posisi = mutableListOf<Posisi>()
    prev.forEachIndexed { c, col ->
        col.forEachIndexed { d, s -> if (s != null) posisi.add(c to d) }
    }
    val n = posisi.size
    if (n == 0) return prev

    val nama = posisi.map { prev[it.first][it.second]!!.nama }

    // riwayat efektif: yang sudah tersimpan + kursi yang sedang terisi sekarang
    var riwayatKini = catatRiwayat(prev, riwayat)

    // papan kecil: sisakan minimal 2 kursi bebas per siswa supaya tetap acak
    val jendela = JENDELA_RIWAYAT.coerceAtMost((n - 2).coerceAtLeast(1))

    fun kandidatSiswa(namaSiswa: String, riwayatEff: Riwayat): List<Int> {
        val terlarang = (riwayatEff[namaSiswa] ?: emptyList()).takeLast(jendela).toSet()
        return posisi.indices.filter { v -> posisi[v] !in terlarang }
    }

    // kursiKeSiswa[v] = indeks siswa yang duduk di kursi v (-1 kalau belum ada)
    val kursiKeSiswa = IntArray(n) { -1 }
    val siswaDuduk = IntArray(n) { -1 }

    var coba = 0
    while (coba++ <= n) {
        val kandidat = nama.map { kandidatSiswa(it, riwayatKini).shuffled() }

        kursiKeSiswa.fill(-1)
        siswaDuduk.fill(-1)

        fun pasang(u: Int, sudah: BooleanArray): Boolean {
            for (v in kandidat[u]) {
                if (sudah[v]) continue
                sudah[v] = true
                if (kursiKeSiswa[v] == -1 || pasang(kursiKeSiswa[v], sudah)) {
                    kursiKeSiswa[v] = u
                    siswaDuduk[u] = v
                    return true
                }
            }
            return false
        }

        for (u in nama.indices.shuffled()) pasang(u, BooleanArray(n))

        if (siswaDuduk.none { it == -1 }) break

        // siswa yang kursinya sudah habis -> lupakan riwayat paling lamanya
        val belum = siswaDuduk.indices.first { siswaDuduk[it] == -1 }
        val riwayatSiswa = riwayatKini[nama[belum]] ?: break
        if (riwayatSiswa.isEmpty()) break
        riwayatKini = riwayatKini + (nama[belum] to riwayatSiswa.drop(1))
    }

    val hasil: Array<Posisi?> = arrayOfNulls(n)
    val terpakai = BooleanArray(n)
    for (v in 0 until n) {
        val u = kursiKeSiswa[v]
        if (u != -1) {
            hasil[u] = posisi[v]
            terpakai[v] = true
        }
    }

    // siswa yang masih nggak kebagian -> kursi yang tersisa, acak
    val sisa = posisi.indices.filter { !terpakai[it] }.shuffled().iterator()
    for (u in nama.indices.shuffled()) {
        if (hasil[u] == null) hasil[u] = posisi[sisa.next()]
    }

    val duduk = HashMap<Posisi, String>(n)
    hasil.forEachIndexed { u, p -> duduk[p!!] = nama[u] }

    return prev.mapIndexed { c, col ->
        col.mapIndexed { d, s ->
            if (s == null) null else Siswa(newId(), duduk.getValue(c to d))
        }
    }
}

// Berapa siswa yang terpaksa mengulang kursi lamanya setelah diacak
fun jumlahPengulang(riwayat: Riwayat, hasil: Seats): Int {
    var ulang = 0
    hasil.forEachIndexed { c, col ->
        col.forEachIndexed { d, s ->
            if (s != null && pernahDuduk(riwayat, s.nama, c to d)) ulang++
        }
    }
    return ulang
}

fun fillFromText(prev: Seats, text: String, gantiSemua: Boolean): Seats {
    val nama = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    var i = 0
    val hasil = prev.map { it.toMutableList() }
    for ((c, d) in semuaPosisi()) {
        if (!gantiSemua && hasil[c][d] != null) continue
        if (i >= nama.size) {
            if (gantiSemua) hasil[c][d] = null
            continue
        }
        hasil[c][d] = Siswa(newId(), nama[i++])
    }
    return hasil
}

fun loadSeats(prefs: SharedPreferences): Seats =
    COLUMN_SIZES.mapIndexed { c, n ->
        List(n) { d ->
            prefs.getString("k${c}d${d}", null)?.let { Siswa(newId(), it) }
        }
    }

// Riwayat disimpan sebagai satu JSON: {"nama":[[kolom,baris], ...], ...}
private const val KEY_RIWAYAT = "riwayat_kursi"

fun loadRiwayat(prefs: SharedPreferences): Riwayat {
    val teks = prefs.getString(KEY_RIWAYAT, null) ?: return emptyRiwayat()
    return runCatching {
        val obj = JSONObject(teks)
        buildMap {
            for (nama in obj.keys()) {
                val arr = obj.optJSONArray(nama) ?: continue
                val daftar = buildList {
                    for (i in 0 until arr.length()) {
                        val pair = arr.optJSONArray(i) ?: continue
                        if (pair.length() >= 2) add(pair.getInt(0) to pair.getInt(1))
                    }
                }
                if (daftar.isNotEmpty()) put(nama, daftar)
            }
        }
    }.getOrElse { emptyRiwayat() }
}

fun saveSeats(prefs: SharedPreferences, seats: Seats, period: Int, riwayat: Riwayat) {
    val obj = JSONObject()
    riwayat.forEach { (nama, posisi) ->
        if (posisi.isEmpty()) return@forEach
        val arr = JSONArray()
        posisi.forEach { arr.put(JSONArray().put(it.first).put(it.second)) }
        obj.put(nama, arr)
    }
    prefs.edit().apply {
        clear()
        seats.forEachIndexed { c, col ->
            col.forEachIndexed { d, s ->
                if (s != null) putString("k${c}d${d}", s.nama)
            }
        }
        putString(KEY_RIWAYAT, obj.toString())
        putInt("period", period)
        apply()
    }
}
