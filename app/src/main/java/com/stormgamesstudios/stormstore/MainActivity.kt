package com.stormgamesstudios.stormstore

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.stormgamesstudios.stormstore.ui.theme.StormStoreTheme
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class StoreApp(
    val name: String,
    val packageName: String,
    val description: String,
    val imageUrl: String,
    val downloadUrl: String,
    val version: String
)

class MainActivity : ComponentActivity() {

    private lateinit var downloadReceiver: BroadcastReceiver
    private var updateUrl by mutableStateOf<String?>(null)
    private var latestVersionName by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setupDownloadReceiver()

        setContent {
            StormStoreTheme {
                var showUpdateDialog by remember { mutableStateOf(false) }
                var currentTab by remember { mutableStateOf(0) }
                
                LaunchedEffect(updateUrl) {
                    if (updateUrl != null) {
                        val currentVersion = try {
                            packageManager.getPackageInfo(packageName, 0).versionName
                                ?.lowercase()?.removePrefix("v")?.trim() ?: ""
                        } catch (e: Exception) { "" }

                        if (latestVersionName != currentVersion) {
                            showUpdateDialog = true
                        }
                    }
                }

                Scaffold(
                    bottomBar = {
                        MainBottomBar(
                            selectedItem = currentTab,
                            onItemSelected = { currentTab = it }
                        )
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        RequestPermissions {
                            Box {
                                when (currentTab) {
                                    0 -> StoreListScreen(this@MainActivity)
                                    1 -> DownloadsScreen(this@MainActivity)
                                    2 -> UpdatesScreen(
                                        context = this@MainActivity,
                                        latestVersionFound = latestVersionName,
                                        onCheckUpdate = {
                                            checkUpdate(this@MainActivity) { url, version ->
                                                updateUrl = url
                                                latestVersionName = version
                                            }
                                        }
                                    )
                                    3 -> AboutScreen()
                                }
                                
                                if (showUpdateDialog && updateUrl != null) {
                                    UpdateDialog(
                                        version = latestVersionName,
                                        onDismiss = { showUpdateDialog = false },
                                        onConfirm = {
                                            descargarAppYIcono(this@MainActivity, updateUrl!!, "")
                                            showUpdateDialog = false
                                            Toast.makeText(this@MainActivity, "Iniciando descarga...", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        checkUpdate(this) { url, version ->
            updateUrl = url
            latestVersionName = version
        }
    }

    private fun setupDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                if (id != -1L && context != null) {
                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val query = DownloadManager.Query().setFilterById(id)
                    val cursor = dm.query(query)
                    
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL) {
                            val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val uriString = cursor.getString(localUriIdx)
                            if (uriString != null && uriString.endsWith(".apk")) {
                                val file = File(Uri.parse(uriString).path ?: "")
                                if (file.exists()) {
                                    installApk(context, file)
                                }
                            }
                        }
                    }
                    cursor.close()
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::downloadReceiver.isInitialized) {
            unregisterReceiver(downloadReceiver)
        }
    }
}

@Composable
fun MainBottomBar(selectedItem: Int, onItemSelected: (Int) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Tienda") },
            label = { Text("Tienda") },
            selected = selectedItem == 0,
            onClick = { onItemSelected(0) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Archive, contentDescription = "Descargas") },
            label = { Text("Descargas") },
            selected = selectedItem == 1,
            onClick = { onItemSelected(1) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") },
            label = { Text("Actualizar") },
            selected = selectedItem == 2,
            onClick = { onItemSelected(2) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Info, contentDescription = "Info") },
            label = { Text("Info") },
            selected = selectedItem == 3,
            onClick = { onItemSelected(3) }
        )
    }
}

@Composable
fun StoreListScreen(context: Context) {
    var apps by remember { mutableStateOf<List<StoreApp>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var screenState by remember { mutableStateOf(ScreenState.Loading) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val filteredApps = remember(apps, searchQuery) {
        apps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(refreshTrigger) {
        screenState = ScreenState.Loading
        Thread {
            try {
                val url = URL("https://raw.githubusercontent.com/acierto-incomodo/stormstore-android/main/apps.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "StormStore-App")
                
                val data = connection.inputStream.bufferedReader().readText()
                val jsonArray = JSONArray(data)
                val appList = mutableListOf<StoreApp>()
                
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    appList.add(StoreApp(
                        name = obj.getString("name"),
                        packageName = obj.optString("packageName", ""),
                        description = obj.getString("description"),
                        imageUrl = obj.getString("imageUrl"),
                        downloadUrl = obj.getString("downloadUrl"),
                        version = obj.getString("version")
                    ))
                }
                apps = appList
                screenState = ScreenState.Content
            } catch (e: Exception) {
                e.printStackTrace()
                screenState = ScreenState.Error
            }
        }.start()
    }

    when (screenState) {
        ScreenState.Loading -> SplashScreen()
        ScreenState.Error -> ErrorScreen(onRetry = { refreshTrigger++ })
        ScreenState.Content -> {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Tienda", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recargar")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar aplicaciones...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No se encontraron aplicaciones", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(filteredApps) { app ->
                            StoreAppCard(app) { 
                                if (isAppInstalledAndUpToDate(context, app.packageName, app.version)) {
                                    launchApp(context, app.packageName)
                                } else {
                                    descargarAppYIcono(context, app.downloadUrl, app.imageUrl)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun isAppInstalledAndUpToDate(context: Context, packageName: String, version: String): Boolean {
    if (packageName.isEmpty()) return false
    return try {
        val pInfo = context.packageManager.getPackageInfo(packageName, 0)
        val installedVersion = pInfo.versionName?.lowercase()?.removePrefix("v")?.trim() ?: ""
        val targetVersion = version.lowercase().removePrefix("v").trim()
        installedVersion == targetVersion
    } catch (e: Exception) {
        false
    }
}

fun launchApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "No se pudo abrir la aplicación", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun StoreAppCard(app: StoreApp, onAction: () -> Unit) {
    val context = LocalContext.current
    val isUpToDate = isAppInstalledAndUpToDate(context, app.packageName, app.version)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = app.imageUrl,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = "v${app.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(text = app.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onAction) {
                Icon(
                    imageVector = if (isUpToDate) Icons.Default.PlayArrow else Icons.Default.Download,
                    contentDescription = if (isUpToDate) "Abrir" else "Descargar",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DownloadsScreen(context: Context) {
    var downloadedFiles by remember { mutableStateOf(getDownloadedApks(context)) }

    LaunchedEffect(Unit) {
        while(true) {
            downloadedFiles = getDownloadedApks(context)
            kotlinx.coroutines.delay(2000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Mis Descargas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (downloadedFiles.isNotEmpty()) {
                IconButton(onClick = {
                    val dir = context.getExternalFilesDir(null)
                    dir?.listFiles()?.forEach { it.delete() }
                    downloadedFiles = emptyList()
                    Toast.makeText(context, "Todas las descargas eliminadas", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Eliminar todo", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (downloadedFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No hay APKs descargados", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(downloadedFiles) { file ->
                    ApkItem(file = file, onInstall = { installApk(context, file) }, onDelete = { 
                        file.delete()
                        val iconFile = File(file.absolutePath.replace(".apk", ".png"))
                        if (iconFile.exists()) iconFile.delete()
                        downloadedFiles = getDownloadedApks(context) 
                    })
                }
            }
        }
    }
}

@Composable
fun ApkItem(file: File, onInstall: () -> Unit, onDelete: () -> Unit) {
    val iconFile = File(file.absolutePath.replace(".apk", ".png"))
    
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (iconFile.exists()) {
                AsyncImage(
                    model = iconFile,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = file.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = String.format(Locale.getDefault(), "%.2f MB", file.length() / (1024f * 1024f)), style = MaterialTheme.typography.bodySmall)
            }
            Row {
                IconButton(onClick = onInstall) { Icon(Icons.Default.PlayArrow, contentDescription = "Instalar", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

fun getDownloadedApks(context: Context): List<File> {
    val dir = context.getExternalFilesDir(null)
    return dir?.listFiles { file -> file.extension == "apk" }?.toList() ?: emptyList()
}

fun installApk(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Error al instalar: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun UpdatesScreen(context: Context, latestVersionFound: String, onCheckUpdate: () -> Unit) {
    val currentVersion = remember { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (e: Exception) { "Unknown" } }
    var isChecking by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Centro de Actualización", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(32.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Versión instalada:", fontWeight = FontWeight.Bold)
                        Text("v${currentVersion ?: "1.0.0"}")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Última disponible:", fontWeight = FontWeight.Bold)
                        Text(if (latestVersionFound.isEmpty()) "Desconocida" else "v$latestVersionFound")
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
            Button(onClick = { isChecking = true; onCheckUpdate(); Handler(Looper.getMainLooper()).postDelayed({ isChecking = false }, 2000) }, modifier = Modifier.height(52.dp).fillMaxWidth(), enabled = !isChecking) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Buscando...")
                } else {
                    Text("Buscar actualizaciones")
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(painter = painterResource(id = R.mipmap.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(120.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "StormStore", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(text = "Tu tienda de juegos favorita", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(48.dp))
            Text(text = "Desarrollado por:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Text(text = "StormGamesStudios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun UpdateDialog(version: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, title = { Text(text = "Actualización disponible") }, text = { Column { Text(text = "Se ha encontrado una nueva versión: ", style = MaterialTheme.typography.bodyMedium); Text(text = "v$version", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.height(8.dp)); Text(text = "¿Deseas descargarla e instalarla ahora?") } }, confirmButton = { Button(onClick = onConfirm) { Text("Actualizar ahora") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Más tarde") } })
}

@Composable
fun RequestPermissions(onPermissionsGranted: @Composable () -> Unit) {
    val context = LocalContext.current
    var hasNotificationPermission by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED } else true) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> hasNotificationPermission = isGranted }
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { if (!context.packageManager.canRequestPackageInstalls()) { val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply { data = Uri.parse("package:${context.packageName}") }; context.startActivity(intent) } } }
    onPermissionsGranted()
}

enum class ScreenState { Loading, Content, Error }

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val scale by infiniteTransition.animateFloat(initialValue = 0.95f, targetValue = 1.05f, animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "Scale")
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Image(painter = painterResource(id = R.mipmap.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(160.dp).scale(scale)); Spacer(modifier = Modifier.height(32.dp)); CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp) } }
}

@Composable
fun ErrorScreen(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(imageVector = Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.error); Spacer(modifier = Modifier.height(24.dp)); Text(text = "Sin conexión", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground); Spacer(modifier = Modifier.height(12.dp)); Text(text = "No se pudo conectar con la tienda. Revisa tu conexión a internet e inténtalo de nuevo.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center); Spacer(modifier = Modifier.height(40.dp)); Button(onClick = onRetry, modifier = Modifier.height(52.dp)) { Text("Reintentar", modifier = Modifier.padding(horizontal = 16.dp)) } } }
}

fun descargarAppYIcono(context: Context, apkUrl: String, imageUrl: String) {
    val fileName = apkUrl.substringAfterLast("/")
    val file = File(context.getExternalFilesDir(null), fileName)
    if (file.exists()) file.delete()

    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val apkRequest = DownloadManager.Request(Uri.parse(apkUrl)).apply {
        setTitle("Descargando $fileName")
        setDestinationInExternalFilesDir(context, null, fileName)
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    }
    dm.enqueue(apkRequest)

    if (imageUrl.isNotEmpty()) {
        val iconName = fileName.replace(".apk", ".png")
        val iconFile = File(context.getExternalFilesDir(null), iconName)
        if (iconFile.exists()) iconFile.delete()

        val iconRequest = DownloadManager.Request(Uri.parse(imageUrl)).apply {
            setTitle("Icono de $fileName")
            setDestinationInExternalFilesDir(context, null, iconName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }
        dm.enqueue(iconRequest)
    }
}

fun checkUpdate(context: Context, onUpdateAvailable: (url: String?, version: String) -> Unit) {
    Thread {
        try {
            val url = URL("https://api.github.com/repos/acierto-incomodo/stormstore-android/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "StormStore-App")
            val data = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(data)
            val latestTag = json.getString("tag_name")
            val latestVersion = latestTag.lowercase().removePrefix("v").trim()
            val assets = json.getJSONArray("assets")
            val apkUrl = if (assets.length() > 0) assets.getJSONObject(0).getString("browser_download_url") else null
            onUpdateAvailable(apkUrl, latestVersion)
        } catch (e: Exception) { e.printStackTrace() }
    }.start()
}
