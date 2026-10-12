package com.tekajeduwa.app

import android.content.SharedPreferences
import kotlin.math.abs

// 4 barisan ke samping; barisan ke-3 punya 5 meja ke belakang
val COLUMN_SIZES = listOf(4, 4, 5, 4)
val MAX_DEPTH = COLUMN_SIZES.max()

private val idCounter = java.util.concurrent.atomic.AtomicLong(1)

fun newId(): Long = idCounter.getAndIncrement()

data class Siswa(val id: Long, val nama: String)

typealias Seats = List<List<Siswa?>>

// Posisi kursi: kolom (B1..B4) to baris (meja 1 = paling depan)
typealias Posisi = Pair<Int, Int>

// Riwayat: tiap kursi menyimpan nama siswa yang pernah duduk di situ
typealias Riwayat = Map<Posisi, Set<String>>

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

fun pernahDuduk(riwayat: Riwayat, pos: Posisi, nama: String): Boolean =
    nama in (riwayat[pos] ?: emptySet())

// Catat semua penghuni kursi sekarang ke riwayat, supaya periode berikutnya
// siswa yang sama tidak ditempatkan lagi di kursi yang sama.
fun catatRiwayat(seats: Seats, riwayat: Riwayat): Riwayat {
    val hasil = HashMap(riwayat)
    seats.forEachIndexed { c, col ->
        col.forEachIndexed { d, s ->
            if (s != null) {
                val pos = c to d
                hasil[pos] = (hasil[pos] ?: emptySet()) + s.nama
            }
        }
    }
    return hasil
}

private fun nearestFree(
    targetCol: Int,
    targetDepth: Int,
    occupied: Set<Posisi>,
): Posisi {
    var best: Posisi? = null
    var bestDist = Int.MAX_VALUE
    for (c in COLUMN_SIZES.indices) {
        for (d in 0 until COLUMN_SIZES[c]) {
            if (c to d in occupied) continue
            val dist = abs(c - targetCol) + abs(d - targetDepth)
            if (dist < bestDist) {
                bestDist = dist
                best = c to d
            }
        }
    }
    return best ?: (targetCol to targetDepth)
}

fun rollSeats(prev: Seats): Seats {
    val occupied = mutableSetOf<Posisi>()
    val placed = emptySeats().map { it.toMutableList() }

    data class Mover(val s: Siswa, val c: Int, val d: Int)

    val movers = buildList {
        prev.forEachIndexed { c, col ->
            col.forEachIndexed { d, s ->
                if (s != null) add(Mover(s, c, d))
            }
        }
    }.sortedWith(compareBy({ it.d }, { it.c }))

    val numCols = COLUMN_SIZES.size

    for (m in movers) {
        // selalu ke barisan sebelah kanan, wrap-around dari B4 balik ke B1
        val t = (m.c + 1) % numCols
        // paling depan pindah ke paling belakang, lainnya maju satu meja
        var nd = if (m.d == 0) COLUMN_SIZES[t] - 1 else m.d - 1
        if (nd >= COLUMN_SIZES[t]) nd = COLUMN_SIZES[t] - 1

        val target =
            if (t to nd in occupied) nearestFree(t, nd, occupied) else (t to nd)
        occupied.add(target)
        placed[target.first][target.second] = m.s
    }
    return placed
}

// Acak tempat duduk tanpa mengulang: tiap siswa hanya boleh duduk di kursi
// yang belum pernah ia duduki. Padanan siswa-kursi dicari dengan algoritma
// Kuhn (pasangan maksimum) berurutan acak, jadi kalau ada padanan valid pasti
// ketemu, dan hasilnya tetap acak. Siswa yang padanannya habis (semua kursi
// sudah pernah ia duduki) ditempatkan di kursi sisa secara acak.
fun shuffleSeats(prev: Seats, riwayat: Riwayat): Seats {
    val posisi = mutableListOf<Posisi>()
    prev.forEachIndexed { c, col ->
        col.forEachIndexed { d, s -> if (s != null) posisi.add(c to d) }
    }
    val n = posisi.size
    if (n == 0) return prev

    val nama = posisi.map { prev[it.first][it.second]!!.nama }

    // kandidat kursi valid buat tiap siswa; urutannya diacak biar hasil random
    val kandidat = nama.map { namaSiswa ->
        posisi.indices.filter { v -> !pernahDuduk(riwayat, posisi[v], namaSiswa) }.shuffled()
    }

    // kursiKeSiswa[v] = indeks siswa yang duduk di kursi v (-1 kalau belum ada)
    val kursiKeSiswa = IntArray(n) { -1 }

    fun pasang(u: Int, sudah: BooleanArray): Boolean {
        for (v in kandidat[u]) {
            if (sudah[v]) continue
            sudah[v] = true
            if (kursiKeSiswa[v] == -1 || pasang(kursiKeSiswa[v], sudah)) {
                kursiKeSiswa[v] = u
                return true
            }
        }
        return false
    }

    for (u in nama.indices.shuffled()) pasang(u, BooleanArray(n))

    val hasil: Array<Posisi?> = arrayOfNulls(n)
    val terpakai = BooleanArray(n)
    for (v in 0 until n) {
        val u = kursiKeSiswa[v]
        if (u != -1) {
            hasil[u] = posisi[v]
            terpakai[v] = true
        }
    }

    // siswa yang nggak dapat kursi baru -> kursi yang tersisa, acak
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
            if (s != null && pernahDuduk(riwayat, c to d, s.nama)) ulang++
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

fun loadRiwayat(prefs: SharedPreferences): Riwayat = buildMap {
    for ((c, n) in COLUMN_SIZES.withIndex()) {
        for (d in 0 until n) {
            prefs.getStringSet(histKey(c to d), null)
                ?.takeIf { it.isNotEmpty() }
                // salinan, soalnya SharedPreferences nggak boleh dimodifikasi
                ?.let { put(c to d, it.toSet()) }
        }
    }
}

private fun histKey(pos: Posisi) = "hist_${pos.first}_${pos.second}"

fun saveSeats(prefs: SharedPreferences, seats: Seats, period: Int, riwayat: Riwayat) {
    prefs.edit().apply {
        clear()
        seats.forEachIndexed { c, col ->
            col.forEachIndexed { d, s ->
                if (s != null) putString("k${c}d${d}", s.nama)
            }
        }
        riwayat.forEach { (pos, nama) ->
            if (nama.isNotEmpty()) putStringSet(histKey(pos), HashSet(nama))
        }
        putInt("period", period)
        apply()
    }
}
