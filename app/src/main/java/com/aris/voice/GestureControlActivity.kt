package com.aris.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.ProgressBar
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.io.FileOutputStream
import java.io.InputStream

class GestureControlActivity : BaseNavigationActivity() {

    private lateinit var holoWorkspace: HoloWorkspaceView
    private lateinit var txtTelemetry: TextView
    private lateinit var txtGestureAction: TextView
    private lateinit var txtStatusBadge: TextView
    private lateinit var txtCameraDesc: TextView
    private lateinit var btnSetupCamera: View
    private lateinit var btnSetupCameraText: TextView
    private lateinit var seekSensitivity: SeekBar
    private lateinit var txtSensitivityVal: TextView
    private lateinit var seekSmoothing: SeekBar
    private lateinit var txtSmoothingVal: TextView
    private lateinit var seekDwellTime: SeekBar
    private lateinit var txtDwellTimeVal: TextView
    private lateinit var btnSimDoubleTap: TextView
    private lateinit var btnSimSwipe: TextView
    private lateinit var btnSimTwoHandZoom: TextView
    private lateinit var btnSimRotate: TextView
    private lateinit var switchSystemOverlay: SwitchCompat
    private lateinit var cardCameraPreview: androidx.cardview.widget.CardView
    private lateinit var cameraPreviewView: androidx.camera.view.PreviewView

    // Model Download UI References
    private lateinit var txtModelStatusBadge: TextView
    private lateinit var txtModelDesc: TextView
    private lateinit var layoutDownloadProgress: View
    private lateinit var pbDownload: ProgressBar
    private lateinit var txtDownloadProgress: TextView
    private lateinit var btnDownloadModel: View
    private lateinit var btnDownloadModelText: TextView

    // CameraX & MediaPipe states
    private var cameraProvider: androidx.camera.lifecycle.ProcessCameraProvider? = null
    private var handLandmarker: com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker? = null
    private var backgroundExecutor: ExecutorService? = null
    private var isCameraActive = false

    // Jitter reduction filtering (Low-pass smoothing filters)
    private var filteredX = 0f
    private var filteredY = 0f
    private var smoothingFactor = 0.65f

    // Pinch-to-tap State Managers
    private var wasPinching = false
    private var isPinching = false
    private var lastPinchTime = 0L
    private var pinchStartPoint = android.graphics.PointF()

    // Flying Cursor Overlay layout parameters
    private var floatingCursorView: View? = null
    private var windowManager: WindowManager? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    override fun getContentLayoutId(): Int = R.layout.activity_gesture_control
    override fun getCurrentNavItem(): NavItem = NavItem.AIR_GESTURES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gesture_control)

        initViews()
        setupListeners()
        updateCameraStatus()
        checkModelStatus()

        // Handle permissions and automatic flow check
        if (hasCameraPermission()) {
            val modelFile = java.io.File(filesDir, "hand_landmarker.task")
            if (modelFile.exists()) {
                startCameraPreview()
            }
        }
    }

    private fun initViews() {
        holoWorkspace = findViewById(R.id.holo_workspace)
        txtTelemetry = findViewById(R.id.txt_coordinate_telemetry)
        txtGestureAction = findViewById(R.id.txt_gesture_action_telemetry)
        txtStatusBadge = findViewById(R.id.txt_camera_status_badge)
        txtCameraDesc = findViewById(R.id.txt_camera_desc)
        btnSetupCamera = findViewById(R.id.btn_setup_camera)
        btnSetupCameraText = findViewById(R.id.btn_setup_camera_text)
        seekSensitivity = findViewById(R.id.seekbar_sensitivity)
        txtSensitivityVal = findViewById(R.id.txt_sensitivity_val)
        seekSmoothing = findViewById(R.id.seekbar_smoothing)
        txtSmoothingVal = findViewById(R.id.txt_smoothing_val)
        seekDwellTime = findViewById(R.id.seekbar_dwell_time)
        txtDwellTimeVal = findViewById(R.id.txt_dwell_time_val)
        btnSimDoubleTap = findViewById(R.id.btn_sim_double_tap)
        btnSimSwipe = findViewById(R.id.btn_sim_swipe)
        btnSimTwoHandZoom = findViewById(R.id.btn_sim_two_hand_zoom)
        btnSimRotate = findViewById(R.id.btn_sim_rotate)
        switchSystemOverlay = findViewById(R.id.switch_system_overlay)
        cardCameraPreview = findViewById(R.id.card_camera_preview)
        cameraPreviewView = findViewById(R.id.camera_preview_view)

        // Bind Model download references
        txtModelStatusBadge = findViewById(R.id.txt_model_status_badge)
        txtModelDesc = findViewById(R.id.txt_model_desc)
        layoutDownloadProgress = findViewById(R.id.layout_download_progress)
        pbDownload = findViewById(R.id.pb_download)
        txtDownloadProgress = findViewById(R.id.txt_download_progress)
        btnDownloadModel = findViewById(R.id.btn_download_model)
        btnDownloadModelText = findViewById(R.id.btn_download_model_text)
    }

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    private fun setupListeners() {
        btnDownloadModel.setOnClickListener {
            startModelDownload()
        }

        btnSetupCamera.setOnClickListener {
            if (!hasCameraPermission()) {
                requestCameraPermission()
            } else {
                if (isCameraActive) {
                    stopCameraPreview()
                } else {
                    startCameraPreview()
                }
            }
        }

        // Seekbar listeners
        seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                txtSensitivityVal.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Seekbar smoothing
        seekSmoothing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val factor = 0.05f + (progress / 100f)
                smoothingFactor = factor
                txtSmoothingVal.text = String.format("%.2f", factor)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Seekbar dwells
        seekDwellTime.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                txtDwellTimeVal.text = "${progress}ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Simulator falls
        btnSimDoubleTap.setOnClickListener {
            txtGestureAction.text = "ACTION: AIR CLICK (DOUBLE TAP)"
            holoWorkspace.triggerAirClick(holoWorkspace.pointerX, holoWorkspace.pointerY)
            val accessibilityService = ScreenInteractionService.instance
            if (accessibilityService != null) {
                accessibilityService.clickOnPoint(holoWorkspace.pointerX, holoWorkspace.pointerY)
                Toast.makeText(this, "Air Click at (${holoWorkspace.pointerX.toInt()}, ${holoWorkspace.pointerY.toInt()})", Toast.LENGTH_SHORT).show()
            }
        }

        btnSimSwipe.setOnClickListener {
            txtGestureAction.text = "ACTION: SWIPING MATRIX FEED"
            holoWorkspace.simulateRotationSwipe()
            val accessibilityService = ScreenInteractionService.instance
            if (accessibilityService != null) {
                accessibilityService.swipe(100f, 800f, 900f, 800f, 300L)
            }
        }

        btnSimTwoHandZoom.setOnClickListener {
            txtGestureAction.text = "ACTION: PINCH ZOOM (TWO HANDS)"
            holoWorkspace.activeHandCount = 2
            holoWorkspace.simulatePinchScale()
            holoWorkspace.pointerX = holoWorkspace.width / 2f
            holoWorkspace.pointerY = holoWorkspace.height / 2f
            Handler(Looper.getMainLooper()).postDelayed({
                holoWorkspace.activeHandCount = 1
                txtGestureAction.text = "READY (Hover index finger tip)"
            }, 1200)
        }

        btnSimRotate.setOnClickListener {
            txtGestureAction.text = "ACTION: ROTATING PROJECT MODEL"
            holoWorkspace.simulateRotationSwipe()
        }

        // Overlays
        switchSystemOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hasOverlayPermission()) {
                    showOverlayCursor()
                } else {
                    switchSystemOverlay.isChecked = false
                    requestOverlayPermission()
                }
            } else {
                hideOverlayCursor()
            }
        }

        // Touch listener coordinates
        holoWorkspace.setOnTouchListener { v, event ->
            v.onTouchEvent(event)
            txtTelemetry.text = "[X: ${event.x.toInt()}, Y: ${event.y.toInt()}, Z: ${holoWorkspace.pointerZ.toInt()}]"
            updateFloatingCursorPosition(event.x, event.y)
            true
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, 1002)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1002) {
            if (hasOverlayPermission()) {
                switchSystemOverlay.isChecked = true
                showOverlayCursor()
            } else {
                Toast.makeText(this, "Permission to overlay over system was denied.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview()
        } else {
            Toast.makeText(this, "Camera permission denied. Manual simulation active.", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateCameraStatus() {
        if (hasCameraPermission()) {
            if (isCameraActive) {
                txtStatusBadge.text = "ACTIVE SENSING"
                txtStatusBadge.setTextColor(Color.parseColor("#00FF55"))
                txtStatusBadge.setBackgroundResource(R.drawable.status_background_granted)
                btnSetupCameraText.text = "TERMINATE CAMERA FEED"
                btnSetupCamera.setBackgroundColor(Color.parseColor("#FF3D00"))
                txtCameraDesc.text = "Neural front camera sensing active. Face the screen and bring 1 or 2 hands samne to take absolute real-time spatial air control!"
            } else {
                txtStatusBadge.text = "STANDBY"
                txtStatusBadge.setTextColor(Color.parseColor("#FFD600"))
                txtStatusBadge.setBackgroundResource(R.drawable.rounded_button_bg)
                btnSetupCameraText.text = "ENGAGE CAMERA SPATIAL STREAM"
                btnSetupCamera.setBackgroundColor(Color.parseColor("#00C4FF"))
                txtCameraDesc.text = "Camera access granted. Click above to engage high-fidelity real-time neural finger/hands sensing."
            }
        } else {
            txtStatusBadge.text = "DISCONNECTED"
            txtStatusBadge.setTextColor(Color.parseColor("#FF4A4A"))
            txtStatusBadge.setBackgroundResource(R.drawable.status_background_denied)
            btnSetupCameraText.text = "INITIALIZE SPATIAL CAMERA"
            btnSetupCamera.setBackgroundColor(Color.parseColor("#00C4FF"))
            txtCameraDesc.text = "Camera permission requested to map fingers to floating screen space."
        }
    }

    private fun checkModelStatus() {
        val modelFile = java.io.File(filesDir, "hand_landmarker.task")
        if (modelFile.exists()) {
            txtModelStatusBadge.text = "INSTALLED"
            txtModelStatusBadge.setTextColor(Color.parseColor("#00FF55"))
            txtModelStatusBadge.setBackgroundResource(R.drawable.status_background_granted)
            txtModelDesc.text = "The 24MB neural model is successfully installed on your device. Ready for 0-delay local hand tracking!"
            btnDownloadModelText.text = "RE-DOWNLOAD NEURAL MODEL"
            btnDownloadModel.setBackgroundResource(R.drawable.rounded_button_secondary)
            btnDownloadModel.setBackgroundColor(Color.parseColor("#1E293B"))
            layoutDownloadProgress.visibility = View.GONE
        } else {
            txtModelStatusBadge.text = "NOT INSTALLED"
            txtModelStatusBadge.setTextColor(Color.parseColor("#FF4A4A"))
            txtModelStatusBadge.setBackgroundResource(R.drawable.status_background_denied)
            txtModelDesc.text = "The gesture control engine requires Google's official public 24 MB hand landmark file to map finger coordinates locally on device."
            btnDownloadModelText.text = "DOWNLOAD NEURAL MODEL (24 MB)"
            btnDownloadModel.setBackgroundResource(R.drawable.rounded_button_bg)
            btnDownloadModel.setBackgroundColor(Color.parseColor("#15803D"))
            layoutDownloadProgress.visibility = View.GONE
        }
    }

    private fun startModelDownload() {
        val modelUrl = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
        val destFile = java.io.File(filesDir, "hand_landmarker.task")

        btnDownloadModel.isEnabled = false
        btnDownloadModelText.text = "DOWNLOADING..."
        layoutDownloadProgress.visibility = View.VISIBLE
        pbDownload.progress = 0
        pbDownload.isIndeterminate = true
        txtDownloadProgress.text = "Establishing connection with Google Cloud servers..."

        Executors.newSingleThreadExecutor().execute {
            var connection: java.net.HttpURLConnection? = null
            var input: java.io.InputStream? = null
            var output: java.io.FileOutputStream? = null
            try {
                val url = java.net.URL(modelUrl)
                connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.connect()

                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("Google server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
                }

                val fileLength = connection.contentLength
                input = connection.inputStream
                output = java.io.FileOutputStream(destFile)

                val data = ByteArray(8192)
                var total: Long = 0
                var count: Int
                var lastUpdate = 0L

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    output.write(data, 0, count)

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdate > 100) {
                        lastUpdate = currentTime
                        val progressPercent = if (fileLength > 0) (total * 100 / fileLength).toInt() else -1
                        val mbDownloaded = total / (1024f * 1024f)
                        val mbTotal = fileLength / (1024f * 1024f)
                        runOnUiThread {
                            if (progressPercent >= 0) {
                                pbDownload.isIndeterminate = false
                                pbDownload.progress = progressPercent
                                txtDownloadProgress.text = String.format(java.util.Locale.US, "Downloading: %.1f MB / %.1f MB (%d%%)", mbDownloaded, mbTotal, progressPercent)
                            } else {
                                pbDownload.isIndeterminate = true
                                txtDownloadProgress.text = String.format(java.util.Locale.US, "Downloading: %.1f MB", mbDownloaded)
                            }
                        }
                    }
                }

                output.flush()
                runOnUiThread {
                    btnDownloadModel.isEnabled = true
                    Toast.makeText(this, "Neural model successfully downloaded!", Toast.LENGTH_SHORT).show()
                    checkModelStatus()
                }
            } catch (e: Exception) {
                if (destFile.exists()) {
                    destFile.delete()
                }
                runOnUiThread {
                    btnDownloadModel.isEnabled = true
                    Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                    checkModelStatus()
                }
            } finally {
                try { output?.close() } catch (ignored: Exception) {}
                try { input?.close() } catch (ignored: Exception) {}
                connection?.disconnect()
            }
        }
    }

    private fun initHandLandmarker() {
        if (handLandmarker != null) return

        val modelFile = java.io.File(filesDir, "hand_landmarker.task")
        if (!modelFile.exists()) {
            runOnUiThread {
                Toast.makeText(this, "Neural model is missing! Please download it first.", Toast.LENGTH_LONG).show()
                stopCameraPreview()
            }
            return
        }

        backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor?.execute {
            try {
                var buffer: java.nio.ByteBuffer? = null
                java.io.FileInputStream(modelFile).use { fis ->
                    val channel = fis.channel
                    buffer = channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, channel.size())
                }

                val baseOptionsBuilder = com.google.mediapipe.tasks.core.BaseOptions.builder()
                    .setModelAssetBuffer(buffer!!)
                    .setDelegate(com.google.mediapipe.tasks.core.Delegate.GPU)

                val optionsBuilder = com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setMinHandDetectionConfidence(0.7f)
                    .setMinHandPresenceConfidence(0.7f)
                    .setMinTrackingConfidence(0.6f)
                    .setNumHands(2) // support 2 hands mode!
                    .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.LIVE_STREAM)
                    .setResultListener { result, _ ->
                        processHandLandmarkerResult(result)
                    }
                    .setErrorListener { error ->
                        Log.e("GestureActivity", "MediaPipe model exception: ${error.message}")
                    }

                handLandmarker = com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.createFromOptions(this, optionsBuilder.build())
                Log.d("GestureActivity", "Neural MediaPipe model fully loaded from device files.")
            } catch (e: Exception) {
                Log.e("GestureActivity", "Failed to build MediaPipe engine", e)
                runOnUiThread {
                    Toast.makeText(this, "Neural engine failed to load: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCameraPreview() {
        val modelFile = java.io.File(filesDir, "hand_landmarker.task")
        if (!modelFile.exists()) {
            Toast.makeText(this, "Neural model is missing! Please download it first.", Toast.LENGTH_LONG).show()
            isCameraActive = false
            updateCameraStatus()
            return
        }

        initHandLandmarker()

        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                if (!hasCameraPermission()) {
                    requestCameraPermission()
                    return@addListener
                }

                cardCameraPreview.visibility = View.VISIBLE

                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(cameraPreviewView.surfaceProvider)
                }

                val imageAnalyzer = androidx.camera.core.ImageAnalysis.Builder()
                    .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(backgroundExecutor ?: Executors.newSingleThreadExecutor()) { imageProxy ->
                            analyzeImageFrame(imageProxy)
                        }
                    }

                val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

                isCameraActive = true
                updateCameraStatus()

            } catch (e: Exception) {
                Log.e("GestureActivity", "CameraX binding error", e)
                isCameraActive = false
                updateCameraStatus()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCameraPreview() {
        try {
            cameraProvider?.unbindAll()
            isCameraActive = false
            cardCameraPreview.visibility = View.GONE
            updateCameraStatus()
            holoWorkspace.detectedHands = emptyList()
            holoWorkspace.activeHandCount = 0
            holoWorkspace.invalidate()
        } catch (e: Exception) {
            Log.e("GestureActivity", "Teardown helper failed", e)
        }
    }

    private fun analyzeImageFrame(imageProxy: androidx.camera.core.ImageProxy) {
        val landmarker = handLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            
            // Mirror image coordinates so it matches the direction of user movements naturalmente!
            val matrix = android.graphics.Matrix().apply {
                postRotate(rotationDegrees.toFloat())
                postScale(-1f, 1f)
            }
            val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(rotatedBitmap).build()
            val timestampMs = SystemClock.uptimeMillis()
            landmarker.detectAsync(mpImage, timestampMs)

        } catch (e: Exception) {
            Log.e("GestureActivity", "Image analyzer pipeline exception", e)
        } finally {
            imageProxy.close()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun processHandLandmarkerResult(result: com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult) {
        val hasHands = result.landmarks().isNotEmpty()

        runOnUiThread {
            if (!hasHands) {
                holoWorkspace.detectedHands = emptyList()
                holoWorkspace.activeHandCount = 0
                txtTelemetry.text = "[STANDBY // SCANNING FRONT SPACE]"
                txtGestureAction.text = "HUD STATUS: STANDBY // SCANNING"
                holoWorkspace.invalidate()
                return@runOnUiThread
            }

            val handsCount = result.landmarks().size
            holoWorkspace.activeHandCount = handsCount

            val customHands = result.landmarks().map { landmarksList ->
                landmarksList.map { landmark ->
                    HoloWorkspaceView.HandLandmark(landmark.x(), landmark.y(), landmark.z())
                }
            }
            holoWorkspace.detectedHands = customHands

            if (handsCount == 1) {
                // SINGLE HAND MODE
                txtGestureAction.text = "HUD STATUS: SINGLE HAND // CURSOR MODE"

                val hand = result.landmarks()[0]
                if (hand.size > 8) {
                    val indexTip = hand[8]
                    val thumbTip = hand[4]

                    val targetX = indexTip.x() * holoWorkspace.width
                    val targetY = indexTip.y() * holoWorkspace.height

                    // Smooth filters
                    filteredX = filteredX + smoothingFactor * (targetX - filteredX)
                    filteredY = filteredY + smoothingFactor * (targetY - filteredY)

                    holoWorkspace.pointerX = filteredX
                    holoWorkspace.pointerY = filteredY

                    val wrist = hand[0]
                    val pinkyRoot = hand[17]
                    val handWidth = Math.sqrt(
                        Math.pow((pinkyRoot.x() - wrist.x()).toDouble(), 2.0) +
                                Math.pow((pinkyRoot.y() - wrist.y()).toDouble(), 2.0)
                    ).toFloat()
                    val distZ = (handWidth * 1000f).coerceIn(100f, 400f)
                    holoWorkspace.pointerZ = distZ

                    txtTelemetry.text = "[X: ${filteredX.toInt()}, Y: ${filteredY.toInt()}, Z: ${distZ.toInt()}]"
                    updateFloatingCursorPosition(filteredX, filteredY)

                    // REAL HAND GESTURES DECIDER: PINCH DETECT (Apple Vision proximity threshold)
                    val dx = indexTip.x() - thumbTip.x()
                    val dy = indexTip.y() - thumbTip.y()
                    val dz = indexTip.z() - thumbTip.z()
                    val pinchDist = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()

                    val targetSensitivity = seekSensitivity.progress / 100f
                    val basePinchThreshold = 0.055f
                    val adaptivePinchThreshold = basePinchThreshold * (1f + (targetSensitivity - 0.75f) * 0.3f)

                    isPinching = pinchDist < adaptivePinchThreshold

                    if (isPinching && !wasPinching) {
                        wasPinching = true
                        lastPinchTime = System.currentTimeMillis()
                        pinchStartPoint.set(filteredX, filteredY)

                        txtGestureAction.text = "ACTION: AIR CLICK PINCH DETECTED"
                        holoWorkspace.triggerAirClick(filteredX, filteredY)

                        val accessibilityService = ScreenInteractionService.instance
                        if (accessibilityService != null) {
                            accessibilityService.clickOnPoint(filteredX, filteredY)
                        }
                    } else if (!isPinching && wasPinching) {
                        wasPinching = false
                        val dragX = filteredX - pinchStartPoint.x
                        val dragY = filteredY - pinchStartPoint.y
                        val dragDist = Math.sqrt((dragX * dragX + dragY * dragY).toDouble()).toFloat()

                        if (dragDist > 120f) {
                            txtGestureAction.text = "ACTION: REAL SWIPE SIGNALLING"
                            holoWorkspace.simulateRotationSwipe()

                            val accessibilityService = ScreenInteractionService.instance
                            if (accessibilityService != null) {
                                val dwellDuration = seekDwellTime.progress.toLong().coerceIn(150L, 1000L)
                                accessibilityService.swipe(
                                    pinchStartPoint.x, pinchStartPoint.y,
                                    filteredX, filteredY,
                                    dwellDuration
                                )
                            }
                        } else {
                            txtGestureAction.text = "ACTION: POINTER HOVER CLICKS SUCCESS"
                        }
                    }
                }
            } else if (handsCount >= 2) {
                // TWO HANDS MODE
                txtGestureAction.text = "HUD STATUS: DOENATH DETECTED // TWO HANDS MULTITOUCH"

                val hand1 = result.landmarks()[0]
                val hand2 = result.landmarks()[1]

                if (hand1.size > 8 && hand2.size > 8) {
                    val index1 = hand1[8]
                    val index2 = hand2[8]

                    val pt1X = index1.x() * holoWorkspace.width
                    val pt1Y = index1.y() * holoWorkspace.height
                    val pt2X = index2.x() * holoWorkspace.width
                    val pt2Y = index2.y() * holoWorkspace.height

                    val midX = (pt1X + pt2X) / 2f
                    val midY = (pt1Y + pt2Y) / 2f

                    holoWorkspace.pointerX = midX
                    holoWorkspace.pointerY = midY

                    val distanceValue = Math.sqrt(
                        Math.pow((pt2X - pt1X).toDouble(), 2.0) +
                                Math.pow((pt2Y - pt1Y).toDouble(), 2.0)
                    ).toFloat()

                    val naturalBoundsMax = 600f
                    val freshScale = (distanceValue / naturalBoundsMax).coerceIn(0.5f, 2.5f)
                    holoWorkspace.scaleFactor = freshScale

                    val slopeAngle = Math.atan2((pt2Y - pt1Y).toDouble(), (pt2X - pt1X).toDouble())
                    val angleInDegrees = Math.toDegrees(slopeAngle).toFloat()
                    holoWorkspace.holoRotationY = angleInDegrees

                    txtTelemetry.text = "[2HANDS // SCALE: ${String.format("%.2f", freshScale)}x // ROTATE: ${angleInDegrees.toInt()}°]"
                    updateFloatingCursorPosition(midX, midY)
                }
            }
            holoWorkspace.invalidate()
        }
    }

    private fun showOverlayCursor() {
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            floatingCursorView = View(this).apply {
                val size = (36 * resources.displayMetrics.density).toInt()
                layoutParams = ViewGroup.LayoutParams(size, size)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                setBackground(object : android.graphics.drawable.Drawable() {
                    override fun draw(canvas: android.graphics.Canvas) {
                        val cx = bounds.centerX().toFloat()
                        val cy = bounds.centerY().toFloat()
                        val r = bounds.width() / 2f

                        paint.color = Color.parseColor("#3300FFDE")
                        paint.style = android.graphics.Paint.Style.FILL
                        canvas.drawCircle(cx, cy, r - 6f, paint)

                        paint.color = Color.parseColor("#00FFDE")
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeWidth = 4f
                        canvas.drawCircle(cx, cy, r - 6f, paint)

                        paint.color = Color.parseColor("#FF0055")
                        paint.style = android.graphics.Paint.Style.FILL
                        canvas.drawCircle(cx, cy, 6f, paint)
                    }

                    override fun setAlpha(alpha: Int) {}
                    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
                })
            }

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            overlayParams = WindowManager.LayoutParams(
                (36 * resources.displayMetrics.density).toInt(),
                (36 * resources.displayMetrics.density).toInt(),
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 100
            }

            windowManager?.addView(floatingCursorView, overlayParams)
            Toast.makeText(this, "Air Mouse Overlay activated successfully.", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("GestureActivity", "Failed to initiate overlay context", e)
            Toast.makeText(this, "System overlay permission required.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideOverlayCursor() {
        if (floatingCursorView != null && windowManager != null) {
            try {
                windowManager?.removeView(floatingCursorView)
            } catch (e: Exception) {
                Log.e("GestureActivity", "Teardown cursor error", e)
            }
            floatingCursorView = null
        }
    }

    private fun updateFloatingCursorPosition(x: Float, y: Float) {
        val flView = floatingCursorView ?: return
        val wm = windowManager ?: return
        val params = overlayParams ?: return
        try {
            val displayMetrics = resources.displayMetrics
            val realScreenX = (x / holoWorkspace.width) * displayMetrics.widthPixels
            val realScreenY = (y / holoWorkspace.height) * displayMetrics.heightPixels

            params.x = (realScreenX - flView.width / 2f).toInt()
            params.y = (realScreenY - flView.height / 2f).toInt()

            wm.updateViewLayout(flView, params)
        } catch (e: Exception) {
            Log.e("GestureActivity", "Position tracking mapping error", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCameraPreview()
        hideOverlayCursor()
        holoWorkspace.stopAnimations()

        try {
            handLandmarker?.close()
            handLandmarker = null
        } catch (e: Exception) {
            Log.e("GestureActivity", "Error closing hand landmarker", e)
        }

        try {
            backgroundExecutor?.shutdown()
            backgroundExecutor = null
        } catch (e: Exception) {
            Log.e("GestureActivity", "Error shutting down background executor", e)
        }
    }
}
