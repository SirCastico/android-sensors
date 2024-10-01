package com.example.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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



class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var mLinAccel: Sensor? = null
    private lateinit var mInfo: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mLinAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        if (mLinAccel==null){
            mInfo = "no sensor"
        } else {
            mInfo = "fine"
        }

        enableEdgeToEdge()
        setContent {
            AppContent(info = mInfo)
        }

    }

    override fun onResume() {
        super.onResume()
        mLinAccel?.also { accel ->
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
        }

        setContent {
            AppContent(info = mInfo)
        }
    }

    override fun onStop() {
        super.onStop()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION){
            mInfo = event.values.contentToString()
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
    Surface(color = Color.Cyan) {
        Text(
            text = content,
            modifier = modifier.padding(24.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    AppContent(info = arrayOf(2.3,2.4,5.2).contentToString())
}