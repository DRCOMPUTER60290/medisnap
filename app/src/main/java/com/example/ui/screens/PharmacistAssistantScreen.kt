package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.GeminiScannerService
import com.example.data.model.Medication
import com.example.ui.theme.PremiumBlueGradient
import com.example.ui.theme.PremiumTealGradient
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val bitmap: Bitmap? = null,
    val timestamp: String = "19:52",
    val suggestedResult: com.example.data.api.ScannedMedicationResult? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmacistAssistantScreen(
    medications: List<Medication>,
    onAddMedication: (name: String, dosage: String, form: String, colorHex: String, instructions: String, times: String, stock: Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    isUser = false,
                    text = "Bonjour ! 👋\n\nJe suis votre assistant pharmacien IA.\n\nPhotographiez une boîte de médicament à la pharmacie et je vous dirai instantanément si vous l'avez déjà en stock à la maison !\n\nUtilisez 📷 pour scanner ou 🖼️ pour choisir une photo."
                )
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isThinking by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                selectedBitmap = BitmapFactory.decodeStream(inputStream)
                Toast.makeText(context, "Photo importée avec succès", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur lors de l'ouverture de l'image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleSend(textToSend: String, bitmapToSend: Bitmap?) {
        if (textToSend.isBlank() && bitmapToSend == null) return

        val userMsg = ChatMessage(
            isUser = true,
            text = textToSend.ifBlank { "Analyse de cette boîte de médicament" },
            bitmap = bitmapToSend
        )

        messages = messages + userMsg
        inputText = ""
        selectedBitmap = null
        isThinking = true

        coroutineScope.launch {
            listState.animateScrollToItem(messages.size - 1)

            // Analyze with Gemini & cross reference with user's home stock
            val aiResult = GeminiScannerService.analyzeMedicationImage(
                bitmap = bitmapToSend,
                promptHint = textToSend
            )

            // Check if user has this medication in stock (only when a valid medication name was extracted)
            val extractedName = aiResult.name.trim()
            val matchingMed = if (extractedName.isNotBlank()) {
                medications.find { med ->
                    med.name.lowercase().contains(extractedName.lowercase()) ||
                        extractedName.lowercase().contains(med.name.lowercase())
                }
            } else {
                null
            }

            val replyText = when {
                extractedName.isBlank() -> {
                    """
                    ℹ️ **ANALYSE AUTOMATIQUE INDISPONIBLE**

                    ${aiResult.rawAiAnalysis}

                    💡 Astuce : saisissez le nom du médicament (ex. Doliprane, Spasfon, Amoxicilline) ou réessayez avec une photo plus nette.
                    """.trimIndent()
                }
                matchingMed != null -> {
                    """
                    ✅ **DÉJÀ EN STOCK À LA MAISON !**

                    📌 **Médicament** : ${matchingMed.name} (${matchingMed.dosage})
                    📦 **Stock chez vous** : ${matchingMed.stockQuantity} ${matchingMed.form.lowercase()}s restant(s)
                    💡 **Consignes de prise** : ${matchingMed.instructions}

                    💬 *Conseil du Pharmacien* : Vous disposez déjà de ${matchingMed.stockQuantity} unités. Il n'est pas nécessaire de racheter cette boîte sauf renouvellement spécifique de votre ordonnance !
                    """.trimIndent()
                }
                else -> {
                    """
                    ⚠️ **NON DÉTECTÉ DANS VOTRE STOCK**

                    📌 **Médicament identifié** : ${aiResult.name} (${aiResult.dosage})
                    💊 **Forme** : ${aiResult.form}
                    📋 **Posologie habituelle** : ${aiResult.instructions}

                    ➕ Vous pouvez l'ajouter directement à votre armoire à pharmacie ci-dessous.
                    """.trimIndent()
                }
            }

            messages = messages + ChatMessage(
                isUser = false,
                text = replyText,
                suggestedResult = if (matchingMed == null && extractedName.isNotBlank()) aiResult else null
            )

            isThinking = false
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Luxury Modern Header
        Surface(
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(PremiumBlueGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MedicalServices,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Assistant Pharmacien",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Scannez pour vérifier votre stock",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

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
                                text = "Gemini AI",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Quick Preset Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SuggestionChip(
                    onClick = { handleSend("Vérifier si j'ai du Doliprane en stock", null) },
                    label = { Text("💊 Doliprane 1000mg") },
                    shape = RoundedCornerShape(20.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                )
            }
            item {
                SuggestionChip(
                    onClick = { handleSend("Est-ce que j'ai du Spasfon ?", null) },
                    label = { Text("⚡ Spasfon 80mg") },
                    shape = RoundedCornerShape(20.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                )
            }
            item {
                SuggestionChip(
                    onClick = { handleSend("Vérifier mon stock d'Amoxicilline", null) },
                    label = { Text("🦠 Amoxicilline") },
                    shape = RoundedCornerShape(20.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                )
            }
            item {
                SuggestionChip(
                    onClick = { handleSend("Conseil de posologie pour Ibuprofène", null) },
                    label = { Text("❓ Posologie Ibuprofène") },
                    shape = RoundedCornerShape(20.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                )
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubbleItem(msg = msg, onAddSuggested = {
                    val res = msg.suggestedResult
                    if (res != null) {
                        onAddMedication(
                            res.name,
                            res.dosage,
                            res.form,
                            res.colorHex,
                            res.instructions,
                            res.timesOfDay,
                            res.stockQuantity
                        )
                        Toast.makeText(context, "${res.name} ajouté au traitement !", Toast.LENGTH_SHORT).show()
                    }
                })
            }

            if (isThinking) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "L'Assistant Pharmacien analyse votre armoire à pharmacie...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Image Preview Box before sending
        if (selectedBitmap != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Aperçu de l'image",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Photo prêtre à être analysée",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Cliquez sur envoyer pour démarrer l'IA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { selectedBitmap = null }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retirer photo")
                    }
                }
            }
        }

        // Ultra-Modern Bottom Floating Input Bar
        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Prendre/Choisir photo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Posez une question ou scannez...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(PremiumBlueGradient)
                        .clickable { handleSend(inputText, selectedBitmap) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Envoyer",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(msg: ChatMessage, onAddSuggested: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (msg.isUser) {
            // User Chat Bubble (Vibrant Gradient)
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(PremiumBlueGradient)
                    .padding(14.dp)
            ) {
                Column {
                    if (msg.bitmap != null) {
                        Image(
                            bitmap = msg.bitmap.asImageBitmap(),
                            contentDescription = "Photo transmise",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Assistant Chat Bubble (Clean Luxury Surface)
            Card(
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 20.dp
                ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .widthIn(max = 330.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 20.dp
                    ))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(PremiumTealGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MedicalServices,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Assistant Pharmacien",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )

                    if (msg.text.contains("NON DÉTECTÉ")) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onAddSuggested,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ajouter à mon armoire ➕")
                        }
                    }
                }
            }
        }
    }
}

