package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.QuickAddSheet
import com.example.ui.screens.AdherenceReportScreen
import com.example.ui.screens.AiScanScreen
import com.example.ui.screens.CloudSyncScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MedicationsScreen
import com.example.ui.screens.PharmacistAssistantScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MediSnapViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MediSnapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                var showQuickAddSheet by remember { mutableStateOf(false) }

                val medications by viewModel.medications.collectAsStateWithLifecycle()
                val todayLogs by viewModel.todayLogs.collectAsStateWithLifecycle()
                val allLogs by viewModel.allLogs.collectAsStateWithLifecycle()
                val syncPayload by viewModel.syncPayload.collectAsStateWithLifecycle()
                val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()

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
                                icon = { Icon(Icons.Default.CloudSync, contentDescription = "Cloud E2EE") },
                                label = { Text("Cloud") },
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
                                onMarkTaken = { viewModel.markTaken(it) },
                                onMarkSkipped = { viewModel.markSkipped(it) },
                                onNavigateToAdd = { showQuickAddSheet = true },
                                onNavigateToSync = { navController.navigate("sync") }
                            )
                        }

                        composable("scan") {
                            AiScanScreen(
                                onAddMedication = { name, dosage, form, colorHex, instructions, times, stock ->
                                    viewModel.addMedication(name, dosage, form, colorHex, instructions, times, stock)
                                }
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
                                allLogs = allLogs
                            )
                        }

                        composable("sync") {
                            CloudSyncScreen(
                                syncPayload = syncPayload,
                                syncMessage = syncMessage,
                                onGenerateBackup = { viewModel.generateE2EEBackup(it) },
                                onRestoreBackup = { passphrase, payload -> viewModel.restoreE2EEBackup(passphrase, payload) },
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
