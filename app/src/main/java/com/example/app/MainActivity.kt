package com.example.app

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.app.ui.theme.AppTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import java.io.File


class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private lateinit var mInfo: String

    // requestInstall(Activity, true) will triggers installation of
    // Google Play Services for AR if necessary.
    private var mUserRequestedInstall = true
    private var mSession: Session? = null
    private lateinit var mAnchor: Anchor
    private var mCurrentInd = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mInfo = if (ArCoreApk.getInstance().checkAvailability(this).isSupported){
            "arcore supported"
        } else {
            "arcore not supported"
        }

        enableEdgeToEdge()
        setContent {
            TextAppContent(info = mInfo)
        }

    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "resuming")

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
                        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)){
                            config.setDepthMode(Config.DepthMode.AUTOMATIC)
                        } else {
                            Log.e(TAG, "no arcore depth")
                        }
                        session.configure(config)
                        //mAnchor = session.createAnchor(Pose.makeTranslation(0.0f,0.0f,0.0f))
                        mSession = session
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
            setContent {
                ButtonAppContent {
                    Log.d(TAG, "button pressed")
                    mSession?.let { session ->
                        val frame = session.update()
                        frame.acquirePointCloud().use {cloud ->
                            Log.d(TAG, "acquired point cloud")
                            val rem = cloud.points.remaining()
                            openFileOutput("data_$mCurrentInd", Context.MODE_PRIVATE).use { file ->
                                Log.d(TAG, "starting file write")
                                val point = FloatArray(4)
                                for (i in 0..<rem/4) {
                                    point[0] = cloud.points.get()
                                    point[1] = cloud.points.get()
                                    point[2] = cloud.points.get()
                                    point[3] = cloud.points.get()

                                    //point = mAnchor.pose.transformPoint(point)

                                    file.write(point[0].toString().toByteArray())
                                    file.write(" ".toByteArray())
                                    file.write(point[1].toString().toByteArray())
                                    file.write(" ".toByteArray())
                                    file.write(point[2].toString().toByteArray())
                                    file.write(" ".toByteArray())
                                    file.write(point[3].toString().toByteArray())
                                    file.write("\n".toByteArray())

                                }
                            }
                            Log.d(TAG, "wrote to file data_$mCurrentInd")
                            mCurrentInd=(mCurrentInd+1)%2

                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        mSession?.pause()
    }

    //override fun onRequestPermissionsResult(
    //    requestCode: Int,
    //    permissions: Array<out String>,
    //    grantResults: IntArray
    //) {
    //    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    //    when (requestCode) {
    //        0 -> {
    //           if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
    //               mHasCameraPermission = true
    //           }
    //        } else -> {

    //        }
    //    }
    //}

}

@Composable
fun ButtonAppContent(callback: () -> Unit){
    AppTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Button(
                onClick = callback,
                contentPadding = innerPadding
            ) {
                Text(text = "button")
            }
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

//@Preview(showBackground = true)
//@Composable
//fun AppPreview() {
//    AppContent(info = "asd", {})
//}