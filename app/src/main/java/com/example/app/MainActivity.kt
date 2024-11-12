package com.example.app

import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLES32
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.app.ui.theme.AppTheme
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class MainActivity : ComponentActivity(), GLSurfaceView.Renderer{
    val TAG = "MainActivity"

    private lateinit var mInfo: String
    private var mUserRequestedInstall = true
    private var mSession: Session? = null
    private var mCurrentInd = 0
    var mShouldWrite = AtomicBoolean(false)
    private lateinit var mDisplayRotationHelper: DisplayRotationHelper
    private var mDepthTimestamp: Long = -1
    private lateinit var mRenderer: PointCloudRenderer
    private val pointMax = 15000

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
            TextAppContent(info = mInfo)
        }

    }

    override fun onDestroy() {
        mSession?.close()
        super.onDestroy()
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
                        if (session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)){
                            config.setDepthMode(Config.DepthMode.RAW_DEPTH_ONLY)
                        } else {
                            Log.e(TAG, "no arcore depth")
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
            setContent{
                TextAppContent(info = mInfo)
            }
        } else {
            Log.d(TAG, "presenting with session exists")
            mSession?.resume()
            //mSurface.onResume()
            setContent {
                AppContent(this)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mSession?.pause()
        mDisplayRotationHelper.onPause()
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glGetString(GLES20.GL_VERSION).also {
            Log.d(TAG, "Version: $it")
        }
        //GLES20.glEnable(GLES32.GL_DEBUG_OUTPUT)
        //GLES32.glDebugMessageCallback { source, type, id, severity, message ->
        //    if (type == GLES32.GL_DEBUG_TYPE_ERROR) {
        //        Log.e(PointCloudRenderer.TAG, "opengl error: $message")
        //    } else {
        //        Log.d(PointCloudRenderer.TAG, "opengl message: $message")
        //    }
        //}
        GLES20.glClearColor(0.1f,0.1f,0.1f,1.0f)
        val texArr = IntArray(1)
        GLES20.glGenTextures(1, texArr, 0)
        mSession?.setCameraTextureName(texArr[0])
        mRenderer = PointCloudRenderer(this, 60, pointMax)
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        Log.d(TAG, "surface changed: $width - $height")
        mDisplayRotationHelper.onSurfaceChanged(width, height)
        GLES20.glViewport(0,0,width,height)
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        mSession?.let {session ->
            mDisplayRotationHelper.updateSessionIfNeeded(session)
            val frame = session.update()
            val camera = frame.getCamera()

            if (camera.getTrackingState() != TrackingState.TRACKING) {
                Log.d(TAG, "camera not tracking")
            } else {
                var containsNewDepthData: Boolean
                var newDepthTimestamp: Long = -1
                try {
                    frame.acquireRawDepthImage16Bits().use { depthImage ->
                        containsNewDepthData = mDepthTimestamp != depthImage.timestamp
                        newDepthTimestamp = depthImage.timestamp
                    }
                } catch (e: NotYetAvailableException) {
                    // This is normal at the beginning of session, where depth hasn't been estimated yet.
                    containsNewDepthData = false
                }
                if (containsNewDepthData){
                    mDepthTimestamp = newDepthTimestamp

                    PointCloudData.create(session, frame, pointMax)?.let { pointData ->
                        mRenderer.addPoints(pointData)
                    }
                } else {
                    Log.d(TAG, "No new depth data")
                }
            }

            mRenderer.draw(camera, 0.3f, 5.0f)
        }

    }
}


@Composable
fun AppContent(main: MainActivity) {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            // GLSurfaceView takes the full screen
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    GLSurfaceView(context).apply {
                        setEGLContextClientVersion(3)
                        setPreserveEGLContextOnPause(true)
                        setRenderer(main)
                        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
                        setWillNotDraw(false)
                        setOnClickListener {
                            main.mShouldWrite.set(true)
                            Log.d(main.TAG, "clicked")
                        }
                    }
                }
            )
        }
    }
}


@Composable
fun TextAppContent(info: String){
    AppTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            TextContent(content = info, Modifier.padding(innerPadding))
        }
    }
}

@Composable
fun TextContent(content: String, modifier: Modifier = Modifier) {
    Surface(color = Color.Black) {
        Text(
            text = content,
            modifier = modifier.padding(24.dp),
            color = Color.White
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    AppContent(MainActivity())
}