package com.example.app

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import com.google.ar.core.Camera
import java.nio.FloatBuffer

class FrameBufferInfo(startInd: Int, pointNum: Int)

class PointCloudRenderer(context: Context, framebufferSize: Int) {

    companion object Info {
        const val TAG = "Renderer"
    }

    private val frameBufferInfos: Array<FrameBufferInfo> = Array(framebufferSize) {
        FrameBufferInfo(0, 0)
    }
    private val frameBufferCurrInd: Int = 0

    private val pointBuffer: Int
    private val pointBufferSize: Int = 0

    init {

        val pbuffer = IntArray(1)
        GLES20.glGenBuffers(1, pbuffer, 0)
        pointBuffer = pbuffer[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pointBuffer)

        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, pointBuffer, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);


        logIfGlError(TAG, "init")
    }

    fun addPoints(points: FloatBuffer){

    }

    fun setCameraImage(colors: FloatBuffer){

    }

    fun draw(camera: Camera){

    }
}

fun logIfGlError(tag: String, msg: String){
    if (GLES20.glGetError() != GLES20.GL_NO_ERROR){
        Log.e(tag, msg)
    }
}