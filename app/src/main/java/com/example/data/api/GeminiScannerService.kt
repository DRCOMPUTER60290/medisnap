package com.example.data.api

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "GeminiScannerService"

enum class GeminiModelOption(val modelId: String, val displayLabel: String) {
    FAST_LITE("gemini-3.5-flash-lite", "3.5 Flash-Lite (Rapide)"),
    STANDARD("gemini-3.8-flash", "3.8 Flash (Précis)")
}

data class ScannedMedicationResult(
    val name: String,
    val dosage: String,
    val form: String,
    val instructions: String,
    val timesOfDay: String,
    val stockQuantity: Int,
    val colorHex: String,
    val rawAiAnalysis: String,
    val activeIngredient: String = "",
    val isAiVerified: Boolean = false,
    val isDemoMode: Boolean = false,
    val errorMessage: String? = null
)

object GeminiScannerService {

    private val medicationSchema = Schema.obj(
        properties = mapOf(
            "name" to Schema.string(description = "Nom commercial exact du médicament, ex: Doliprane, Augmentin, Spasfon"),
            "activeIngredient" to Schema.string(description = "DCI ou principe actif en français, ex: Paracétamol"),
            "dosage" to Schema.string(description = "Dosage visible sur la boîte ou l'ordonnance, ex: 1000 mg"),
            "form" to Schema.string(description = "Forme galénique, ex: Comprimé, Gélule, Sachet, Sirop"),
            "instructions" to Schema.string(description = "Consignes de prise claires en français"),
            "timesOfDay" to Schema.string(description = "Horaires de prise usuels séparés par des virgules, ex: 08:00,20:00"),
            "stockQuantity" to Schema.integer(description = "Nombre d'unités dans la boîte si visible, sinon 20"),
            "colorHex" to Schema.string(description = "Code couleur hex parmi #0F62FE, #10B981, #EF4444, #F59E0B, #8B5CF6")
        )
    )

    private val fastLiteModel: GenerativeModel by lazy {
        buildModel(GeminiModelOption.FAST_LITE.modelId)
    }

    private val standardModel: GenerativeModel by lazy {
        buildModel(GeminiModelOption.STANDARD.modelId)
    }

    private fun buildModel(modelName: String): GenerativeModel {
        return Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = medicationSchema
                temperature = 0.2f
            },
            systemInstruction = content {
                text(
                    """
                    Tu es un pharmacien clinicien expert en France.
                    Analyse l'image d'une boîte de médicament, d'un flacon ou d'une ordonnance (et/ou le texte fourni).
                    Extrais avec précision les informations médicales en français selon le schéma JSON demandé.
                    Sois concis et direct.
                    """.trimIndent()
                )
            }
        )
    }

    private fun modelFor(option: GeminiModelOption): GenerativeModel = when (option) {
        GeminiModelOption.FAST_LITE -> fastLiteModel
        GeminiModelOption.STANDARD -> standardModel
    }

    private fun Bitmap.scaleForInference(maxDim: Float = 768f): Bitmap {
        val largest = max(width, height).coerceAtLeast(1)
        if (largest <= maxDim) return this
        val scale = maxDim / largest
        val targetW = (width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetW, targetH, true)
    }

    suspend fun analyzeMedicationImage(
        bitmap: Bitmap?,
        promptHint: String = "",
        allowDemoPreset: Boolean = false,
        modelOption: GeminiModelOption = GeminiModelOption.FAST_LITE
    ): ScannedMedicationResult = withContext(Dispatchers.IO) {
        if (allowDemoPreset && bitmap == null) {
            demoPresetResult(promptHint)?.let { return@withContext it }
        }

        val scaledBitmap = bitmap?.scaleForInference()
        val promptText = if (scaledBitmap != null) {
            "Identifie le médicament sur cette photo et remplis le JSON en français. $promptHint".trim()
        } else {
            "Identifie ce médicament et remplis le JSON en français : ${promptHint.ifBlank { "Médicament courant" }}".trim()
        }

        val inputContent = content {
            text(promptText)
            if (scaledBitmap != null) {
                image(scaledBitmap)
            }
        }

        // Try selected model first (streaming reduces latency); if STANDARD times out, retry once on FAST_LITE
        val candidates = if (modelOption == GeminiModelOption.STANDARD) {
            listOf(GeminiModelOption.STANDARD, GeminiModelOption.FAST_LITE)
        } else {
            listOf(GeminiModelOption.FAST_LITE)
        }

        var lastError: String? = null

        for (candidate in candidates) {
            try {
                Log.d(TAG, "Starting Firebase AI stream (${candidate.modelId}), hasImage=${scaledBitmap != null}")
                val accumulated = StringBuilder()

                withTimeout(90_000L) {
                    modelFor(candidate).generateContentStream(inputContent).collect { chunk ->
                        val part = chunk.text.orEmpty()
                        if (part.isNotEmpty()) {
                            accumulated.append(part)
                        }
                    }
                }

                val jsonString = accumulated.toString().trim()
                if (jsonString.isBlank()) {
                    throw IllegalStateException("Le modèle ${candidate.modelId} n'a renvoyé aucune réponse.")
                }

                val medJson = JSONObject(jsonString)
                val parsedName = medJson.optString("name", "").trim()
                if (parsedName.isBlank()) {
                    throw IllegalStateException("Aucun nom de médicament n'a pu être extrait.")
                }

                return@withContext ScannedMedicationResult(
                    name = parsedName,
                    dosage = medJson.optString("dosage", "Non précisé").trim(),
                    form = medJson.optString("form", "Comprimé").trim(),
                    instructions = medJson.optString(
                        "instructions",
                        "Prendre selon la prescription médicale"
                    ).trim(),
                    timesOfDay = medJson.optString("timesOfDay", "08:00,20:00").trim(),
                    stockQuantity = medJson.optInt("stockQuantity", 20).coerceAtLeast(1),
                    colorHex = medJson.optString("colorHex", "#0F62FE").trim(),
                    rawAiAnalysis = "Analyse réussie avec ${candidate.displayLabel}",
                    activeIngredient = medJson.optString("activeIngredient", "").trim(),
                    isAiVerified = true,
                    isDemoMode = false,
                    errorMessage = null
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Firebase AI generation timed out on ${candidate.modelId}", e)
                lastError = "Délai d'attente dépassé (90 s) sur ${candidate.modelId}. Vérifiez la connexion réseau de l'émulateur."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI generation failed on ${candidate.modelId}", e)
                val detail = e.localizedMessage ?: "Erreur inconnue"
                lastError = "Erreur Firebase AI (${candidate.modelId}) : $detail"
            }
        }

        errorResult(lastError ?: "Erreur d'analyse Firebase AI.")
    }

    private fun errorResult(diagnostic: String): ScannedMedicationResult =
        ScannedMedicationResult(
            name = "",
            dosage = "",
            form = "Comprimé",
            instructions = "",
            timesOfDay = "08:00,20:00",
            stockQuantity = 30,
            colorHex = "#0F62FE",
            rawAiAnalysis = diagnostic,
            activeIngredient = "",
            isAiVerified = false,
            isDemoMode = false,
            errorMessage = diagnostic
        )

    private fun demoPresetResult(promptHint: String): ScannedMedicationResult? {
        val lower = promptHint.lowercase()
        return when {
            lower.contains("doliprane") || lower.contains("paracetamol") -> ScannedMedicationResult(
                name = "Doliprane",
                dosage = "1000 mg",
                form = "Comprimé",
                instructions = "1 comprimé toutes les 6 h en cas de douleur ou fièvre (max 3 g/jour sans avis médical)",
                timesOfDay = "08:00,14:00,20:00",
                stockQuantity = 8,
                colorHex = "#0066FF",
                rawAiAnalysis = "Fiche de démonstration — Doliprane 1000 mg",
                activeIngredient = "Paracétamol",
                isAiVerified = false,
                isDemoMode = true
            )
            lower.contains("amoxicilline") || lower.contains("antibiotique") -> ScannedMedicationResult(
                name = "Amoxicilline",
                dosage = "500 mg",
                form = "Gélule",
                instructions = "1 gélule au début des repas jusqu'à la fin de la durée prescrite",
                timesOfDay = "08:00,13:00,20:00",
                stockQuantity = 12,
                colorHex = "#10B981",
                rawAiAnalysis = "Fiche de démonstration — Amoxicilline 500 mg",
                activeIngredient = "Amoxicilline",
                isAiVerified = false,
                isDemoMode = true
            )
            lower.contains("spasfon") -> ScannedMedicationResult(
                name = "Spasfon",
                dosage = "80 mg",
                form = "Comprimé",
                instructions = "2 comprimés au moment des spasmes douloureux",
                timesOfDay = "08:00,13:00,20:00",
                stockQuantity = 30,
                colorHex = "#F59E0B",
                rawAiAnalysis = "Fiche de démonstration — Spasfon 80 mg",
                activeIngredient = "Phloroglucinol",
                isAiVerified = false,
                isDemoMode = true
            )
            lower.contains("augmentin") -> ScannedMedicationResult(
                name = "Augmentin",
                dosage = "1 g / 125 mg",
                form = "Sachet",
                instructions = "Dissoudre dans un verre d'eau au début du repas",
                timesOfDay = "08:00,20:00",
                stockQuantity = 16,
                colorHex = "#8B5CF6",
                rawAiAnalysis = "Fiche de démonstration — Augmentin 1 g / 125 mg",
                activeIngredient = "Amoxicilline / Acide clavulanique",
                isAiVerified = false,
                isDemoMode = true
            )
            else -> null
        }
    }
}
