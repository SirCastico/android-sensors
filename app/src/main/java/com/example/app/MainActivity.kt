package com.example.app

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.app.ui.theme.AppTheme
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

import android.media.Image
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import java.io.ByteArrayOutputStream

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableIntStateOf
import com.google.ar.core.Frame
import kotlin.math.abs

class MainActivity : ComponentActivity(), GLSurfaceView.Renderer{
        private val tag = "MainActivity"

        private lateinit var mInfo: String
        private var mUserRequestedInstall = true
        private var mSession: Session? = null
        var mShouldWrite = AtomicBoolean(false)
        private lateinit var mDisplayRotationHelper: DisplayRotationHelper

        var currentFrameIndex by mutableIntStateOf(0)
        var maxFrames by mutableIntStateOf(10)
        var fps by mutableIntStateOf(30) // Default to 30 FPS


        var isCapturing = false
        private val handler = Handler(Looper.getMainLooper())

        // Variable to hold the last captured image
        var lastCapturedImage by mutableStateOf<Bitmap?>(null)

        // Variable to hold the last captured depth image
        var lastCapturedDepthImage: Bitmap? by mutableStateOf(null)

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            mInfo = if (ArCoreApk.getInstance().checkAvailability(this).isSupported){
                "arcore supported"
            } else {
                "arcore not supported"
            }
            mDisplayRotationHelper = DisplayRotationHelper(this)

            enableEdgeToEdge()
            setContent {
                AppTheme {
                    CaptureScreen(this)
                }
            }


        }

        override fun onResume() {
            super.onResume()
            Log.d(tag, "onResume: Starting onResume process")

            // Update display rotation helper state
            mDisplayRotationHelper.onResume()

            // Check for camera permission
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
                Log.d(tag, "onResume: Camera permission denied. Requesting permission.")
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 0)
                return
            }

            // Check and initialize AR session if needed
            if (mSession == null) {
                Log.d(tag, "onResume: AR session is null, attempting to initialize ARCore session")
                try {
                    when (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                        ArCoreApk.InstallStatus.INSTALLED -> {
                            Log.d(tag, "onResume: ARCore is installed. Creating AR session.")
                            val session = Session(this)
                            val config = session.config

                            // Enable autofocus
                            config.focusMode = Config.FocusMode.AUTO
                            Log.d(tag, "onResume: Autofocus enabled")

                            // Check and enable depth mode if supported
                            if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                                config.depthMode = Config.DepthMode.AUTOMATIC
                                Log.d(tag, "onResume: Depth mode set to AUTOMATIC")
                            } else {
                                Log.e(tag, "onResume: Depth mode AUTOMATIC is not supported on this device")
                            }

                            // Configure session with updated settings
                            session.configure(config)
                            mSession = session
                            Log.d(tag, "onResume: AR session created and configured successfully")
                        }
                        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                            Log.d(tag, "onResume: ARCore installation requested. User prompted to install/update ARCore.")
                            mUserRequestedInstall = false
                            return
                        }
                    }
                } catch (e: UnavailableUserDeclinedInstallationException) {
                    Log.e(tag, "onResume: ARCore installation was declined by the user", e)
                    return
                } catch (e: Exception) {
                    Log.e(tag, "onResume: Error while creating AR session: ${e.message}", e)
                    return
                }
            }

            // Resume the AR session if it exists
            if (mSession == null) {
                Log.d(tag, "onResume: Session is still null after initialization attempt")
            } else {
                Log.d(tag, "onResume: Resuming existing AR session")
                mSession?.resume()

                // Set content and UI if the session is valid
                setContent {
                    AppTheme {
                        CaptureScreen(this)
                    }
                }
                Log.d(tag, "onResume: UI content set with CaptureScreen")
            }
        }

        override fun onStop() {
            super.onStop()
            mSession?.pause()
            mDisplayRotationHelper.onPause()
        }

        override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
            GLES20.glClearColor(0.1f,0.1f,0.1f,1.0f)
            val texArr = IntArray(1)
            GLES20.glGenTextures(1, texArr, 0)
            mSession?.setCameraTextureName(texArr[0])
        }

        override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
            mDisplayRotationHelper.onSurfaceChanged(width, height)
            GLES20.glViewport(0,0,width,height)
        }

        override fun onDrawFrame(unused: GL10) {
            mSession?.let { session ->
                mDisplayRotationHelper.updateSessionIfNeeded(session)
                val frame = session.update()
                val camera = frame.camera

                if (camera.trackingState != TrackingState.TRACKING) return

                if (mShouldWrite.get()) {
                    mShouldWrite.set(false)
                    try {
                        captureImage(frame)
                        captureDepthImage(frame)

                        currentFrameIndex = (currentFrameIndex + 1) % maxFrames

                    } catch (e: NotYetAvailableException) {
                        Log.e(tag, "Required data not yet available: ${e.message}")
                    } catch (e: Exception) {
                        Log.e(tag, "Error saving RGB and depth data: ${e.message}")
                    }
                }
            }
        }


        // --------------------- IMAGE ------------------------
        private fun captureImage(frame: Frame) {
            // Define file names with the current index for cycling
            val rgbFileName = "camera_image_$currentFrameIndex.png"

            // Capture and save only the RGB image
            frame.acquireCameraImage().use { image ->
                val rgbBitmap = imageToBitmap(image)

                // Check if the phone is held in portrait or landscape mode
                val orientationDegrees = getDeviceRotationDegrees()

                // Rotate the image if necessary to match the device's orientation
                val rotatedBitmap = if (orientationDegrees == 90 || orientationDegrees == 270) {
                    // If the device is in portrait orientation, rotate to match it
                    rotateBitmap(rgbBitmap)
                } else {
                    rgbBitmap
                }

                // Update lastCapturedImage for UI display
                lastCapturedImage = rotatedBitmap
            }
        }

        // Convert ARCore YUV image to Bitmap
        private fun imageToBitmap(image: Image): Bitmap {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
            val jpegByteArray = out.toByteArray()
            return BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.size)
        }

        // --------------------- DEPTH ------------------------
        private fun captureDepthImage(frame: Frame) {
            frame.acquireRawDepthImage16Bits()?.use { depthImage ->
                lastCapturedDepthImage = rotateBitmap(convertRawDepthImageToBitmap(depthImage))
            }
        }


    private fun convertRawDepthImageToBitmap(depthImage: Image): Bitmap {
        val width = depthImage.width
        val height = depthImage.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val depthBuffer = depthImage.planes[0].buffer.asShortBuffer()
        val maxDisplayDepth = 10000f // Assuming 10 meters as max displayable range

        for (y in 0 until height) {
            for (x in 0 until width) {
                val depth = depthBuffer.get(y * width + x).toFloat()

                // Direct mapping: 0 depth = black, maxDisplayDepth = white
                val intensity = ((depth / maxDisplayDepth) * 255).toInt().coerceIn(0, 255)
                bitmap.setPixel(x, y, android.graphics.Color.rgb(intensity, intensity, intensity))
            }
        }

        var minDepth = Float.MAX_VALUE
        var maxDepth = Float.MIN_VALUE

        // Iterate over the depth buffer and print values
        for (i in 0 until depthBuffer.limit()) {
            val depth = depthBuffer.get(i).toFloat()
            if (depth > 0) { // Ignore zero (no data)
                if (depth < minDepth) minDepth = depth
                if (depth > maxDepth) maxDepth = depth
            }

            // Log every 1000th value for analysis
            if (i % 1000 == 0) {
                Log.d("DepthData", "Depth value at index $i: $depth")
            }
        }

        Log.d("DepthData", "Min depth: $minDepth, Max depth: $maxDepth")
        return bitmap
    }




    // --------------------- SAVE ------------------------
        // Save Bitmap as PNG file
        private fun saveBitmapAsPng(bitmap: Bitmap, fileName: String) {
            openFileOutput(fileName, Context.MODE_PRIVATE).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        }

        // -------------------- PREVIEW -----------------------
        fun startAutomaticCapture() {
            isCapturing = true
            handler.post(captureRunnable)
        }

        fun stopAutomaticCapture() {
            isCapturing = false
            handler.removeCallbacks(captureRunnable)
        }

        private val captureRunnable = object : Runnable {
            override fun run() {
                if (isCapturing) {
                    mShouldWrite.set(true)
                    val delayMillis = (1000 / fps).toLong() // Convert FPS to milliseconds
                    handler.postDelayed(this, delayMillis)
                }
            }
        }

        fun takeSnapshot() {
            if (isCapturing) stopAutomaticCapture()  // Stop auto-capture if it's running
            mShouldWrite.set(true)
            if (isCapturing) startAutomaticCapture()  // Resume auto-capture if needed
        }

        private fun getDeviceRotationDegrees(): Int {
            return when (resources.configuration.orientation) {
                Configuration.ORIENTATION_PORTRAIT -> 90
                Configuration.ORIENTATION_LANDSCAPE -> 0
                else -> 0
            }
        }

        private fun rotateBitmap(bitmap: Bitmap): Bitmap {
            val matrix = android.graphics.Matrix().apply { postRotate(90.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

    }

@Composable
fun CaptureScreen(main: MainActivity) {
    var isCapturing by remember { mutableStateOf(main.isCapturing) }
    val lastCapturedImage = main.lastCapturedImage
    val lastCapturedDepthImage = main.lastCapturedDepthImage
    val frameCounter = "${main.currentFrameIndex + 1}/${main.maxFrames}"

    // State for toggling display between RGB and Depth
    var isDisplayingDepth by remember { mutableStateOf(false) }

    LaunchedEffect(main.isCapturing) {
        isCapturing = main.isCapturing
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Camera Preview with limited height to create space below
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Take up available space but allow room below
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    GLSurfaceView(context).apply {
                        setEGLContextClientVersion(2)
                        preserveEGLContextOnPause = true
                        setRenderer(main)
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        setWillNotDraw(false)
                    }
                }
            )

            // Display either the last captured RGB image or Depth image
            val bitmapToShow = if (isDisplayingDepth) lastCapturedDepthImage else lastCapturedImage
            bitmapToShow?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = if (isDisplayingDepth) "Depth Image" else "RGB Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Frame Counter
        Text(
            text = "Frame: $frameCounter",
            color = Color.White,
            modifier = Modifier
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 2.dp)
        )

        // Determine the resolution to display based on the selected image type
        val resolutionText = if (isDisplayingDepth) {
            lastCapturedDepthImage?.let { "Depth: ${it.width} x ${it.height}" } ?: "Error"
        } else {
            lastCapturedImage?.let { "Image: ${it.width} x ${it.height}" } ?: "Error"
        }
        Text(
            text = resolutionText,
            color = Color.White,
            modifier = Modifier
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 2.dp)
        )

        // Controls and button overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // FPS Slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "FPS: ${main.fps}",
                    color = Color.White,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Slider(
                    value = main.fps.toFloat(),
                    onValueChange = { newValue ->
                        main.fps = newValue.toInt()
                    },
                    valueRange = 1f..60f,
                    steps = 59,
                    modifier = Modifier.weight(1f)
                )
            }

            // Max Frames Slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Max Frames: ${main.maxFrames}",
                    color = Color.White,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Slider(
                    value = main.maxFrames.toFloat(),
                    onValueChange = { newValue ->
                        main.maxFrames = newValue.toInt()
                    },
                    valueRange = 1f..30f,
                    steps = 29,
                    modifier = Modifier.weight(1f)
                )
            }

            // Capture Buttons Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Start/Stop Capture Button
                Box(
                    modifier = Modifier
                        .background(
                            if (isCapturing) Color.hsl(0F, 1F, 0.36F) else Color.hsl(145F, 1F, 0.36F),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (main.isCapturing) {
                                main.stopAutomaticCapture()
                            } else {
                                main.startAutomaticCapture()
                            }
                            isCapturing = main.isCapturing
                        }
                        .padding(16.dp)
                        .weight(1f), // Take equal width as snapshot button
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isCapturing) "Stop" else "Capture",
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Manual Snapshot Button
                Box(
                    modifier = Modifier
                        .background(
                            Color.hsl(200F, 1F, 0.36F),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            Log.d("CaptureScreen", "Manual snapshot button clicked")
                            main.takeSnapshot() // Call the manual snapshot function in MainActivity
                        }
                        .padding(16.dp)
                        .weight(1f), // Take equal width as capturing button
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Snapshot",
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Swap Button for toggling RGB/Depth display
                Box(
                    modifier = Modifier
                        .background(
                            Color(0xFF8A2BE2), // Purple color
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            isDisplayingDepth = !isDisplayingDepth
                        }
                        .padding(16.dp)
                        .weight(1f), // Take equal width as other buttons
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔃", // Use an icon or emoji to represent swap
                        color = Color.White
                    )
                }
            }
        }
    }
}
