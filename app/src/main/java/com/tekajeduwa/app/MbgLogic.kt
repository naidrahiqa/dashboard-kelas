package com.tekajeduwa.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

// Status pengambilan makanan MBG seorang siswa
enum class StatusMbg(val label: String) {
    AMBIL("Ambil"),
    BELUM("Belum Ngambil"),
    TIDAK("Gak Ngambil"),
    TEMAN("Dikasih Temen");

    companion object {
        fun dariKode(kode: String): StatusMbg =
            entries.firstOrNull { it.name == kode } ?: BELUM
    }
}

data class SiswaMbg(val id: Long, val nama: String, val status: StatusMbg)

typealias DaftarMbg = List<SiswaMbg>

private const val KEY_MBG = "daftar_mbg"

// Nama baru selalu mulai dari "Belum Ngambil", tinggal diketuk nanti
fun baruSiswaMbg(nama: String): SiswaMbg = SiswaMbg(newId(), nama.trim(), StatusMbg.BELUM)

fun tambahMbg(daftar: DaftarMbg, nama: String): DaftarMbg {
    val bersih = nama.trim()
    if (bersih.isEmpty()) return daftar
    return daftar + baruSiswaMbg(bersih)
}

fun ubahMbg(daftar: DaftarMbg, id: Long, nama: String, status: StatusMbg): DaftarMbg =
    daftar.map {
        if (it.id == id) {
            // nama kosong berarti mempertahankan nama lama
            it.copy(nama = nama.trim().ifEmpty { it.nama }, status = status)
        } else {
            it
        }
    }

fun hapusMbg(daftar: DaftarMbg, id: Long): DaftarMbg = daftar.filterNot { it.id == id }

// Tempel banyak nama sekaligus (satu nama per baris), bisa menambah di akhir
// daftar atau mengganti seluruh daftar. Nama dobel (beda huruf besar/kecil)
// tidak didaftarkan dua kali.
fun daftarDariTeks(teks: String, daftarLama: DaftarMbg, gantiSemua: Boolean): DaftarMbg {
    val awal = if (gantiSemua) emptyList() else daftarLama
    val sudahAda = awal.map { it.nama.lowercase() }.toSet()
    val baru = teks.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.lowercase() !in sudahAda }
        .distinctBy { it.lowercase() }
        .map { baruSiswaMbg(it) }
    return awal + baru
}

fun rekapMbg(daftar: DaftarMbg): Map<StatusMbg, Int> =
    StatusMbg.entries.associateWith { s -> daftar.count { it.status == s } }

fun loadMbg(prefs: SharedPreferences): DaftarMbg {
    val teks = prefs.getString(KEY_MBG, null) ?: return emptyList()
    return runCatching {
        val arr = JSONArray(teks)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val nama = obj.optString("nama").trim()
                if (nama.isNotEmpty()) {
                    add(SiswaMbg(newId(), nama, StatusMbg.dariKode(obj.optString("status"))))
                }
            }
        }
    }.getOrElse { emptyList() }
}

fun saveMbg(prefs: SharedPreferences, daftar: DaftarMbg) {
    val arr = JSONArray()
    daftar.forEach { s ->
        arr.put(JSONObject().put("nama", s.nama).put("status", s.status.name))
    }
    prefs.edit().putString(KEY_MBG, arr.toString()).apply()
}
