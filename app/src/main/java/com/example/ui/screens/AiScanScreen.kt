package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.api.GeminiScannerService
import com.example.data.api.ScannedMedicationResult
import com.example.ui.theme.PremiumBlueGradient
import com.example.ui.theme.PremiumTealGradient
import kotlinx.coroutines.launch

data class SampleScanPreset(
    val title: String,
    val subtitle: String,
    val iconEmoji: String,
    val hint: String
)

val SAMPLE_SCAN_PRESETS = listOf(
    SampleScanPreset("Doliprane 1g", "Boîte 8 comprimés", "💊", "Doliprane 1000 mg comprimé effervescent"),
    SampleScanPreset("Amoxicilline", "Antibiotique 500mg", "🦠", "Amoxicilline 500 mg gélules"),
    SampleScanPreset("Spasfon 80mg", "Antispasmodique", "⚡", "Spasfon 80 mg comprimé enrobé"),
    SampleScanPreset("Augmentin 1g", "Ordonnance Pédiatrique", "📄", "Augmentin 1g/125mg sachet pour suspension buvable")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScanScreen(
    onAddMedication: (name: String, dosage: String, form: String, colorHex: String, instructions: String, times: String, stock: Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var hintText by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<ScannedMedicationResult?>(null) }
    var showSuccessBanner by remember { mutableStateOf(false) }

    // Editable form state after scan
    var medName by remember { mutableStateOf("") }
    var medDosage by remember { mutableStateOf("") }
    var medForm by remember { mutableStateOf("Comprimé") }
    var medInstructions by remember { mutableStateOf("") }
    var medTimes by remember { mutableStateOf("08:00,20:00") }
    var medStock by remember { mutableStateOf("30") }
    var medColorHex by remember { mutableStateOf("#0F62FE") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                selectedBitmap = bitmap
                hintText = "Image Galerie"
                scanResult = null
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur chargement image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startScan(promptHint: String) {
        isAnalyzing = true
        scanResult = null
        showSuccessBanner = false

        coroutineScope.launch {
            val result = GeminiScannerService.analyzeMedicationImage(selectedBitmap, promptHint)
            isAnalyzing = false
            scanResult = result

            medName = result.name
            medDosage = result.dosage
            medForm = result.form
            medInstructions = result.instructions
            medTimes = result.timesOfDay
            medStock = result.stockQuantity.toString()
            medColorHex = result.colorHex
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 100.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scan IA Médical 🤖",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Gemini 3.5",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Scannez votre boîte ou ordonnance pour pré-remplir automatiquement la posologie",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedBitmap != null) {
                            Image(
                                bitmap = selectedBitmap!!.asImageBitmap(),
                                contentDescription = "Aperçu Scan",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(PremiumBlueGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Centrez la boîte de médicament dans le cadre",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "L'IA détectera le nom, le dosage et la forme",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isAnalyzing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Analyse multimodale Gemini en cours...",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Importer")
                        }

                        Button(
                            onClick = { startScan(hintText.ifBlank { "Analyse boîte médicament" }) },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isAnalyzing
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Analyser IA")
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text(
                    text = "Démonstrations Rapides :",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(SAMPLE_SCAN_PRESETS) { preset ->
                        Surface(
                            onClick = {
                                hintText = preset.hint
                                startScan(preset.hint)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(preset.iconEmoji, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(preset.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(preset.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showSuccessBanner) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(PremiumTealGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Médicament $medName ajouté avec succès à votre traitement !",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        if (scanResult != null) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Résultats Extraits par l'IA",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { scanResult = null }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Réinitialiser")
                            }
                        }

                        Text(
                            text = scanResult?.rawAiAnalysis ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = medName,
                            onValueChange = { medName = it },
                            label = { Text("Nom du médicament") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = medDosage,
                                onValueChange = { medDosage = it },
                                label = { Text("Dosage") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )

                            OutlinedTextField(
                                value = medStock,
                                onValueChange = { medStock = it },
                                label = { Text("Quantité Stock") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = medForm,
                            onValueChange = { medForm = it },
                            label = { Text("Forme galénique") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = medTimes,
                            onValueChange = { medTimes = it },
                            label = { Text("Heures de prise") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = medInstructions,
                            onValueChange = { medInstructions = it },
                            label = { Text("Consignes de prise") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (medName.isNotBlank()) {
                                    onAddMedication(
                                        medName,
                                        medDosage,
                                        medForm,
                                        medColorHex,
                                        medInstructions,
                                        medTimes,
                                        medStock.toIntOrNull() ?: 30
                                    )
                                    showSuccessBanner = true
                                    Toast.makeText(context, "$medName ajouté au traitement !", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            enabled = medName.isNotBlank()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ajouter au Traitement ➕", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

