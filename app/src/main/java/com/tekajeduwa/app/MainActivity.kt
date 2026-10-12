package com.tekajeduwa.app

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Fallback buat HP di bawah Android 12: hijau khas app sekolah
private val LightGreenScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF2E6B34),
    secondary = androidx.compose.ui.graphics.Color(0xFF52634F),
    tertiary = androidx.compose.ui.graphics.Color(0xFF38656A),
)

private val DarkGreenScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFA2D391),
    secondary = androidx.compose.ui.graphics.Color(0xFFB8CCB4),
    tertiary = androidx.compose.ui.graphics.Color(0xFFA2CED4),
)

// Layar yang sedang tampil: papan tempat duduk atau daftar MBG
enum class Tampilan { KURSI, MBG }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("tekajeduwa", MODE_PRIVATE)
        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dark -> DarkGreenScheme
                else -> LightGreenScheme
            }
            MaterialTheme(colorScheme = scheme) {
                App(prefs)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(prefs: SharedPreferences) {
    var seats by remember { mutableStateOf(loadSeats(prefs)) }
    var period by remember { mutableStateOf(prefs.getInt("period", 0)) }
    var riwayat by remember { mutableStateOf(loadRiwayat(prefs)) }
    var tampilan by remember { mutableStateOf(Tampilan.KURSI) }
    var daftarMbg by remember { mutableStateOf(loadMbg(prefs)) }
    var mbgTarget by remember { mutableStateOf<Long?>(null) }
    var showMbgTambah by remember { mutableStateOf(false) }
    var showMbgBulk by remember { mutableStateOf(false) }
    var showMbgReset by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showBulk by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showRollResult by remember { mutableStateOf(false) }
    var rollResultSeats by remember { mutableStateOf<Seats?>(null) }
    var acakInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var isShuffling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun simpan(s: Seats, p: Int = period) {
        // sekalian catat ke riwayat: siapa yang baru duduk di kursi mana
        val r = catatRiwayat(s, riwayat)
        seats = s
        period = p
        riwayat = r
        saveSeats(prefs, s, p, r)
    }

    fun simpanMbg(d: DaftarMbg) {
        daftarMbg = d
        saveMbg(prefs, d)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (tampilan == Tampilan.KURSI) "Dashboard Kelas" else "Daftar MBG")
                },
                actions = {
                    if (tampilan == Tampilan.KURSI) {
                        IconButton(onClick = { showBulk = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Isi nama cepat")
                        }
                        IconButton(onClick = { showResetConfirm = true }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reset")
                        }
                        IconButton(onClick = { tampilan = Tampilan.MBG }) {
                            Icon(Icons.Filled.Person, contentDescription = "Daftar MBG")
                        }
                    } else {
                        IconButton(onClick = { showMbgTambah = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Tambah siswa")
                        }
                        IconButton(onClick = { showMbgBulk = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Isi nama cepat")
                        }
                        IconButton(onClick = { showMbgReset = true }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reset")
                        }
                        IconButton(onClick = { tampilan = Tampilan.KURSI }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali ke papan kursi")
                        }
                    }
                },
            )
        },
        bottomBar = {
            // tombol rolling & acak hanya berlaku di papan tempat duduk
            if (tampilan == Tampilan.KURSI) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            val hasil = shuffleSeats(seats, riwayat)
                            simpan(hasil, period + 1)
                            rollResultSeats = hasil
                            showRollResult = true
                        },
                        modifier = Modifier.weight(1.4f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Rolling Periode") }

                    FilledTonalButton(
                        onClick = {
                            if (!isShuffling && seats.flatten().any { it != null }) {
                                isShuffling = true
                                scope.launch {
                                    val names = seats.flatten().filterNotNull().map { it.nama }
                                    val startSeats = seats
                                    val endSeats = shuffleSeats(seats, riwayat)
                                    val total = endSeats.flatten().count { it != null }
                                    val ulang = jumlahPengulang(riwayat, endSeats)
                                    // animasi pura-pura acak selama ~3 detik
                                    val totalFrames = 18
                                    val frameDelay = 170L // ~3 detik total
                                    for (frame in 0 until totalFrames) {
                                        seats = if (frame < totalFrames - 1) {
                                            // frame acak: random positions buat visual
                                            val shuffled = names.shuffled()
                                            var idx = 0
                                            startSeats.map { col ->
                                                col.map { s ->
                                                    if (s != null && idx < shuffled.size) {
                                                        Siswa(newId(), shuffled[idx++])
                                                    } else if (s != null) {
                                                        s
                                                    } else {
                                                        null
                                                    }
                                                }
                                            }
                                        } else {
                                            endSeats
                                        }
                                        delay(frameDelay)
                                    }
                                    simpan(endSeats)
                                    isShuffling = false
                                    acakInfo = (total - ulang) to ulang
                                }
                            }
                        },
                        enabled = !isShuffling,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (isShuffling) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp).width(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Acak")
                        }
                    }
                }
            }
        },
    ) { inner ->
        if (tampilan == Tampilan.MBG) {
            MbgContent(
                daftar = daftarMbg,
                padding = inner,
                onPilih = { mbgTarget = it },
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Ringkasan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    nilai = period.toString(),
                    label = "Periode",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    nilai = seats.flatten().filterNotNull().size.toString(),
                    label = "Siswa",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))

            // Papan tempat duduk sebagai satu kartu besar
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "DEPAN KELAS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(8.dp))

                        // Header barisan
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.width(22.dp))
                            COLUMN_SIZES.indices.forEach { c ->
                                Text(
                                    "B${c + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Grid kursi
                        for (d in 0 until MAX_DEPTH) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${d + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(22.dp),
                                )
                                seats.forEachIndexed { c, col ->
                                    Box(Modifier.weight(1f).padding(horizontal = 3.dp)) {
                                        if (d < col.size) {
                                            SeatCell(
                                                siswa = col[d],
                                                onClick = { if (!isShuffling) editTarget = c to d },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // overlay acak
                    if (isShuffling) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(40.dp).width(40.dp),
                                strokeWidth = 3.dp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Ketuk kursi untuk mengisi nama. Tombol Acak dan Rolling Periode " +
                    "sama-sama membagi kursi secara acak, dan tidak akan " +
                    "menempatkan siswa di kursi yang pernah ia duduki selama " +
                    "masih ada kursi yang belum pernah ia pakai. Rolling Periode " +
                    "sekalian menambah hitungan periode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    // Dialog edit satu kursi
    editTarget?.let { (c, d) ->
        var draft by remember(c to d) { mutableStateOf(seats[c][d]?.nama ?: "") }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Barisan ${c + 1}, Meja ${d + 1}") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Nama siswa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nama = draft.trim()
                        simpan(
                            seats.mapIndexed { ci, col ->
                                col.mapIndexed { di, s ->
                                    when {
                                        ci == c && di == d && nama.isNotEmpty() -> Siswa(newId(), nama)
                                        ci == c && di == d -> null
                                        else -> s
                                    }
                                }
                            },
                        )
                        editTarget = null
                    },
                ) { Text("Simpan") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        simpan(
                            seats.mapIndexed { ci, col ->
                                col.mapIndexed { di, s -> if (ci == c && di == d) null else s }
                            },
                        )
                        editTarget = null
                    }) { Text("Hapus") }
                    TextButton(onClick = { editTarget = null }) { Text("Batal") }
                }
            },
        )
    }

    // Dialog isi nama cepat
    if (showBulk) {
        BulkDialog(
            onDismiss = { showBulk = false },
            onApply = { text, ganti ->
                simpan(fillFromText(seats, text, ganti))
                showBulk = false
            },
        )
    }

    // Dialog konfirmasi reset
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset semua?") },
            text = { Text("Semua nama siswa, hitungan periode, dan riwayat tempat duduk akan dihapus.") },
            confirmButton = {
                Button(onClick = {
                    // reset juga membuang riwayat, jadi semua kursi "baru" lagi
                    riwayat = emptyRiwayat()
                    simpan(emptySeats(), 0)
                    showResetConfirm = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Batal") }
            },
        )
    }

    // Dialog data MBG satu siswa: ubah nama, pilih status, atau hapus
    daftarMbg.firstOrNull { it.id == mbgTarget }?.let { siswa ->
        MbgDialog(
            siswa = siswa,
            onSimpan = { nama, status ->
                simpanMbg(ubahMbg(daftarMbg, siswa.id, nama, status))
                mbgTarget = null
            },
            onHapus = {
                simpanMbg(hapusMbg(daftarMbg, siswa.id))
                mbgTarget = null
            },
            onDismiss = { mbgTarget = null },
        )
    }

    // Dialog tambah satu siswa MBG
    if (showMbgTambah) {
        TambahMbgDialog(
            onDismiss = { showMbgTambah = false },
            onTambah = { nama ->
                simpanMbg(tambahMbg(daftarMbg, nama))
                showMbgTambah = false
            },
        )
    }

    // Dialog isi nama cepat untuk daftar MBG
    if (showMbgBulk) {
        BulkMbgDialog(
            onDismiss = { showMbgBulk = false },
            onApply = { text, ganti ->
                simpanMbg(daftarDariTeks(text, daftarMbg, ganti))
                showMbgBulk = false
            },
        )
    }

    // Dialog konfirmasi reset daftar MBG
    if (showMbgReset) {
        AlertDialog(
            onDismissRequest = { showMbgReset = false },
            title = { Text("Hapus semua daftar MBG?") },
            text = { Text("Seluruh nama siswa dan status ambil MBG akan dihapus. Papan tempat duduk tidak ikut terhapus.") },
            confirmButton = {
                Button(onClick = {
                    simpanMbg(emptyList())
                    showMbgReset = false
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { showMbgReset = false }) { Text("Batal") }
            },
        )
    }

    // Dialog hasil acak
    acakInfo?.let { (baru, ulang) ->
        AcakInfoDialog(baru, ulang) { acakInfo = null }
    }

    // Dialog hasil roll
    if (showRollResult) {
        rollResultSeats?.let { result ->
            RollResultDialog(
                seats = result,
                period = period,
                onDismiss = { showRollResult = false },
            )
        }
    }
}

@Composable
fun StatCard(nilai: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(nilai, style = MaterialTheme.typography.headlineSmall)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SeatCell(siswa: Siswa?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(
                if (siswa != null) {
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            shape,
                        )
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
                },
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (siswa != null) {
            Text(
                siswa.nama,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                "+",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
fun AcakInfoDialog(baru: Int, ulang: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selesai Diacak") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "$baru siswa dapat kursi yang belum pernah mereka duduki.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (ulang > 0) {
                    Text(
                        "$ulang siswa terpaksa duduk di kursi yang sudah pernah " +
                            "mereka duduki, karena semua kursi sudah pernah " +
                            "mereka pakai.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
fun BulkDialog(onDismiss: () -> Unit, onApply: (String, Boolean) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var gantiSemua by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Isi Nama Cepat") },
        text = {
            Column {
                Text(
                    "Satu nama per baris, mengisi dari depan ke belakang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Andi\nBudi\nCitra") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !gantiSemua,
                        onClick = { gantiSemua = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Isi kosong saja", maxLines = 1) }
                    SegmentedButton(
                        selected = gantiSemua,
                        onClick = { gantiSemua = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Ganti semua", maxLines = 1) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(text, gantiSemua) }) { Text("Terapkan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

@Composable
fun RollResultDialog(seats: Seats, period: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hasil Periode $period") },
        text = {
            Column {
                Text(
                    "DEPAN KELAS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(18.dp))
                    COLUMN_SIZES.indices.forEach { c ->
                        Text(
                            "B${c + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                for (d in 0 until MAX_DEPTH) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${d + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(18.dp),
                        )
                        seats.forEachIndexed { c, col ->
                            Box(
                                Modifier.weight(1f).padding(horizontal = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (d < col.size) {
                                    val s = col[d]
                                    val bg = if (s != null)
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .background(bg, RoundedCornerShape(8.dp))
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            s?.nama ?: "",
                                            style = MaterialTheme.typography.labelSmall,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("OK") }
        },
    )
}

// Layar daftar MBG: ringkasan status + daftar nama siswa
@Composable
fun MbgContent(
    daftar: DaftarMbg,
    padding: PaddingValues,
    onPilih: (Long) -> Unit,
) {
    val rekap = rekapMbg(daftar)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusMbg.entries.forEach { s ->
                MbgStatCard(
                    nilai = (rekap[s] ?: 0).toString(),
                    label = s.label,
                    status = s,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            "${daftar.size} siswa terdaftar · ketuk nama untuk ubah status",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        if (daftar.isEmpty()) {
            Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Belum ada nama. Ketuk ikon + di atas untuk menambah satu nama, " +
                        "atau ikon pensil untuk tempel daftar nama sekaligus.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        } else {
            Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    daftar.forEachIndexed { i, s ->
                        if (i > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPilih(s.id) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "${i + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(26.dp),
                            )
                            Text(
                                s.nama,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            StatusChip(s.status)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// Warna status dipakai kartu ringkasan & chip di daftar
@Composable
private fun warnaStatus(status: StatusMbg): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    when (status) {
        StatusMbg.AMBIL ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        StatusMbg.BELUM ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        StatusMbg.TIDAK ->
            MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        StatusMbg.TEMAN ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

@Composable
fun MbgStatCard(nilai: String, label: String, status: StatusMbg, modifier: Modifier = Modifier) {
    val (bg, fg) = warnaStatus(status)
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(nilai, style = MaterialTheme.typography.headlineSmall, color = fg)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StatusChip(status: StatusMbg) {
    val (bg, fg) = warnaStatus(status)
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(status.label, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

// Dialog satu siswa: ganti nama, pilih status ambil MBG, atau hapus
@Composable
fun MbgDialog(
    siswa: SiswaMbg,
    onSimpan: (String, StatusMbg) -> Unit,
    onHapus: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draftNama by remember(siswa.id) { mutableStateOf(siswa.nama) }
    var status by remember(siswa.id) { mutableStateOf(siswa.status) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Data MBG") },
        text = {
            Column {
                OutlinedTextField(
                    value = draftNama,
                    onValueChange = { draftNama = it },
                    label = { Text("Nama siswa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                StatusMbg.entries.forEach { s ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { status = s }
                            .padding(vertical = 1.dp),
                    ) {
                        RadioButton(selected = status == s, onClick = { status = s })
                        Text(s.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSimpan(draftNama, status) },
                enabled = draftNama.isNotBlank(),
            ) { Text("Simpan") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onHapus) { Text("Hapus") }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        },
    )
}

@Composable
fun TambahMbgDialog(onDismiss: () -> Unit, onTambah: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Siswa") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Nama siswa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onTambah(draft) },
                enabled = draft.isNotBlank(),
            ) { Text("Tambah") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun BulkMbgDialog(onDismiss: () -> Unit, onApply: (String, Boolean) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var gantiSemua by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Isi Daftar MBG") },
        text = {
            Column {
                Text(
                    "Satu nama per baris. Nama yang sudah ada tidak didaftarkan dua kali.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Andi\nBudi\nCitra") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !gantiSemua,
                        onClick = { gantiSemua = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Tambah di akhir", maxLines = 1) }
                    SegmentedButton(
                        selected = gantiSemua,
                        onClick = { gantiSemua = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Ganti semua", maxLines = 1) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(text, gantiSemua) }) { Text("Terapkan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
