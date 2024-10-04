package com.example.app

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.tracqi.fsensor.sensor.acceleration.ComplementaryLinearAccelerationFSensor


class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var mSensorManager: SensorManager
    private var mLinAccelSensor: Sensor? = null
    private lateinit var mInfo: String
    private var mLinAccelData: FloatArray = floatArrayOf(0.0f,0.0f,0.0f)
    private val mDataWeight = 0.2f

    //private lateinit var mCamManager: CameraManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mLinAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        mInfo = if (mLinAccelSensor==null){
            "no sensor"
        } else {
            "fine"
        }

        var fsensor = ComplementaryLinearAccelerationFSensor(mSensorManager)
        fsensor.regis
        //mCamManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        //val camIdList = mCamManager.cameraIdList
        //
        //val requestPermissionLauncher =
        //    registerForActivityResult(ActivityResultContracts.RequestPermission()
        //    ) { isGranted: Boolean ->
        //        if (isGranted) {
        //            mInfo = "has perms"
        //        } else {
        //            mInfo = "no perms"
        //        }
        //    }
        //
        //requestPermissionLauncher.launch(Context.CAMERA_SERVICE)
        enableEdgeToEdge()
        setContent {
            AppContent(info = mInfo)
        }

    }


    override fun onResume() {
        super.onResume()
        mLinAccelSensor?.also { accel ->
            mSensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_FASTEST)
        }

        //val camCharacteristics =
        //    mCamManager.cameraIdList.map{id -> mCamManager.getCameraCharacteristics(id)}

        //val cal0 = camCharacteristics[0][CameraCharacteristics.LENS_INTRINSIC_CALIBRATION]
        //val cal1 = camCharacteristics[1][CameraCharacteristics.LENS_INTRINSIC_CALIBRATION]

        //setContent {
        //    AppContent(info = cal0.contentToString() + '\n' + cal1.contentToString())
        //}
        setContent {
            AppContent(info = mInfo)
        }
    }

    override fun onStop() {
        super.onStop()
        mSensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION){

            //mLinAccelData[0] = (1-mDataWeight)*mLinAccelData[0] + mDataWeight*event.values[0]
            //mLinAccelData[1] = (1-mDataWeight)*mLinAccelData[1] + mDataWeight*event.values[1]
            //mLinAccelData[2] = (1-mDataWeight)*mLinAccelData[2] + mDataWeight*event.values[2]
            mLinAccelData[0] = event.values[0]
            mLinAccelData[1] = event.values[1]
            mLinAccelData[2] = event.values[2]

            mInfo = mLinAccelData.contentToString()
            setContent {
                AppContent(info = mInfo)
            }
        }
    }
}

@Composable
fun AppContent(info: String){
    AppTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            TextContent(
                content = info,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
fun TextContent(content: String, modifier: Modifier = Modifier) {
    Surface(color = Color.Black) {
        Text(
            text = content,
            modifier = modifier.padding(),
            color = Color.White
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    AppContent(info = arrayOf(2.3,2.4,5.2).contentToString())
}