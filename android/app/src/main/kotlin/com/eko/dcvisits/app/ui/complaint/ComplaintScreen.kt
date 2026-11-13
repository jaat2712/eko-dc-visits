package com.eko.dcvisits.app.ui.complaint

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eko.dcvisits.app.R
import com.eko.dcvisits.app.data.db.CspCacheEntity
import com.eko.dcvisits.app.data.sync.Fix
import com.eko.dcvisits.app.di.ServiceLocator
import com.eko.dcvisits.app.ui.components.GhostGlassButton
import com.eko.dcvisits.app.ui.components.GlassCard
import com.eko.dcvisits.app.ui.components.GlassTextField
import com.eko.dcvisits.app.ui.components.GlowButton
import com.eko.dcvisits.app.ui.components.ScreenContainer
import com.eko.dcvisits.app.ui.location.LocationProvider
import com.eko.dcvisits.app.ui.theme.EkoBlue
import com.eko.dcvisits.core.geo.haversineMeters
import kotlinx.coroutines.launch

/** Wire values match contracts/c1-entities/csp-complaint.schema.json `category`. */
private enum class Category(val wire: String, @StringRes val label: Int) {
    DEVICE("DEVICE", R.string.complaint_type_device),
    PAYMENT("PAYMENT", R.string.complaint_type_payment),
    COMMISSION("COMMISSION", R.string.complaint_type_commission),
    SUPPORT("SUPPORT", R.string.complaint_type_support),
    TRAINING("TRAINING", R.string.complaint_type_training),
    OTHER("OTHER", R.string.complaint_type_other),
}

/**
 * DC tab: write down what the CSP complained about. Pick the CSP (nearest
 * first when GPS is on), pick a type, write it, send. Queued in the outbox
 * like a visit, so it works with no network; the Eko team reads it on the
 * dashboard Complaints tab.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComplaintScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val csps by ServiceLocator.cspRepository.cspFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var fix by remember { mutableStateOf<Fix?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ServiceLocator.cspRepository.refresh()
        fix = runCatching { LocationProvider.currentFix(context) }.getOrNull()
    }

    val selected = csps.firstOrNull { it.cspLocationId == selectedId }
    val sorted = remember(csps, fix, query) {
        val q = query.trim().lowercase()
        csps
            .filter { q.isEmpty() || it.code.lowercase().contains(q) || it.name.lowercase().contains(q) }
            .sortedBy { c -> fix?.let { f -> distanceM(f, c) } ?: 0.0 }
    }

    ScreenContainer { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column {
                    Text(stringResource(R.string.complaint_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text(stringResource(R.string.complaint_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (sent) {
                item {
                    GlassCard(Modifier.fillMaxWidth(), tint = EkoBlue) {
                        Text("✅ " + stringResource(R.string.complaint_sent), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // 1. CSP
            item { SectionTitle(R.string.complaint_pick_csp) }
            if (selected != null) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            CspLine(selected, fix, Modifier.weight(1f))
                            GhostGlassButton(stringResource(R.string.complaint_change), onClick = { selectedId = null })
                        }
                    }
                }
            } else if (csps.isEmpty()) {
                item { Text(stringResource(R.string.complaint_no_csps), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item { GlassTextField(query, { query = it }, stringResource(R.string.complaint_search), Modifier.fillMaxWidth()) }
                items(sorted.take(30), key = { it.cspLocationId }) { csp ->
                    GlassCard(Modifier.fillMaxWidth(), contentPadding = 12.dp, onClick = { selectedId = csp.cspLocationId; sent = false }) {
                        CspLine(csp, fix)
                    }
                }
            }

            // 2. Type + 3. Text, once a CSP is chosen
            if (selected != null) {
                item { SectionTitle(R.string.complaint_pick_type) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Category.entries.forEach { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(stringResource(c.label)) })
                        }
                    }
                }
                item { SectionTitle(R.string.complaint_write) }
                item {
                    GlassTextField(
                        value = text,
                        onValueChange = { if (it.length <= 1000) text = it },
                        label = stringResource(R.string.complaint_text_label),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        singleLine = false,
                    )
                }
                item {
                    GlowButton(
                        text = stringResource(R.string.complaint_send),
                        enabled = category != null && text.isNotBlank() && !sending,
                        loading = sending,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val csp = selected
                            val cat = category ?: return@GlowButton
                            sending = true
                            scope.launch {
                                runCatching { ServiceLocator.visitRepository.submitComplaint(csp, cat.wire, text, fix) }
                                    .onSuccess { selectedId = null; category = null; text = ""; query = ""; sent = true }
                                sending = false
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(@StringRes res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun CspLine(csp: CspCacheEntity, fix: Fix?, modifier: Modifier = Modifier) {
    Column(modifier) {
        val title = if (csp.name.isNotBlank() && csp.name != csp.code) "${csp.code} · ${csp.name}" else csp.code
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        val dist = fix?.let { distanceM(it, csp) }?.takeIf { csp.lat != 0.0 || csp.lng != 0.0 }
        val sub = listOfNotNull(dist?.let { if (it < 1000) "${it.toInt()} m" else "%.1f km".format(it / 1000) }, csp.address.takeIf { it.isNotBlank() })
        if (sub.isNotEmpty()) {
            Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** CSPs with no pin (0,0) sort last instead of "nearest". */
private fun distanceM(f: Fix, c: CspCacheEntity): Double =
    if (c.lat == 0.0 && c.lng == 0.0) Double.MAX_VALUE else haversineMeters(f.lat, f.lng, c.lat, c.lng)
