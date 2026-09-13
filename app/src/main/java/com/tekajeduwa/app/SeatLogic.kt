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

fun emptySeats(): Seats = COLUMN_SIZES.map { n -> List(n) { null } }

// daftar posisi urut dari depan ke belakang (untuk isi cepat)
fun semuaPosisi(): List<Pair<Int, Int>> = buildList {
    for (d in 0 until MAX_DEPTH) {
        for (c in COLUMN_SIZES.indices) {
            if (d < COLUMN_SIZES[c]) add(c to d)
        }
    }
}

private fun nearestFree(
    targetCol: Int,
    targetDepth: Int,
    occupied: Set<Pair<Int, Int>>,
): Pair<Int, Int> {
    var best: Pair<Int, Int>? = null
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
    val occupied = mutableSetOf<Pair<Int, Int>>()
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

fun shuffleSeats(prev: Seats): Seats {
    val nama = prev.flatten().filterNotNull().map { it.nama }.shuffled()
    var i = 0
    return prev.map { col ->
        col.map {
            if (i < nama.size) Siswa(newId(), nama[i++]) else null
        }
    }
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

fun saveSeats(prefs: SharedPreferences, seats: Seats, period: Int) {
    prefs.edit().apply {
        clear()
        seats.forEachIndexed { c, col ->
            col.forEachIndexed { d, s ->
                if (s != null) putString("k${c}d${d}", s.nama)
            }
        }
        putInt("period", period)
        apply()
    }
}
