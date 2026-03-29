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
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.stormgamesstudios.stormstore.ui.theme.StormStoreTheme

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    private lateinit var downloadReceiver: BroadcastReceiver
    private var updateUrl by mutableStateOf<String?>(null)
    private var newVersionName by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setupDownloadReceiver()

        setContent {
            StormStoreTheme {
                var showUpdateDialog by remember { mutableStateOf(false) }
                var currentTab by remember { mutableStateOf(0) } // 0: Tienda, 1: Descargas, 2: Actualizar, 3: Info
                
                LaunchedEffect(updateUrl) {
                    if (updateUrl != null) {
                        showUpdateDialog = true
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
                                    0 -> WebViewScreen(this@MainActivity)
                                    1 -> DownloadsScreen(this@MainActivity)
                                    2 -> UpdatesScreen(
                                        context = this@MainActivity,
                                        latestVersion = newVersionName,
                                        onCheckUpdate = {
                                            checkUpdate(this@MainActivity) { url, version ->
                                                updateUrl = url
                                                newVersionName = version
                                            }
                                        }
                                    )
                                    3 -> AboutScreen()
                                }
                                
                                if (showUpdateDialog && updateUrl != null) {
                                    UpdateDialog(
                                        version = newVersionName,
                                        onDismiss = { showUpdateDialog = false },
                                        onConfirm = {
                                            descargarAPK(this@MainActivity, updateUrl!!)
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
            newVersionName = version
        }
    }

    private fun setupDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Toast.makeText(context, "Descarga finalizada", Toast.LENGTH_SHORT).show()
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                this,
                downloadReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
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
            icon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descargas") },
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
fun DownloadsScreen(context: Context) {
    var downloadedFiles by remember { 
        mutableStateOf(getDownloadedApks(context)) 
    }

    LaunchedEffect(Unit) {
        while(true) {
            downloadedFiles = getDownloadedApks(context)
            kotlinx.coroutines.delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Mis Descargas",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (downloadedFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No hay APKs descargados", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(downloadedFiles) { file ->
                    ApkItem(
                        file = file,
                        onInstall = { installApk(context, file) },
                        onDelete = {
                            file.delete()
                            downloadedFiles = getDownloadedApks(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ApkItem(file: File, onInstall: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${String.format("%.2f", file.length() / (1024f * 1024f))} MB",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row {
                IconButton(onClick = onInstall) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Instalar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

fun getDownloadedApks(context: Context): List<File> {
    val dir = context.getExternalFilesDir(null)
    return dir?.listFiles { file -> file.extension == "apk" }?.toList() ?: emptyList()
}

fun installApk(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Error al instalar: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun UpdatesScreen(context: Context, latestVersion: String, onCheckUpdate: () -> Unit) {
    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) { "Unknown" }
    }
    
    var isChecking by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Centro de Actualización",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Versión instalada:", fontWeight = FontWeight.Bold)
                        Text(currentVersion ?: "1.0.0")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Última disponible:", fontWeight = FontWeight.Bold)
                        Text(if (latestVersion.isEmpty()) "Desconocida" else "v$latestVersion")
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            
            Button(
                onClick = {
                    isChecking = true
                    onCheckUpdate()
                    Handler(Looper.getMainLooper()).postDelayed({
                        isChecking = false
                    }, 2000)
                },
                modifier = Modifier.height(52.dp).fillMaxWidth(),
                enabled = !isChecking
            ) {
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "StormStore",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Tu tienda de juegos favorita",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Desarrollado por:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "StormGamesStudios",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "© 2024 Todos los derechos reservados.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun UpdateDialog(version: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(text = "Actualización disponible") },
        text = {
            Column {
                Text(text = "Se ha encontrado una nueva versión: ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "v$version", 
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "¿Deseas descargarla e instalarla ahora?")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Actualizar ahora")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Más tarde")
            }
        }
    )
}

@Composable
fun RequestPermissions(onPermissionsGranted: @Composable () -> Unit) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
    }

    onPermissionsGranted()
}

enum class ScreenState {
    Loading,
    Content,
    Error
}

@Composable
fun WebViewScreen(context: Context) {
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    val currentState = when {
        isError -> ScreenState.Error
        isLoading -> ScreenState.Loading
        else -> ScreenState.Content
    }

    if (LocalInspectionMode.current) {
        SplashScreen()
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            key(reloadTrigger) {
                AndroidView(
                    factory = {
                        WebView(it).apply {
                            webView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    canGoBack = view?.canGoBack() == true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (!isError) {
                                        isLoading = false
                                    }
                                    canGoBack = view?.canGoBack() == true
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    canGoBack = view?.canGoBack() == true
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        isError = true
                                        isLoading = false
                                    }
                                }
                            }

                            setDownloadListener { url, _, _, _, _ ->
                                if (url.endsWith(".apk")) {
                                    descargarAPK(context, url)
                                }
                            }

                            loadUrl("https://stormstore.vercel.app")
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                )
            }

            AnimatedContent(
                targetState = currentState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
                },
                label = "StateTransition"
            ) { state ->
                when (state) {
                    ScreenState.Loading -> SplashScreen()
                    ScreenState.Error -> ErrorScreen(onRetry = {
                        isError = false
                        isLoading = true
                        reloadTrigger++
                    })
                    ScreenState.Content -> {
                        Spacer(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale)
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
        }
    }
}

@Composable
fun ErrorScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Sin conexión",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No se pudo conectar con la tienda. Revisa tu conexión a internet e inténtalo de nuevo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.height(52.dp)
            ) {
                Text("Reintentar", modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

fun descargarAPK(context: Context, url: String) {
    val fileName = url.substringAfterLast("/")
    val file = File(context.getExternalFilesDir(null), fileName)
    
    if (file.exists()) {
        file.delete()
    }

    val request = DownloadManager.Request(Uri.parse(url)).apply {
        setTitle("Descargando $fileName")
        setDescription("Descargando en carpeta privada de StormStore")
        setDestinationInExternalFilesDir(context, null, fileName)
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    }

    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(request)
}

fun checkUpdate(context: Context, onUpdateAvailable: (url: String, version: String) -> Unit) {
    Thread {
        try {
            val url = URL("https://api.github.com/repos/acierto-incomodo/stormstore-android/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "StormStore-App")
            
            val data = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(data)
            
            val latestTag = json.getString("tag_name")
            val latestVersion = latestTag.lowercase().removePrefix("v").trim()

            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            
            val currentVersion = pInfo.versionName?.lowercase()?.removePrefix("v")?.trim() ?: ""

            if (latestVersion.isNotEmpty() && latestVersion != currentVersion) {
                val assets = json.getJSONArray("assets")
                if (assets.length() > 0) {
                    val apkUrl = assets.getJSONObject(0).getString("browser_download_url")
                    onUpdateAvailable(apkUrl, latestVersion)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }.start()
}

@Preview(showBackground = true)
@Composable
fun WebViewScreenPreview() {
    StormStoreTheme {
        WebViewScreen(context = LocalContext.current)
    }
}

@Preview(showBackground = true)
@Composable
fun ErrorScreenPreview() {
    StormStoreTheme {
        ErrorScreen(onRetry = {})
    }
}
