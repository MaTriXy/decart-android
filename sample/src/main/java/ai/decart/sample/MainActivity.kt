package ai.decart.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import ai.decart.sdk.*
import ai.decart.sdk.realtime.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.webrtc.*

class MainActivity : ComponentActivity() {

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var client: RealTimeClient? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                initializeWebRTC()
                showUI()
            } else {
                Toast.makeText(this, "Camera and mic permissions required", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (hasCamera && hasMic) {
            initializeWebRTC()
            showUI()
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ))
        }
    }

    private fun initializeWebRTC() {
        eglBase = EglBase.create()
        val eglContext = eglBase!!.eglBaseContext

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
            .createPeerConnectionFactory()
    }

    private fun startCamera() {
        val factory = peerConnectionFactory ?: return
        val egl = eglBase ?: return

        val enumerator = Camera2Enumerator(this)
        val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: return

        val capturer = enumerator.createCapturer(deviceName, null)
        videoCapturer = capturer

        videoSource = factory.createVideoSource(capturer.isScreencast)
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", egl.eglBaseContext)
        capturer.initialize(surfaceTextureHelper, applicationContext, videoSource!!.capturerObserver)
        capturer.startCapture(1280, 720, 30)

        localVideoTrack = factory.createVideoTrack("local_video", videoSource)
        localVideoTrack?.setEnabled(true)
        localRenderer?.let { localVideoTrack?.addSink(it) }
    }

    private fun stopCamera() {
        localRenderer?.let { localVideoTrack?.removeSink(it) }
        localVideoTrack?.dispose()
        localVideoTrack = null
        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {}
        videoCapturer?.dispose()
        videoCapturer = null
        videoSource?.dispose()
        videoSource = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
    }

    private fun showUI() {
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                DecartSampleScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DecartSampleScreen() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        var apiKey by remember { mutableStateOf("your-api-key-here") }
        var prompt by remember { mutableStateOf("") }
        var enhancePrompt by remember { mutableStateOf(true) }
        var selectedModel by remember { mutableStateOf(RealtimeModels.LUCY_V2V_720P_RT) }
        var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
        var modelMenuExpanded by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf("Ready") }

        // Collect connection state from client
        val currentClient = client
        if (currentClient != null) {
            LaunchedEffect(currentClient) {
                currentClient.connectionState.collectLatest { state ->
                    connectionState = state
                    statusMessage = when (state) {
                        ConnectionState.DISCONNECTED -> "Disconnected"
                        ConnectionState.CONNECTING -> "Connecting..."
                        ConnectionState.CONNECTED -> "Connected"
                        ConnectionState.GENERATING -> "Generating"
                        ConnectionState.RECONNECTING -> "Reconnecting..."
                    }
                }
            }
            LaunchedEffect(currentClient) {
                currentClient.errors.collectLatest { error ->
                    statusMessage = "Error: ${error.message}"
                }
            }
        }

        val isConnected = connectionState == ConnectionState.CONNECTED ||
                connectionState == ConnectionState.GENERATING

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Decart SDK Sample") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Video views
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Local camera
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.DarkGray, RoundedCornerShape(8.dp))
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                SurfaceViewRenderer(ctx).also { renderer ->
                                    renderer.init(eglBase!!.eglBaseContext, null)
                                    renderer.setMirror(true)
                                    renderer.setEnableHardwareScaler(true)
                                    localRenderer = renderer
                                    // If camera already started, add sink
                                    localVideoTrack?.addSink(renderer)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            "Local",
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Remote AI output
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.DarkGray, RoundedCornerShape(8.dp))
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                SurfaceViewRenderer(ctx).also { renderer ->
                                    renderer.init(eglBase!!.eglBaseContext, null)
                                    renderer.setEnableHardwareScaler(true)
                                    remoteRenderer = renderer
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            "AI Output",
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // State chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val stateColor = when (connectionState) {
                        ConnectionState.CONNECTED, ConnectionState.GENERATING -> Color(0xFF4CAF50)
                        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Color(0xFFFFC107)
                        ConnectionState.DISCONNECTED -> Color(0xFFF44336)
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = stateColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            connectionState.name,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = stateColor,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Text(
                        statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Model selector
                ExposedDropdownMenuBox(
                    expanded = modelMenuExpanded,
                    onExpandedChange = { modelMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedModel.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false }
                    ) {
                        RealtimeModels.all.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.name) },
                                onClick = {
                                    selectedModel = model
                                    modelMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Prompt + enhance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Prompt") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    if (isConnected && prompt.isNotBlank()) {
                        Button(onClick = {
                            try {
                                client?.setPrompt(prompt, enhancePrompt)
                                statusMessage = "Prompt sent"
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.message}"
                            }
                        }) {
                            Text("Send")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enhance Prompt")
                    Switch(checked = enhancePrompt, onCheckedChange = { enhancePrompt = it })
                }

                // Connect/Disconnect
                Button(
                    onClick = {
                        if (isConnected || connectionState == ConnectionState.CONNECTING) {
                            // Disconnect
                            client?.disconnect()
                            client?.release()
                            client = null
                            stopCamera()
                            statusMessage = "Disconnected"
                            connectionState = ConnectionState.DISCONNECTED
                        } else {
                            if (apiKey.isBlank()) {
                                statusMessage = "Please enter an API key"
                                return@Button
                            }
                            statusMessage = "Starting camera..."
                            connectionState = ConnectionState.CONNECTING

                            coroutineScope.launch {
                                try {
                                    // Start camera
                                    startCamera()

                                    // Create client
                                    val rtClient = RealTimeClient(
                                        context = context,
                                        config = RealTimeClientConfig(
                                            apiKey = apiKey,
                                            baseUrl = "wss://api.decart.ai",
                                            logger = AndroidLogger(LogLevel.DEBUG)
                                        )
                                    )
                                    rtClient.initialize(eglBase)
                                    client = rtClient

                                    val initialPromptObj = if (prompt.isNotBlank()) {
                                        InitialPrompt(text = prompt, enhance = enhancePrompt)
                                    } else null

                                    val remote = remoteRenderer!!
                                    rtClient.connect(
                                        localVideoTrack = localVideoTrack,
                                        options = ConnectOptions(
                                            model = selectedModel,
                                            onRemoteVideoTrack = { track ->
                                                track.addSink(remote)
                                            },
                                            initialPrompt = initialPromptObj
                                        )
                                    )
                                    statusMessage = "Connected!"
                                } catch (e: Exception) {
                                    statusMessage = "Failed: ${e.message}"
                                    connectionState = ConnectionState.DISCONNECTED
                                    client?.release()
                                    client = null
                                    stopCamera()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected || connectionState == ConnectionState.CONNECTING)
                            Color(0xFFF44336) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        if (isConnected || connectionState == ConnectionState.CONNECTING)
                            "Disconnect" else "Connect"
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.disconnect()
        client?.release()
        client = null
        stopCamera()
        localRenderer?.release()
        remoteRenderer?.release()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        eglBase?.release()
        eglBase = null
    }
}
