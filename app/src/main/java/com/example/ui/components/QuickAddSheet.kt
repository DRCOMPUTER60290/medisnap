package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

data class PresetMed(val name: String, val dosage: String, val form: String, val colorHex: String)

val MED_PRESETS = listOf(
    PresetMed("Doliprane", "1000 mg", "Comprimé", "#0066FF"),
    PresetMed("Paracétamol", "500 mg", "Comprimé", "#0284C7"),
    PresetMed("Ibuprofène", "400 mg", "Comprimé", "#EF4444"),
    PresetMed("Amoxicilline", "500 mg", "Gélule", "#10B981"),
    PresetMed("Spasfon", "80 mg", "Comprimé", "#F59E0B"),
    PresetMed("Vitamine C", "1000 mg", "Sachet", "#8B5CF6")
)

val COLOR_OPTIONS = listOf("#0066FF", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    onDismiss: () -> Unit,
    onAdd: (name: String, dosage: String, form: String, colorHex: String, instructions: String, times: String, stock: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("1000 mg") }
    var selectedForm by remember { mutableStateOf("Comprimé") }
    var selectedColor by remember { mutableStateOf("#0066FF") }
    var instructions by remember { mutableStateOf("Pendant le repas") }
    var selectedTimes by remember { mutableStateOf(listOf("08:00", "20:00")) }
    var stockQuantity by remember { mutableStateOf("30") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saisie Rapide de Médicament ⚡",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Saisie express par suggestion :", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MED_PRESETS) { preset ->
                    SuggestionChip(
                        onClick = {
                            name = preset.name
                            dosage = preset.dosage
                            selectedForm = preset.form
                            selectedColor = preset.colorHex
                        },
                        label = { Text("${preset.name} ${preset.dosage}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom du médicament") },
                placeholder = { Text("Ex: Doliprane") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosage") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = stockQuantity,
                    onValueChange = { stockQuantity = it },
                    label = { Text("Stock initial") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Forme & Couleur :", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Comprimé", "Gélule", "Sirop", "Sachet").forEach { form ->
                    FilterChip(
                        selected = selectedForm == form,
                        onClick = { selectedForm = form },
                        label = { Text(form) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                COLOR_OPTIONS.forEach { hex ->
                    val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Blue }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { selectedColor = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedColor == hex) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Heures de prise :", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val timesOptions = listOf("08:00", "12:00", "13:00", "19:00", "20:00")
                timesOptions.forEach { time ->
                    val isSelected = selectedTimes.contains(time)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTimes = if (isSelected) {
                                if (selectedTimes.size > 1) selectedTimes - time else selectedTimes
                            } else {
                                (selectedTimes + time).sorted()
                            }
                        },
                        label = { Text(time) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                label = { Text("Consignes de prise") },
                placeholder = { Text("Ex: Pendant le repas avec de l'eau") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(
                            name,
                            dosage,
                            selectedForm,
                            selectedColor,
                            instructions,
                            selectedTimes.joinToString(","),
                            stockQuantity.toIntOrNull() ?: 30
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enregistrer le médicament")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
