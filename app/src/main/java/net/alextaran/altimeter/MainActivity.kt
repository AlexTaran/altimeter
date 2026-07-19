package net.alextaran.altimeter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import net.alextaran.altimeter.storage.AppDatabase
import net.alextaran.altimeter.ui.AltitudeChart
import net.alextaran.altimeter.ui.theme.AltimeterTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AltimeterTheme {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current

                val requiredPermissions = remember {
                    listOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }

                var permissionStates by remember { mutableStateOf(emptyMap<String, Boolean>()) }

                val updatePermissions = {
                    permissionStates = requiredPermissions.associateWith {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                }

                val bgLocationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    updatePermissions()
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    updatePermissions()
                    if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                        bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START) {
                            updatePermissions()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val isServiceRunning by AltimeterService.isRunning.collectAsState()
                        val allPermissionsGranted = requiredPermissions.all { permissionStates[it] == true }

                        // Start/Stop button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(context, AltimeterService::class.java)
                                    if (isServiceRunning) {
                                        context.stopService(intent)
                                    } else {
                                        context.startForegroundService(intent)
                                    }
                                },
                                enabled = isServiceRunning || allPermissionsGranted,
                                modifier = Modifier
                                    .height(100.dp)
                                    .fillMaxWidth(0.5f)
                            ) {
                                Text(
                                    text = if (isServiceRunning) stringResource(R.string.action_stop_altimeter) else stringResource(R.string.action_start_altimeter),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        // Altitude chart — last 2 hours
                        val db = remember { AppDatabase.getDatabase(context) }
                        val windowEndMs = remember { System.currentTimeMillis() }
                        val windowStartMs = remember { windowEndMs - 2 * 60 * 60 * 1000L }
                        val altitudePoints by db.altitudeDao().getPointsSince(windowStartMs)
                            .collectAsState(initial = emptyList())

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "Altitude — last 2 hours",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AltitudeChart(
                                points = altitudePoints,
                                windowStartMs = windowStartMs,
                                windowEndMs = windowEndMs,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                            )
                        }

                        // Add a weighted spacer to push permissions section to the bottom of the screen
                        Spacer(modifier = Modifier.weight(1f))

                        if (!allPermissionsGranted) {
                            Button(
                                onClick = {
                                    val fineGranted = permissionStates[Manifest.permission.ACCESS_FINE_LOCATION] == true
                                    val notifGranted = permissionStates[Manifest.permission.POST_NOTIFICATIONS] == true

                                    if (!fineGranted || !notifGranted) {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        )
                                    } else {
                                        bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.btn_action_give_permissions))
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(stringResource(R.string.title_permissions_status), style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            permissionStates.forEach { (permission, isGranted) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = permission.substringAfterLast("."),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (isGranted) stringResource(R.string.status_enabled) else stringResource(R.string.status_disabled),
                                        color = if (isGranted) colorResource(R.color.color_enabled) else colorResource(R.color.color_disabled),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
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