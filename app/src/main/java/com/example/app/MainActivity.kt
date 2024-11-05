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
import com.google.ar.core.Frame
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.FileWriter
import java.io.BufferedWriter

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

import android.graphics.Matrix
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity(), GLSurfaceView.Renderer{
    val TAG = "MainActivity"

    private lateinit var mInfo: String
    private var mUserRequestedInstall = true
    private var mSession: Session? = null
    var mShouldWrite = AtomicBoolean(false)
    private lateinit var mDisplayRotationHelper: DisplayRotationHelper

    var currentFrameIndex by mutableStateOf(0)
    var maxFrames by mutableStateOf(10)
    var fps by mutableStateOf(30) // Default to 30 FPS


    var isCapturing = false
    private val handler = Handler(Looper.getMainLooper())

    // Variable to hold the last captured image
    var lastCapturedImage by mutableStateOf<Bitmap?>(null)


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
        Log.d(TAG, "resuming")
        mDisplayRotationHelper.onResume()

        // Check camera permission.
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {

            Log.d(TAG, "requesting camera permission")
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 0)
            return
        }

        // Ensure that Google Play Services for AR and ARCore device profile data are
        // installed and up to date.
        if (mSession == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                    ArCoreApk.InstallStatus.INSTALLED -> {
                        // Success: Safe to create the AR session.
                        val session = Session(this)
                        val config = session.getConfig()

                        // Enable autofocus by setting the focus mode to AUTO
                        config.focusMode = Config.FocusMode.AUTO

                        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)){
                            config.setDepthMode(Config.DepthMode.AUTOMATIC)
                        } else {
                            Log.e(TAG, "no arcore  ")
                        }
                        session.configure(config)
                        //mAnchor = session.createAnchor(Pose.makeTranslation(0.0f,0.0f,0.0f))
                        mSession = session
                        //mSurface.setRenderer(this)
                        Log.d(TAG, "created session")
                    }
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        // When this method returns `INSTALL_REQUESTED`:
                        // 1. ARCore pauses this activity.
                        // 2. ARCore prompts the user to install or update Google Play
                        //    Services for AR (market://details?id=com.google.ar.core).
                        // 3. ARCore downloads the latest device profile data.
                        // 4. ARCore resumes this activity. The next invocation of
                        //    requestInstall() will either return `INSTALLED` or throw an
                        //    exception if the installation or update did not succeed.
                        mUserRequestedInstall = false
                        Log.d(TAG, "install requested")
                        return
                    }
                }
            } catch (e: UnavailableUserDeclinedInstallationException) {
                Log.e(TAG, "declined arcore install")
                return
            } catch (e: Exception) {
                Log.e(TAG, "arcore install error:" + e.message)
                return
            }
        }

        if (mSession == null){
            Log.d(TAG, "presenting with session null")
        } else {
            Log.d(TAG, "presenting with session exists")
            mSession?.resume()
            //mSurface.onResume()
            setContent {
                AppTheme {
                    CaptureScreen(this)
                }
            }

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

            if (mShouldWrite.get()) {
                mShouldWrite.set(false)
                val camera = frame.camera
                if (camera.trackingState != TrackingState.TRACKING) return
                Log.d(TAG, "FRAME: $currentFrameIndex")
                try {
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
                            rotateBitmap(rgbBitmap, 90)
                        } else {
                            rgbBitmap
                        }

                        // Update lastCapturedImage for UI display
                        lastCapturedImage = rotatedBitmap
                    }

                    // Update the frame index and cycle within maxFrames
                    currentFrameIndex = (currentFrameIndex + 1) % maxFrames

                } catch (e: NotYetAvailableException) {
                    Log.e(TAG, "Required data not yet available: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving RGB and depth data: ${e.message}")
                }
            }
        }
    }


    // Function to capture and save RGB and depth data
    private fun saveRgbAndDepthData(frame: Frame, rgbFileName: String, dataFileName: String) {
        // Acquire and save RGB image
        val rgbBitmap = frame.acquireCameraImage().use { image ->
            imageToBitmap(image).also { bitmap ->
                saveBitmapAsPng(bitmap, rgbFileName)
                Log.d(TAG, "Saved RGB image as $rgbFileName")
            }
        }

        // Acquire depth data
        val depthData = frame.acquireRawDepthImage16Bits().use { depthImage ->
            depthImageToShortArray(depthImage)
        }

        // Resize depth data to match RGB resolution
        val resizedDepthData = resizeDepthBilinear(depthData, 160, 120, rgbBitmap.width, rgbBitmap.height)

        // Save combined RGB and depth information
        saveRgbAndDepthFile(rgbBitmap, resizedDepthData, dataFileName)
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

    // Convert ARCore Depth Image to ShortArray
    private fun depthImageToShortArray(depthImage: Image): ShortArray {
        val buffer = depthImage.planes[0].buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val depthArray = ShortArray(buffer.remaining())
        buffer.get(depthArray)
        return depthArray
    }

    // Bilinear interpolation to resize depth data to match RGB resolution
    private fun resizeDepthBilinear(depthData: ShortArray, depthWidth: Int, depthHeight: Int, rgbWidth: Int, rgbHeight: Int): Array<Array<Short>> {
        val resizedDepth = Array(rgbHeight) { Array(rgbWidth) { 0.toShort() } }

        for (y in 0 until rgbHeight) {
            for (x in 0 until rgbWidth) {
                // Map RGB coordinates to depth coordinates
                val gx = (x.toFloat() * depthWidth / rgbWidth).toFloat()
                val gy = (y.toFloat() * depthHeight / rgbHeight).toFloat()

                // Find the four neighboring pixels in depth space, ensuring they are within bounds
                val x0 = gx.toInt().coerceIn(0, depthWidth - 1)
                val x1 = (x0 + 1).coerceIn(0, depthWidth - 1)
                val y0 = gy.toInt().coerceIn(0, depthHeight - 1)
                val y1 = (y0 + 1).coerceIn(0, depthHeight - 1)

                // Calculate interpolation weights
                val wx = gx - x0
                val wy = gy - y0

                // Depth values at the four neighboring points, with bounds check
                val depth00 = depthData.getOrNull(y0 * depthWidth + x0)?.toFloat() ?: 0f
                val depth01 = depthData.getOrNull(y1 * depthWidth + x0)?.toFloat() ?: 0f
                val depth10 = depthData.getOrNull(y0 * depthWidth + x1)?.toFloat() ?: 0f
                val depth11 = depthData.getOrNull(y1 * depthWidth + x1)?.toFloat() ?: 0f

                // Bilinear interpolation
                val interpolatedDepth = ((1 - wx) * (1 - wy) * depth00 +
                        wx * (1 - wy) * depth10 +
                        (1 - wx) * wy * depth01 +
                        wx * wy * depth11).toInt().toShort()

                resizedDepth[y][x] = interpolatedDepth
            }
        }

        return resizedDepth
    }

    // Save Bitmap as PNG file
    private fun saveBitmapAsPng(bitmap: Bitmap, fileName: String) {
        openFileOutput(fileName, Context.MODE_PRIVATE).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
    }

    // Save combined RGB and Depth data
    private fun saveRgbAndDepthFile(rgbBitmap: Bitmap, depthData: Array<Array<Short>>, dataFileName: String) {
        openFileOutput(dataFileName, Context.MODE_PRIVATE).use { fos ->
            for (y in 0 until rgbBitmap.height) {
                for (x in 0 until rgbBitmap.width) {
                    // Get RGB values
                    val pixelColor = rgbBitmap.getPixel(x, y)
                    val red = (pixelColor shr 16) and 0xFF
                    val green = (pixelColor shr 8) and 0xFF
                    val blue = pixelColor and 0xFF

                    // Get Depth value
                    val depthValue = depthData[y][x]

                    // Write RGB and depth data in a structured format
                    fos.write(byteArrayOf(red.toByte(), green.toByte(), blue.toByte()))
                    fos.write(ByteBuffer.allocate(2).putShort(depthValue).array())
                }
            }
        }
        Log.d(TAG, "Saved RGB+depth data as $dataFileName")
    }

    // Save combined RGB and Depth data as CSV for inspection
    private fun saveRgbAndDepthCsv(rgbBitmap: Bitmap, depthData: Array<Array<Short>>, csvFileName: String) {
        openFileOutput(csvFileName, Context.MODE_PRIVATE).use { fos ->
            val writer = BufferedWriter(FileWriter(fos.fd))

            // Write header
            writer.write("Red,Green,Blue,Depth")
            writer.newLine()

            for (y in 0 until rgbBitmap.height) {
                for (x in 0 until rgbBitmap.width) {
                    // Get RGB values
                    val pixelColor = rgbBitmap.getPixel(x, y)
                    val red = (pixelColor shr 16) and 0xFF
                    val green = (pixelColor shr 8) and 0xFF
                    val blue = pixelColor and 0xFF

                    // Get Depth value
                    val depthValue = depthData[y][x]

                    // Write RGB and depth values to CSV
                    writer.write("$red,$green,$blue,$depthValue")
                    writer.newLine()
                }
            }

            writer.flush()
            writer.close()
        }
        Log.d(TAG, "Saved RGB+depth data as CSV $csvFileName")
    }


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
        Log.d(TAG, "Manual Snapshot Taken - Frame: $currentFrameIndex")
        if (isCapturing) startAutomaticCapture()  // Resume auto-capture if needed
    }

    private fun getDeviceRotationDegrees(): Int {
        return when (resources.configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> 90
            Configuration.ORIENTATION_LANDSCAPE -> 0
            else -> 0
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

}

@Composable
fun CaptureScreen(main: MainActivity) {
    var isCapturing by remember { mutableStateOf(main.isCapturing) }
    val lastCapturedImage = main.lastCapturedImage
    val frameCounter = "${main.currentFrameIndex + 1}/${main.maxFrames}"

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
                        setPreserveEGLContextOnPause(true)
                        setRenderer(main)
                        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
                        setWillNotDraw(false)
                    }
                }
            )

            // Display the last captured image if available
            lastCapturedImage?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Last Captured Image",
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
                        text = if (isCapturing) "Stop Capturing" else "Start Capturing",
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
                        text = "Take Snapshot",
                        color = Color.White
                    )
                }
            }
        }
    }
}


