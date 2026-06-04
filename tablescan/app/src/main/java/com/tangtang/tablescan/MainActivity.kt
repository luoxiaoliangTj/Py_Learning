package com.tangtang.tablescan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tangtang.tablescan.ui.screen.*
import com.tangtang.tablescan.ui.theme.TableScanTheme
import com.tangtang.tablescan.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.d("TableScan", "Camera permission granted")
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { (perm, granted) ->
            Log.d("TableScan", "Permission $perm: ${if (granted) "GRANTED" else "DENIED"}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions at startup
        requestPermissions()

        setContent {
            TableScanTheme {
                val navController = rememberNavController()
                val homeViewModel: HomeViewModel = viewModel()

                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        HomeScreen(
                            navController = navController,
                            viewModel = homeViewModel
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            navController = navController
                        )
                    }
                    composable("detail/{recordId}") { backStackEntry ->
                        val recordId = backStackEntry.arguments?.getString("recordId")?.toLongOrNull() ?: 0L
                        DetailScreen(
                            navController = navController,
                            recordId = recordId
                        )
                    }
                    composable("debug") {
                        DebugScreen(
                            navController = navController
                        )
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        // Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Storage (Android 13+ uses READ_MEDIA_IMAGES, older uses READ_EXTERNAL_STORAGE)
        val storagePermissions = if (android.os.Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        val needsStorage = storagePermissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needsStorage) {
            storagePermissionLauncher.launch(storagePermissions)
        }
    }
}
