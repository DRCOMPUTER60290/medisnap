package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.api.GeminiModelOption
import com.example.data.api.GeminiScannerService
import com.example.data.api.ScannedMedicationResult
import com.example.ui.theme.PremiumBlueGradient
import com.example.ui.theme.PremiumTealGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

private fun createScanPhotoUri(context: Context): Uri {
    val scanDir = File(context.cacheDir, "scans").apply { mkdirs() }
    val photoFile = File(scanDir, "med_scan_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}

private fun decodeSampledBitmap(context: Context, uri: Uri, maxDim: Int = 1280): Bitmap? {
    val resolver = context.contentResolver
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, boundsOptions)
    }

    val width = boundsOptions.outWidth
    val height = boundsOptions.outHeight
    var sampleSize = 1
    if (width > 0 && height > 0 && (width > maxDim || height > maxDim)) {
        val halfW = width / 2
        val halfH = height / 2
        while (halfW / sampleSize >= maxDim && halfH / sampleSize >= maxDim) {
            sampleSize *= 2
        }
    }

    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOptions)
    } ?: return null

    val rotationDegrees = try {
        resolver.openInputStream(uri)?.use { exifStream ->
            val exif = ExifInterface(exifStream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    } catch (_: Exception) {
        0f
    }

    if (rotationDegrees == 0f) return rawBitmap
    val matrix = Matrix().apply { postRotate(rotationDegrees) }
    return Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScanScreen(
    onAddMedication: (name: String, dosage: String, form: String, colorHex: String, instructions: String, times: String, stock: Int) -> Unit,
    onExternalPickerLaunched: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingPhotoUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    var lastCapturedUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    var hintText by rememberSaveable { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<ScannedMedicationResult?>(null) }
    var showSuccessBanner by rememberSaveable { mutableStateOf(false) }
    var selectedModel by rememberSaveable { mutableStateOf(GeminiModelOption.FAST_LITE) }

    // Editable form state after scan
    var medName by rememberSaveable { mutableStateOf("") }
    var medActiveIngredient by rememberSaveable { mutableStateOf("") }
    var medDosage by rememberSaveable { mutableStateOf("") }
    var medForm by rememberSaveable { mutableStateOf("Comprimé") }
    var medInstructions by rememberSaveable { mutableStateOf("") }
    var medTimes by rememberSaveable { mutableStateOf("08:00,20:00") }
    var medStock by rememberSaveable { mutableStateOf("30") }
    var medColorHex by rememberSaveable { mutableStateOf("#0F62FE") }

    // Reload captured bitmap if activity recreated
    LaunchedEffect(lastCapturedUriStr) {
        val uriStr = lastCapturedUriStr
        if (selectedBitmap == null && uriStr != null) {
            val restored = withContext(Dispatchers.IO) {
                runCatching { decodeSampledBitmap(context, Uri.parse(uriStr)) }.getOrNull()
            }
            if (restored != null) {
                selectedBitmap = restored
            }
        }
    }

    // Automatically bring the medication details card directly into view when analysis runs or completes
    LaunchedEffect(isAnalyzing, scanResult) {
        if (isAnalyzing || scanResult != null) {
            listState.animateScrollToItem(index = 1, scrollOffset = 340)
        }
    }

    fun startScan(
        bitmapOverride: Bitmap? = selectedBitmap,
        promptHint: String,
        allowDemoPreset: Boolean = false
    ) {
        isAnalyzing = true
        scanResult = null
        showSuccessBanner = false

        coroutineScope.launch {
            val result = GeminiScannerService.analyzeMedicationImage(
                bitmap = bitmapOverride,
                promptHint = promptHint,
                allowDemoPreset = allowDemoPreset,
                modelOption = selectedModel
            )
            isAnalyzing = false
            scanResult = result

            medName = result.name
            medActiveIngredient = result.activeIngredient
            medDosage = result.dosage
            medForm = result.form
            medInstructions = result.instructions
            medTimes = result.timesOfDay
            medStock = result.stockQuantity.toString()
            medColorHex = result.colorHex
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uriStr = pendingPhotoUriStr
        pendingPhotoUriStr = null
        if (success && uriStr != null) {
            lastCapturedUriStr = uriStr
            coroutineScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeSampledBitmap(context, Uri.parse(uriStr))
                }
                if (bitmap != null) {
                    selectedBitmap = bitmap
                    val captureHint = "Photo boîte ou ordonnance de médicament"
                    hintText = captureHint
                    startScan(bitmapOverride = bitmap, promptHint = captureHint)
                } else {
                    Toast.makeText(context, "Impossible de charger la photo prise.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun launchSystemCamera() {
        try {
            val photoUri = createScanPhotoUri(context)
            pendingPhotoUriStr = photoUri.toString()
            onExternalPickerLaunched()
            takePictureLauncher.launch(photoUri)
        } catch (e: Exception) {
            pendingPhotoUriStr = null
            Toast.makeText(
                context,
                "Appareil photo indisponible sur cet appareil : ${e.localizedMessage}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchSystemCamera()
        } else {
            Toast.makeText(
                context,
                "Autorisez l’accès à la caméra pour photographier un médicament.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun onCameraClick() {
        val permissionStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            launchSystemCamera()
        } else {
            onExternalPickerLaunched()
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            lastCapturedUriStr = uri.toString()
            coroutineScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeSampledBitmap(context, uri)
                }
                if (bitmap != null) {
                    selectedBitmap = bitmap
                    val galleryHint = "Photo importée d'une boîte de médicament ou ordonnance"
                    hintText = galleryHint
                    startScan(bitmapOverride = bitmap, promptHint = galleryHint)
                } else {
                    Toast.makeText(context, "Erreur chargement image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 100.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scan IA Médical",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            RoundedCornerShape(20.dp)
                        )
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
                                text = "Gemini Vision",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Prenez une photo de votre boîte ou ordonnance : les renseignements du médicament s’afficheront juste en dessous.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "scan_card") {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = !isAnalyzing) { onCameraClick() }
                            .testTag("scan_camera_preview_box"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedBitmap != null) {
                            Image(
                                bitmap = selectedBitmap!!.asImageBitmap(),
                                contentDescription = "Photo du médicament capturée",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(PremiumBlueGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "Prendre une photo",
                                        modifier = Modifier.size(30.dp),
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Appuyez pour photographier la boîte",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "L’IA détectera le nom, le dosage et la forme",
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
                                    Spacer(modifier = Modifier.height(12.dp))
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

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onCameraClick() },
                            modifier = Modifier
                                .weight(1.1f)
                                .height(48.dp)
                                .testTag("scan_take_photo_button"),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isAnalyzing
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Prendre photo")
                        }

                        OutlinedButton(
                            onClick = {
                                onExternalPickerLaunched()
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(0.9f)
                                .height(48.dp)
                                .testTag("scan_import_image_button"),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isAnalyzing
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Galerie")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GeminiModelOption.entries.forEach { option ->
                            FilterChip(
                                selected = selectedModel == option,
                                onClick = { if (!isAnalyzing) selectedModel = option },
                                label = {
                                    Text(
                                        text = option.displayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scan_model_${option.name.lowercase()}"),
                                enabled = !isAnalyzing
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FilledTonalButton(
                        onClick = {
                            startScan(
                                bitmapOverride = selectedBitmap,
                                promptHint = hintText.ifBlank { "Analyse boîte médicament" }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("scan_analyze_button"),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isAnalyzing
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedBitmap != null) {
                                "Relancer l’analyse IA sur cette photo"
                            } else {
                                "Analyser IA"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Immediate loading feedback directly below the photo card
        if (isAnalyzing) {
            item(key = "analyzing_card") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_analyzing_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Extraction des renseignements du médicament...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        // Medication information card placed IMMEDIATELY below the photo/scan card
        if (scanResult != null) {
            item(key = "result_card") {
                val currentResult = scanResult!!
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.5.dp,
                            if (currentResult.errorMessage != null) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            },
                            RoundedCornerShape(24.dp)
                        )
                        .testTag("scan_results_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (selectedBitmap != null) {
                                    Image(
                                        bitmap = selectedBitmap!!.asImageBitmap(),
                                        contentDescription = "Miniature du médicament analysé",
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline,
                                                RoundedCornerShape(10.dp)
                                            ),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                } else {
                                    Icon(
                                        Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column {
                                    Text(
                                        text = "Renseignements du médicament",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when {
                                            currentResult.isAiVerified -> "Identifié par Gemini Vision"
                                            currentResult.isDemoMode -> "Fiche de démonstration"
                                            else -> "Saisie manuelle ou vérification requise"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { scanResult = null },
                                modifier = Modifier.testTag("scan_reset_result_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Réinitialiser")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Analysis status banner (API verified, demo, or error/missing key)
                        val bannerContainerColor = when {
                            currentResult.errorMessage != null -> MaterialTheme.colorScheme.errorContainer
                            currentResult.isAiVerified -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                        val bannerContentColor = when {
                            currentResult.errorMessage != null -> MaterialTheme.colorScheme.onErrorContainer
                            currentResult.isAiVerified -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = bannerContainerColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_status_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = when {
                                        currentResult.errorMessage != null -> Icons.Default.Warning
                                        currentResult.isAiVerified -> Icons.Default.CheckCircle
                                        else -> Icons.Default.Info
                                    },
                                    contentDescription = null,
                                    tint = bannerContentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentResult.rawAiAnalysis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = bannerContentColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Highlighted Medication Summary Sheet when a medication was extracted
                        if (medName.isNotBlank() || medDosage.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scan_medication_summary_box")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Medication,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = listOf(medName, medDosage)
                                                .filter { it.isNotBlank() }
                                                .joinToString(" • "),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (medActiveIngredient.isNotBlank()) {
                                        Text(
                                            text = "Principe actif : $medActiveIngredient",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Forme : ${medForm.ifBlank { "Comprimé" }} • Stock estimé : ${medStock.ifBlank { "30" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (medTimes.isNotBlank()) {
                                        Text(
                                            text = "Horaires conseillés : $medTimes",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (medInstructions.isNotBlank()) {
                                        Text(
                                            text = "Posologie : $medInstructions",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Vérifier ou modifier avant ajout :",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = medName,
                            onValueChange = { medName = it },
                            label = { Text("Nom du médicament") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_med_name_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = medDosage,
                                onValueChange = { medDosage = it },
                                label = { Text("Dosage") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scan_med_dosage_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )

                            OutlinedTextField(
                                value = medStock,
                                onValueChange = { medStock = it },
                                label = { Text("Quantité Stock") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scan_med_stock_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = medForm,
                            onValueChange = { medForm = it },
                            label = { Text("Forme galénique") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_med_form_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = medTimes,
                            onValueChange = { medTimes = it },
                            label = { Text("Heures de prise") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_med_times_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = medInstructions,
                            onValueChange = { medInstructions = it },
                            label = { Text("Consignes de prise") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_med_instructions_input"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (showSuccessBanner) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(PremiumTealGradient),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Médicament $medName ajouté avec succès à votre traitement !",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (medName.isNotBlank()) {
                                    onAddMedication(
                                        medName.trim(),
                                        medDosage.trim(),
                                        medForm.trim().ifBlank { "Comprimé" },
                                        medColorHex,
                                        medInstructions.trim(),
                                        medTimes.trim().ifBlank { "08:00" },
                                        medStock.toIntOrNull() ?: 30
                                    )
                                    showSuccessBanner = true
                                    Toast.makeText(
                                        context,
                                        "$medName ajouté au traitement !",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("scan_save_medication_button"),
                            shape = RoundedCornerShape(16.dp),
                            enabled = medName.isNotBlank()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ajouter au Traitement", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Quick demo presets placed below the extracted medication details
        item(key = "demos") {
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
                                startScan(
                                    bitmapOverride = null,
                                    promptHint = preset.hint,
                                    allowDemoPreset = true
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(16.dp)
                                )
                                .testTag("scan_demo_preset_${preset.title.lowercase().replace(" ", "_")}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(preset.iconEmoji, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        preset.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        preset.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
