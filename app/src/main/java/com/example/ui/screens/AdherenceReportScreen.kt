package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MedicationLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdherenceReportScreen(
    allLogs: List<MedicationLog>
) {
    val context = LocalContext.current
    val totalCount = allLogs.size
    val takenCount = allLogs.count { it.status == "TAKEN" }
    val skippedCount = allLogs.count { it.status == "SKIPPED" }
    val adherencePercent = if (totalCount > 0) (takenCount.toFloat() / totalCount * 100).toInt() else 100

    val daysLabel = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
    val weeklyRates = listOf(0.85f, 0.90f, 1.0f, 0.75f, 0.95f, 0.80f, adherencePercent / 100f)

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Observance & Rapports 📊",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Statistiques de suivi médical et exportation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        val reportText = buildString {
                            appendLine("--- Rapport d'Observance Médicale MediSnap ---")
                            appendLine("Date : ${SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date())}")
                            appendLine("Taux global d'observance : $adherencePercent %")
                            appendLine("Doses prises : $takenCount")
                            appendLine("Doses manquées/ignorées : $skippedCount")
                            appendLine("\nHistorique récent :")
                            allLogs.take(10).forEach { log ->
                                val statusStr = if (log.status == "TAKEN") "PRIS ✅" else "IGNORÉ ⏭️"
                                appendLine("- ${log.medicationName} (${log.dosage}) - ${log.timeSlot} : $statusStr")
                            }
                        }

                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, reportText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Partager le rapport médical"))
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Partager le rapport")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Observance", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "$adherencePercent %",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Doses Prises", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "$takenCount",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Évolution hebdomadaire",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val barWidth = 24.dp.toPx()
                            val totalWidth = size.width
                            val spacing = (totalWidth - (barWidth * 7)) / 8
                            val maxHeight = size.height - 30.dp.toPx()

                            weeklyRates.forEachIndexed { i, rate ->
                                val x = spacing + i * (barWidth + spacing)
                                val barHeight = maxHeight * rate.coerceIn(0.1f, 1f)
                                val topY = maxHeight - barHeight

                                drawRoundRect(
                                    color = trackColor,
                                    topLeft = Offset(x, 0f),
                                    size = Size(barWidth, maxHeight),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )

                                drawRoundRect(
                                    color = primaryColor,
                                    topLeft = Offset(x, topY),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        daysLabel.forEach { day ->
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Historique des prises",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (allLogs.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("Aucune donnée d'historique disponible.")
                    }
                }
            }
        } else {
            items(allLogs.take(30)) { log ->
                val dateStr = SimpleDateFormat("dd MMM • HH:mm", Locale.FRANCE).format(Date(log.scheduledTimeMs))
                ElevatedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${log.medicationName} (${log.dosage})",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Prévu le : $dateStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    when (log.status) {
                                        "TAKEN" -> "Pris ✅"
                                        "SKIPPED" -> "Ignoré ⏭️"
                                        else -> "En attente ⏳"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
