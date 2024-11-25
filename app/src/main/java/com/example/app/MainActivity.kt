package com.example.app

import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.time.measureTime

data class CurrentPointCloud(var data: PointCloudData, val gpuData: GPUPointCloud, var isSaved: Boolean = false)

class MainActivity : ComponentActivity(), GLSurfaceView.Renderer{
    val TAG = "MainActivity"

    private lateinit var mInfo: String
    private var mUserRequestedInstall = true
    private var mSession: Session? = null
    private lateinit var mDisplayRotationHelper: DisplayRotationHelper
    private var mDepthTimestamp: Long = -1
    private lateinit var mRenderer: PointCloudRendererEx
    private val pointMax = 15000

    private var mCurrentPointCloud: CurrentPointCloud? = null

    var mState: MainState = MainState.CAPTURER
    var mSavePointCloud: Boolean = false
    var mSerialize: Boolean = false

    private val mPointCloudList: MutableList<PointCloudData> = mutableListOf()
    private val mGPUPointCloudList: MutableList<GPUPointCloud> = mutableListOf()
    private var mClusterAABBs: Array<AABB>? = null

    private val nativeCode: NativeCode = NativeCode()

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

        GLES20.glClearColor(0.1f,0.1f,0.1f,1.0f)
        val texArr = IntArray(1)
        GLES20.glGenTextures(1, texArr, 0)
        mSession?.setCameraTextureName(texArr[0])
        mRenderer = PointCloudRendererEx(this)
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        Log.d(TAG, "surface changed: $width - $height")
        mDisplayRotationHelper.onSurfaceChanged(width, height)
        GLES20.glViewport(0,0,width,height)
    }

    override fun onDrawFrame(unused: GL10) {
        Log.d("TEST", "val: ${nativeCode.getVal()}")
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        mSession?.let {session ->
            Log.d(TAG, "anchor num: ${session.allAnchors.size}")
            mDisplayRotationHelper.updateSessionIfNeeded(session)
            val frame = session.update()
            val camera = frame.getCamera()

            if (mState == MainState.CAPTURER){
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
                        containsNewDepthData = false
                    }
                    if (containsNewDepthData){
                        mDepthTimestamp = newDepthTimestamp

                        val pcTimeTaken = measureTime {
                            PointCloudData.create(session, frame, pointMax)?.let { pointData ->
                                mCurrentPointCloud?.let {
                                    if(!it.isSaved){
                                        it.data.close()
                                        it.gpuData.close()
                                    }
                                }
                                mCurrentPointCloud =
                                    CurrentPointCloud(pointData, GPUPointCloud(pointData.points), false)
                            }
                        }
                        Log.d(TAG, "point gen time taken: $pcTimeTaken")
                    } else {
                        Log.d(TAG, "No new depth data")
                    }
                }

                mCurrentPointCloud?.let{ pointCloud ->
                    if(mSavePointCloud){
                        mPointCloudList.add(pointCloud.data)
                        mGPUPointCloudList.add(pointCloud.gpuData)
                        mSavePointCloud = false
                        pointCloud.isSaved = true
                        mClusterAABBs=null
                    }
                    val modelMat = FloatArray(16)
                    //Matrix.setIdentityM(modelMat, 0)
                    pointCloud.data.cameraAnchor.pose.toMatrix(modelMat,0)
                    mRenderer.draw(
                        pointCloud.gpuData.gpuBuffer,
                        pointCloud.gpuData.pointNum,
                        modelMat,
                        camera,
                        0.3f,
                        5.0f
                    )
                }
            } else {
                if (mClusterAABBs==null && mPointCloudList.size>0){
                    var size = 0
                    for(pc in mPointCloudList){
                        size += pc.points.remaining()
                    }
                    val pcBuf = ByteBuffer.allocateDirect(size*Float.SIZE_BYTES)
                        .order(ByteOrder.nativeOrder()).asFloatBuffer()
                    val modelMat = FloatArray(16)
                    val pCamera = FloatArray(4)
                    val pWorld = FloatArray(4)
                    var pointCount = 0
                    for(pc in mPointCloudList){
                        pc.cameraAnchor.pose.toMatrix(modelMat,0)
                        while(pc.points.hasRemaining()){
                            pc.points.get(pCamera)
                            val confidence = pCamera[3]
                            if(confidence < 1.0){
                                continue
                            }
                            pCamera[3] = 1.0f
                            Matrix.multiplyMV(pWorld,0,modelMat,0,pCamera,0)
                            pcBuf.put(pWorld[0])
                            pcBuf.put(pWorld[1])
                            pcBuf.put(pWorld[2])
                            //pcBuf.put(confidence)
                            pointCount+=1
                        }
                        pc.points.rewind()
                    }
                    pcBuf.rewind()
                    Log.d("A/D", "antes")
                    mClusterAABBs = nativeCode.cluster(pcBuf,pointCount)
                    Log.d("A/D", "depois")
                }
                mClusterAABBs?.let {
                    for(aabb in it){
                        Log.d("ClusterAABB", "x:${aabb.bx-aabb.sx},y:${aabb.by-aabb.sy},z:${aabb.bz-aabb.sz}")
                    }
                }
                if(mSerialize){
                    openFileOutput("data", Context.MODE_PRIVATE).use { file ->
                        Log.d(TAG, "starting file write")
                        for (pc in mPointCloudList){
                            pc.serializeToFile(file)
                        }
                    }
                    mSerialize = false
                }
                for (i in mPointCloudList.indices){
                    val modelMat = FloatArray(16)
                    //Matrix.setIdentityM(modelMat, 0)
                    mPointCloudList[i].cameraAnchor.pose.toMatrix(modelMat,0)
                    mRenderer.draw(
                        mGPUPointCloudList[i].gpuBuffer,
                        mGPUPointCloudList[i].pointNum,
                        modelMat,
                        camera,
                        0.3f,
                        5.0f
                    )
                }
            }
        }

    }
}


@Composable
fun AppContent(main: MainActivity) {
    AppTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1.0f)) {
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
                        }
                    }
                )
            }

            Row(modifier = Modifier.offset(0.dp, (-64).dp)) {
                var uiMode by remember {mutableStateOf(main.mState)}
                val modeChanger = {
                    if(uiMode == MainState.CAPTURER){
                        main.mState = MainState.RENDERER
                        uiMode = MainState.RENDERER
                    }
                    else {
                        main.mState = MainState.CAPTURER
                        uiMode = MainState.CAPTURER
                    }
                }
                Button(onClick = modeChanger, ) {
                    val otherModeText: String = if (uiMode == MainState.CAPTURER) "renderer"
                    else "capturer"
                    Text(
                        text = otherModeText,
                        color = Color.White
                    )
                }

                if(uiMode == MainState.CAPTURER){
                    Button(onClick = { main.mSavePointCloud = true }, ) {
                        Text(
                            text = "save point cloud",
                            color = Color.White
                        )
                    }
                } else {
                    Button(onClick = { main.mSerialize = true }, ) {
                        Text(
                            text = "serialize",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

enum class MainState{
    CAPTURER, RENDERER
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