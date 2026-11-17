package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.sync.CryptoSyncEngine
import com.example.data.sync.SyncPayload
import com.example.security.BiometricAuthManager
import com.example.ui.theme.PremiumBlueGradient
import com.example.ui.theme.PremiumTealGradient
import com.example.ui.viewmodel.RestorePreview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CloudSyncScreen(
    syncPayload: SyncPayload?,
    serializedArchive: String?,
    syncMessage: String?,
    backupBusy: Boolean,
    pendingRestorePreview: RestorePreview?,
    isBiometricLockEnabled: Boolean,
    autoLockTimeoutSeconds: Int,
    requireBiometricForSensitiveActions: Boolean,
    hideAppPreviewInRecents: Boolean,
    isMasterKeyVaultEnabled: Boolean,
    vaultUnlockedPassphrase: String?,
    rgpdConsentAcceptedAtMs: Long,
    lastLocalBackupMs: Long,
    lastSovereignSyncMs: Long,
    sovereignWebDavUrl: String,
    sovereignWebDavUsername: String,
    onSetBiometricLockEnabled: (Boolean) -> Unit,
    onSetAutoLockTimeoutSeconds: (Int) -> Unit,
    onSetRequireBiometricForSensitiveActions: (Boolean) -> Unit,
    onSetHideAppPreviewInRecents: (Boolean) -> Unit,
    onLockAppNow: () -> Unit,
    onSavePassphraseToVault: (String) -> Unit,
    onUnlockPassphraseFromVault: () -> String?,
    onClearVault: () -> Unit,
    onGenerateBackup: (String) -> Unit,
    onExportLocalUri: (String, Uri) -> Unit,
    onInspectLocalUri: (String, Uri) -> Unit,
    onInspectRawArchive: (String, String) -> Unit,
    onConfirmRestore: (Boolean) -> Unit,
    onDismissRestorePreview: () -> Unit,
    onUploadWebDav: (String, String, String, String) -> Unit,
    onDownloadWebDav: (String, String, String, String) -> Unit,
    onAcceptRgpdConsent: () -> Unit,
    onEraseAllHealthData: () -> Unit,
    onExternalPickerLaunched: () -> Unit,
    onSetAuthenticating: (Boolean) -> Unit,
    onClearMessage: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAvailable = remember { BiometricAuthManager.isAvailable(context) }
    val biometricStatusLabel = remember { BiometricAuthManager.getStatusDescription(context) }

    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var inputArchiveStr by remember { mutableStateOf("") }

    var showWebDavSection by remember { mutableStateOf(sovereignWebDavUrl.isNotBlank()) }
    var webDavUrl by remember(sovereignWebDavUrl) { mutableStateOf(sovereignWebDavUrl) }
    var webDavUser by remember(sovereignWebDavUsername) { mutableStateOf(sovereignWebDavUsername) }
    var webDavPassword by remember { mutableStateOf("") }

    var showRgpdDialog by remember { mutableStateOf(false) }
    var showEraseConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(vaultUnlockedPassphrase) {
        if (!vaultUnlockedPassphrase.isNullOrBlank() && passphrase.isBlank()) {
            passphrase = vaultUnlockedPassphrase
        }
    }

    val passwordStrength = remember(passphrase) {
        CryptoSyncEngine.evaluatePasswordStrength(passphrase)
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            onExportLocalUri(passphrase, uri)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onInspectLocalUri(passphrase, uri)
        }
    }

    fun runWithBiometricGate(
        subtitle: String,
        forceCheck: Boolean = false,
        action: () -> Unit
    ) {
        val mustPrompt = (requireBiometricForSensitiveActions || forceCheck) &&
            biometricAvailable &&
            activity != null
        if (!mustPrompt) {
            action()
            return
        }
        onSetAuthenticating(true)
        BiometricAuthManager.authenticate(
            activity = activity!!,
            title = "Confirmation Biométrique",
            subtitle = subtitle,
            confirmationRequired = true,
            onSuccess = {
                onSetAuthenticating(false)
                action()
            },
            onError = { _, msg ->
                onSetAuthenticating(false)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
            onFailedAttempt = {
                Toast.makeText(context, "Empreinte ou visage non reconnu", Toast.LENGTH_SHORT).show()
            }
        )
    }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Sauvegarde & Sécurité RGPD",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Chiffrement souverain ANSSI (AES-256-GCM) & Biométrie",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // French Compliance Hero Card (RGPD Art. 9 / ANSSI / HDS)
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        RoundedCornerShape(22.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Conformité Santé France (RGPD • ANSSI • HDS)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "100 % local par défaut — Zéro serveur non-HDS. Vos sauvegardes quittent le téléphone uniquement sous forme chiffrée Zero-Knowledge.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val consentText = if (rgpdConsentAcceptedAtMs > 0L) {
                            "Consentement RGPD actif (${dateFormatter.format(Date(rgpdConsentAcceptedAtMs))})"
                        } else {
                            "Notice RGPD Art. 9 disponible"
                        }
                        Text(
                            text = consentText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { showRgpdDialog = true },
                            modifier = Modifier.testTag("open_rgpd_notice_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Policy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Notice & Droits")
                        }
                    }
                }
            }
        }

        // Status / Progress Feedback
        if (backupBusy) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }

        if (syncMessage != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = syncMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearMessage) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        // BiometricPrompt Security Settings Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
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
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Verrouillage Biométrique (BiometricPrompt)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = biometricStatusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Protéger l’accès à l’application",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Exige empreinte, visage ou code PIN à l’ouverture",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isBiometricLockEnabled,
                            onCheckedChange = { wantEnable ->
                                if (!wantEnable && biometricAvailable) {
                                    runWithBiometricGate(
                                        subtitle = "Confirmez votre identité pour désactiver le verrouillage",
                                        forceCheck = true
                                    ) {
                                        onSetBiometricLockEnabled(false)
                                    }
                                } else {
                                    onSetBiometricLockEnabled(wantEnable)
                                }
                            },
                            modifier = Modifier.testTag("biometric_lock_switch")
                        )
                    }

                    AnimatedVisibility(visible = isBiometricLockEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Délai de re-verrouillage en arrière-plan",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val options = listOf(
                                    0 to "Immédiat",
                                    30 to "30 sec",
                                    60 to "1 min",
                                    -1 to "Manuel"
                                )
                                options.forEach { (seconds, label) ->
                                    FilterChip(
                                        selected = autoLockTimeoutSeconds == seconds,
                                        onClick = { onSetAutoLockTimeoutSeconds(seconds) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Biométrie pour exports & sauvegardes",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Confirmation requise avant sauvegarde, restauration ou partage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = requireBiometricForSensitiveActions,
                            onCheckedChange = onSetRequireBiometricForSensitiveActions,
                            modifier = Modifier.testTag("biometric_sensitive_actions_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bloquer captures d’écran (FLAG_SECURE)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Empêche captures, partage d’écran et aperçus distants",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hideAppPreviewInRecents,
                            onCheckedChange = onSetHideAppPreviewInRecents,
                            modifier = Modifier.testTag("flag_secure_switch")
                        )
                    }

                    if (isBiometricLockEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = onLockAppNow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("lock_now_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verrouiller l’application maintenant")
                        }
                    }
                }
            }
        }

        // Master Key & Android Keystore Vault Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1. Clé Maître Zero-Knowledge (ANSSI)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Dérivation PBKDF2-HMAC-SHA256 (600 000 itérations) + AES-256-GCM. Cette clé n’est jamais envoyée à aucun serveur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Mot de passe maître (12+ car. recommandés)") },
                        placeholder = { Text("Ex: MonTraitement#2026!") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                Icon(
                                    imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassphrase) "Masquer" else "Afficher"
                                )
                            }
                        },
                        visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("master_passphrase_input"),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // ANSSI Strength Bar
                    val progress = passwordStrength.score / 4f
                    val strengthColor = when (passwordStrength.score) {
                        0, 1 -> MaterialTheme.colorScheme.error
                        2 -> Color(0xFFD97706)
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = strengthColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Robustesse : ${passwordStrength.label} — ${passwordStrength.guidance}",
                        style = MaterialTheme.typography.labelSmall,
                        color = strengthColor
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Android Keystore Vault Controls
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isMasterKeyVaultEnabled) {
                                            "Clé mémorisée dans Android Keystore"
                                        } else {
                                            "Coffre matériel Android Keystore"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (isMasterKeyVaultEnabled) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Coffre actif",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Scelle votre mot de passe maître sur cet appareil pour le remplir automatiquement après validation biométrique.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!isMasterKeyVaultEnabled) {
                                    FilledTonalButton(
                                        onClick = {
                                            runWithBiometricGate(
                                                subtitle = "Sceller la clé maître dans le coffre matériel"
                                            ) {
                                                onSavePassphraseToVault(passphrase)
                                            }
                                        },
                                        enabled = passphrase.length >= 8,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Sceller via Biométrie")
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = {
                                            runWithBiometricGate(
                                                subtitle = "Déverrouiller la clé maître du coffre",
                                                forceCheck = true
                                            ) {
                                                val secret = onUnlockPassphraseFromVault()
                                                if (!secret.isNullOrBlank()) {
                                                    passphrase = secret
                                                    Toast.makeText(
                                                        context,
                                                        "Clé maître déverrouillée depuis Android Keystore",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Charger via Biométrie")
                                    }
                                    OutlinedButton(
                                        onClick = onClearVault,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Oublier")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Local Encrypted SAF Backup & Restore (.medisnap)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "2. Sauvegarde Locale Chiffrée (.medisnap)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enregistrez un fichier chiffré sur votre téléphone, clé USB ou fournisseur souverain compatible Android (Cozy Cloud, Nextcloud) sans permission de stockage large.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (lastLocalBackupMs > 0L) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Dernier export local : ${dateFormatter.format(Date(lastLocalBackupMs))}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            runWithBiometricGate(
                                subtitle = "Confirmer l’export local chiffré de vos traitements"
                            ) {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.FRANCE).format(Date())
                                onExternalPickerLaunched()
                                createDocumentLauncher.launch("medisnap_sauvegarde_$stamp.medisnap")
                            }
                        },
                        enabled = passphrase.length >= 8 && !backupBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                brush = if (passphrase.length >= 8 && !backupBusy) {
                                    PremiumBlueGradient
                                } else {
                                    androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant)
                                }
                            )
                            .testTag("export_local_backup_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            contentColor = Color.White,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exporter un fichier local (.medisnap)", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            runWithBiometricGate(
                                subtitle = "Confirmer l’ouverture d’une archive médicale chiffrée"
                            ) {
                                onExternalPickerLaunched()
                                openDocumentLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
                            }
                        },
                        enabled = passphrase.isNotBlank() && !backupBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("import_local_backup_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restaurer depuis un fichier .medisnap")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            runWithBiometricGate(
                                subtitle = "Générer un aperçu d’enveloppe chiffrée"
                            ) {
                                onGenerateBackup(passphrase)
                            }
                        },
                        enabled = passphrase.length >= 8 && !backupBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Générer une enveloppe texte chiffrée (copie rapide)")
                    }
                }
            }
        }

        // Generated Archive Envelope Preview (when generated)
        if (syncPayload != null && serializedArchive != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Archive Chiffrée v${syncPayload.version} Prête",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("MediSnap Archive", serializedArchive)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(
                                        context,
                                        "Archive chiffrée copiée dans le presse-papier",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copier l’archive")
                            }
                        }

                        Text(
                            text = "Source : ${syncPayload.deviceId} • PBKDF2 ${syncPayload.iterations} itérations",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (syncPayload.sha256Digest.isNotBlank()) {
                            Text(
                                text = "SHA-256 : ${syncPayload.sha256Digest.take(24)}...",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = serializedArchive.take(180) + "...",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Manual Paste Restore + Optional Sovereign French/EU WebDAV (Nextcloud / Cozy / HDS)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
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
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "3. Cloud Souverain / HDS (WebDAV HTTPS)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Optionnel : Nextcloud, Cozy Cloud ou serveur HDS en France/UE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { showWebDavSection = !showWebDavSection }) {
                            Icon(
                                imageVector = if (showWebDavSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Déplier configuration WebDAV"
                            )
                        }
                    }

                    AnimatedVisibility(visible = showWebDavSection) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Seules des archives AES-256-GCM déjà chiffrées sur votre appareil transitent via HTTPS TLS.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (lastSovereignSyncMs > 0L) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Dernière synchro souveraine : ${dateFormatter.format(Date(lastSovereignSyncMs))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = webDavUrl,
                                onValueChange = { webDavUrl = it },
                                label = { Text("URL HTTPS WebDAV / Nextcloud") },
                                placeholder = { Text("https://mon-cloud.fr/remote.php/dav/files/moi/") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = webDavUser,
                                    onValueChange = { webDavUser = it },
                                    label = { Text("Identifiant") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = webDavPassword,
                                    onValueChange = { webDavPassword = it },
                                    label = { Text("Jeton / Mot de passe") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        runWithBiometricGate(
                                            subtitle = "Confirmer l’envoi chiffré vers votre serveur souverain"
                                        ) {
                                            onUploadWebDav(passphrase, webDavUrl, webDavUser, webDavPassword)
                                        }
                                    },
                                    enabled = passphrase.length >= 8 && webDavUrl.isNotBlank() && !backupBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            brush = if (passphrase.length >= 8 && webDavUrl.isNotBlank() && !backupBusy) {
                                                PremiumTealGradient
                                            } else {
                                                androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant)
                                            }
                                        ),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        contentColor = Color.White,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Envoyer")
                                }

                                OutlinedButton(
                                    onClick = {
                                        runWithBiometricGate(
                                            subtitle = "Confirmer la récupération depuis votre serveur souverain"
                                        ) {
                                            onDownloadWebDav(passphrase, webDavUrl, webDavUser, webDavPassword)
                                        }
                                    },
                                    enabled = passphrase.isNotBlank() && webDavUrl.isNotBlank() && !backupBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Récupérer")
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    )

                    Text(
                        text = "Import manuel depuis texte d’archive (.medisnap)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputArchiveStr,
                        onValueChange = { inputArchiveStr = it },
                        label = { Text("Coller le contenu JSON de l’archive") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            runWithBiometricGate(
                                subtitle = "Déchiffrer et inspecter l’archive collée"
                            ) {
                                onInspectRawArchive(passphrase, inputArchiveStr)
                            }
                        },
                        enabled = passphrase.isNotBlank() && inputArchiveStr.isNotBlank() && !backupBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vérifier & Déchiffrer l’archive")
                    }
                }
            }
        }

        // RGPD Art. 17 Right to Erasure Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Droit à l’effacement (Art. 17 RGPD)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Supprime immédiatement tous vos médicaments, historiques et clés Keystore locaux.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            runWithBiometricGate(
                                subtitle = "Authentifiez-vous avant d’effacer vos données médicales",
                                forceCheck = true
                            ) {
                                showEraseConfirmDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("rgpd_erase_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Effacer tout")
                    }
                }
            }
        }
    }

    // Restore Preview Dialog (Atomic Replace vs Merge)
    if (pendingRestorePreview != null) {
        val preview = pendingRestorePreview
        AlertDialog(
            onDismissRequest = onDismissRestorePreview,
            icon = { Icon(Icons.Default.VerifiedUserFallback(), contentDescription = null) },
            title = { Text("Archive Vérifiée (${preview.sourceLabel})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "L’authenticité AES-GCM et l’empreinte SHA-256 ont été validées.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "• Appareil source : ${preview.payload.deviceId}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Date : ${dateFormatter.format(Date(preview.payload.timestamp))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Traitements trouvés : ${preview.medications.size}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Historique de prises : ${preview.logs.size}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Choisissez le mode d’importation :",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmRestore(true) }
                ) {
                    Text("Remplacer tout")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismissRestorePreview) {
                        Text("Annuler")
                    }
                    OutlinedButton(onClick = { onConfirmRestore(false) }) {
                        Text("Fusionner")
                    }
                }
            }
        )
    }

    // French RGPD / ANSSI / HDS Compliance Notice Dialog
    if (showRgpdDialog) {
        AlertDialog(
            onDismissRequest = { showRgpdDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Policy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Notice de Conformité Santé (France / UE)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "1. Données de santé (RGPD Article 9) : Vos traitements, posologies et historiques sont traités exclusivement sur votre appareil pour votre suivi personnel.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Hébergement HDS (Art. L.1111-8 CSP) : Aucun serveur éditeur ne stocke vos données de santé. Les sauvegardes sortantes sont chiffrées localement (Zero-Knowledge) avant export sur fichier local ou serveur WebDAV souverain HTTPS de votre choix.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Référentiel ANSSI (RGS) : Chiffrement authentifié AES-256-GCM, dérivation PBKDF2-HMAC-SHA256 à 600 000 itérations, contrôle d’intégrité SHA-256, coffre matériel Android Keystore et verrouillage BiometricPrompt.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "4. Vos droits : Portabilité (.medisnap) et effacement immédiat (Art. 17 & 20 RGPD) accessibles directement depuis cet écran.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAcceptRgpdConsent()
                        showRgpdDialog = false
                    }
                ) {
                    Text("J’accepte et je comprends")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRgpdDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }

    // Erase All Confirmation Dialog
    if (showEraseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEraseConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Effacer toutes vos données ?") },
            text = {
                Text(
                    "Cette action supprime définitivement vos médicaments, votre historique d’observance et les clés scellées dans Android Keystore sur cet appareil. Pensez à exporter une sauvegarde .medisnap avant."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEraseConfirmDialog = false
                        onEraseAllHealthData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text("Tout effacer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseConfirmDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun Icons.Filled.VerifiedUserFallback() = Security
