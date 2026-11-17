package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.security.BiometricAuthManager
import com.example.security.configureAppCheck
import com.example.ui.components.QuickAddSheet
import com.example.ui.screens.AdherenceReportScreen
import com.example.ui.screens.AiScanScreen
import com.example.ui.screens.BiometricLockScreen
import com.example.ui.screens.CloudSyncScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MedicationsScreen
import com.example.ui.screens.PharmacistAssistantScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MediSnapViewModel
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {

    private val viewModel: MediSnapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val isBiometricLockEnabled by viewModel.isBiometricLockEnabled.collectAsStateWithLifecycle()
                val isAppUnlocked by viewModel.isAppUnlocked.collectAsStateWithLifecycle()
                val autoLockTimeoutSeconds by viewModel.autoLockTimeoutSeconds.collectAsStateWithLifecycle()
                val requireBiometricForSensitiveActions by viewModel.requireBiometricForSensitiveActions.collectAsStateWithLifecycle()
                val hideAppPreviewInRecents by viewModel.hideAppPreviewInRecents.collectAsStateWithLifecycle()
                val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
                val authErrorMessage by viewModel.authErrorMessage.collectAsStateWithLifecycle()

                // Hide sensitive medication screens from Recents switcher on Android 13+ without blocking emulator streaming
                LaunchedEffect(isBiometricLockEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setRecentsScreenshotEnabled(!isBiometricLockEnabled)
                    }
                }

                // Opt-in FLAG_SECURE (blocks all screenshots & virtual display streaming when enabled by user)
                LaunchedEffect(hideAppPreviewInRecents) {
                    if (hideAppPreviewInRecents) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                // Observe lifecycle to re-lock automatically when returning from background
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                            Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val promptTitle = stringResource(R.string.biometric_prompt_title)
                val promptSubtitle = stringResource(R.string.biometric_prompt_subtitle)

                fun launchBiometricUnlock() {
                    if (isAuthenticating) return
                    if (!BiometricAuthManager.isAvailable(this@MainActivity)) {
                        viewModel.onAuthError(BiometricAuthManager.getStatusDescription(this@MainActivity))
                        return
                    }
                    viewModel.clearAuthError()
                    viewModel.setAuthenticating(true)
                    BiometricAuthManager.authenticate(
                        activity = this@MainActivity,
                        title = promptTitle,
                        subtitle = promptSubtitle,
                        confirmationRequired = false,
                        onSuccess = { viewModel.onUnlockSucceeded() },
                        onError = { _, msg -> viewModel.onAuthError(msg) },
                        onFailedAttempt = { viewModel.onAuthFailedAttempt() }
                    )
                }

                val isLocked = isBiometricLockEnabled && !isAppUnlocked
                var autoPromptedForCurrentLock by remember { mutableStateOf(false) }

                LaunchedEffect(isLocked) {
                    if (isLocked) {
                        if (!autoPromptedForCurrentLock && BiometricAuthManager.isAvailable(this@MainActivity)) {
                            autoPromptedForCurrentLock = true
                            lifecycleOwner.lifecycle.withResumed {
                                // Short settle delay so the window has focus before BiometricPrompt attaches
                            }
                            delay(250)
                            if (isLocked && !isAuthenticating) {
                                launchBiometricUnlock()
                            }
                        }
                    } else {
                        autoPromptedForCurrentLock = false
                    }
                }

                if (isLocked) {
                    val biometricAvailable = BiometricAuthManager.isAvailable(this@MainActivity)
                    val statusDescription = BiometricAuthManager.getStatusDescription(this@MainActivity)

                    BiometricLockScreen(
                        isBiometricAvailable = biometricAvailable,
                        statusDescription = statusDescription,
                        errorMessage = authErrorMessage,
                        isAuthenticating = isAuthenticating,
                        onTriggerBiometric = { launchBiometricUnlock() },
                        onOpenSecuritySettings = {
                            viewModel.markExternalActivityLaunched()
                            runCatching {
                                startActivity(BiometricAuthManager.createEnrollIntent())
                            }
                        },
                        onContinueUnconfigured = {
                            if (!biometricAvailable) {
                                viewModel.unlockWhenDeviceUnconfigured()
                            }
                        }
                    )
                } else {
                    val navController = rememberNavController()
                    var showQuickAddSheet by remember { mutableStateOf(false) }

                    val medications by viewModel.medications.collectAsStateWithLifecycle()
                    val todayLogs by viewModel.todayLogs.collectAsStateWithLifecycle()
                    val allLogs by viewModel.allLogs.collectAsStateWithLifecycle()
                    val syncPayload by viewModel.syncPayload.collectAsStateWithLifecycle()
                    val serializedArchive by viewModel.serializedArchive.collectAsStateWithLifecycle()
                    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
                    val backupBusy by viewModel.backupBusy.collectAsStateWithLifecycle()
                    val pendingRestorePreview by viewModel.pendingRestorePreview.collectAsStateWithLifecycle()
                    val isMasterKeyVaultEnabled by viewModel.isMasterKeyVaultEnabled.collectAsStateWithLifecycle()
                    val vaultUnlockedPassphrase by viewModel.vaultUnlockedPassphrase.collectAsStateWithLifecycle()
                    val rgpdConsentAcceptedAtMs by viewModel.rgpdConsentAcceptedAtMs.collectAsStateWithLifecycle()
                    val lastLocalBackupMs by viewModel.lastLocalBackupMs.collectAsStateWithLifecycle()
                    val lastSovereignSyncMs by viewModel.lastSovereignSyncMs.collectAsStateWithLifecycle()
                    val sovereignWebDavUrl by viewModel.sovereignWebDavUrl.collectAsStateWithLifecycle()
                    val sovereignWebDavUsername by viewModel.sovereignWebDavUsername.collectAsStateWithLifecycle()

                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 6.dp,
                                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute == "dashboard",
                                    onClick = { navController.navigate("dashboard") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Accueil") },
                                    label = { Text("Aujourd'hui") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "scan",
                                    onClick = { navController.navigate("scan") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Scan IA") },
                                    label = { Text("Scan IA") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "assistant",
                                    onClick = { navController.navigate("assistant") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.MedicalServices, contentDescription = "Assistant") },
                                    label = { Text("Assistant") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "medications",
                                    onClick = { navController.navigate("medications") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.Medication, contentDescription = "Médicaments") },
                                    label = { Text("Traitement") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "adherence",
                                    onClick = { navController.navigate("adherence") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.Assessment, contentDescription = "Observance") },
                                    label = { Text("Observance") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentRoute == "sync",
                                    onClick = { navController.navigate("sync") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.Security, contentDescription = "Sauvegarde et RGPD") },
                                    label = { Text("Sécurité") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "dashboard",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            composable("dashboard") {
                                DashboardScreen(
                                    medications = medications,
                                    todayLogs = todayLogs,
                                    isBiometricLockEnabled = isBiometricLockEnabled,
                                    onMarkTaken = { viewModel.markTaken(it) },
                                    onMarkSkipped = { viewModel.markSkipped(it) },
                                    onNavigateToAdd = { showQuickAddSheet = true },
                                    onNavigateToSync = { navController.navigate("sync") },
                                    onLockApp = { viewModel.lockApp() }
                                )
                            }

                            composable("scan") {
                                AiScanScreen(
                                    onAddMedication = { name, dosage, form, colorHex, instructions, times, stock ->
                                        viewModel.addMedication(name, dosage, form, colorHex, instructions, times, stock)
                                    },
                                    onExternalPickerLaunched = { viewModel.markExternalActivityLaunched() }
                                )
                            }

                            composable("assistant") {
                                PharmacistAssistantScreen(
                                    medications = medications,
                                    onAddMedication = { name, dosage, form, colorHex, instructions, times, stock ->
                                        viewModel.addMedication(name, dosage, form, colorHex, instructions, times, stock)
                                    }
                                )
                            }

                            composable("medications") {
                                MedicationsScreen(
                                    medications = medications,
                                    onAddClick = { showQuickAddSheet = true },
                                    onDeleteClick = { viewModel.deleteMedication(it) },
                                    onAddStock = { id, amount -> viewModel.addStock(id, amount) }
                                )
                            }

                            composable("adherence") {
                                AdherenceReportScreen(
                                    allLogs = allLogs,
                                    requireBiometricForExport = requireBiometricForSensitiveActions,
                                    onSetAuthenticating = { viewModel.setAuthenticating(it) },
                                    onExternalPickerLaunched = { viewModel.markExternalActivityLaunched() }
                                )
                            }

                            composable("sync") {
                                CloudSyncScreen(
                                    syncPayload = syncPayload,
                                    serializedArchive = serializedArchive,
                                    syncMessage = syncMessage,
                                    backupBusy = backupBusy,
                                    pendingRestorePreview = pendingRestorePreview,
                                    isBiometricLockEnabled = isBiometricLockEnabled,
                                    autoLockTimeoutSeconds = autoLockTimeoutSeconds,
                                    requireBiometricForSensitiveActions = requireBiometricForSensitiveActions,
                                    hideAppPreviewInRecents = hideAppPreviewInRecents,
                                    isMasterKeyVaultEnabled = isMasterKeyVaultEnabled,
                                    vaultUnlockedPassphrase = vaultUnlockedPassphrase,
                                    rgpdConsentAcceptedAtMs = rgpdConsentAcceptedAtMs,
                                    lastLocalBackupMs = lastLocalBackupMs,
                                    lastSovereignSyncMs = lastSovereignSyncMs,
                                    sovereignWebDavUrl = sovereignWebDavUrl,
                                    sovereignWebDavUsername = sovereignWebDavUsername,
                                    onSetBiometricLockEnabled = { viewModel.setBiometricLockEnabled(it) },
                                    onSetAutoLockTimeoutSeconds = { viewModel.setAutoLockTimeoutSeconds(it) },
                                    onSetRequireBiometricForSensitiveActions = {
                                        viewModel.setRequireBiometricForSensitiveActions(it)
                                    },
                                    onSetHideAppPreviewInRecents = { viewModel.setHideAppPreviewInRecents(it) },
                                    onLockAppNow = { viewModel.lockApp() },
                                    onSavePassphraseToVault = { viewModel.savePassphraseToVault(it) },
                                    onUnlockPassphraseFromVault = { viewModel.unsealVaultIfAvailable() },
                                    onClearVault = { viewModel.clearPassphraseVault() },
                                    onGenerateBackup = { viewModel.generateE2EEBackup(it) },
                                    onExportLocalUri = { pass, uri ->
                                        viewModel.exportEncryptedArchiveToUri(pass, uri)
                                    },
                                    onInspectLocalUri = { pass, uri ->
                                        viewModel.inspectAndDecryptLocalUri(pass, uri)
                                    },
                                    onInspectRawArchive = { pass, raw ->
                                        viewModel.inspectAndDecryptRawArchive(pass, raw)
                                    },
                                    onConfirmRestore = { replace -> viewModel.confirmRestore(replace) },
                                    onDismissRestorePreview = { viewModel.dismissRestorePreview() },
                                    onUploadWebDav = { pass, url, user, pwd ->
                                        viewModel.uploadToSovereignWebDav(pass, url, user, pwd)
                                    },
                                    onDownloadWebDav = { pass, url, user, pwd ->
                                        viewModel.downloadFromSovereignWebDav(pass, url, user, pwd)
                                    },
                                    onAcceptRgpdConsent = { viewModel.acceptRgpdConsent() },
                                    onEraseAllHealthData = { viewModel.eraseAllHealthData() },
                                    onExternalPickerLaunched = { viewModel.markExternalActivityLaunched() },
                                    onSetAuthenticating = { viewModel.setAuthenticating(it) },
                                    onClearMessage = { viewModel.clearSyncMessage() }
                                )
                            }
                        }

                        if (showQuickAddSheet) {
                            QuickAddSheet(
                                onDismiss = { showQuickAddSheet = false },
                                onAdd = { name, dosage, form, colorHex, instructions, times, stock ->
                                    viewModel.addMedication(name, dosage, form, colorHex, instructions, times, stock)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
