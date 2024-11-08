package com.example.app

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.example.app.PointCloudRenderer.Info.TAG
import com.example.app.PointCloudRenderer.Info.VERT_SHADER_FILE
import com.google.ar.core.Camera
import com.google.ar.core.Pose
import java.io.InputStreamReader
import java.lang.RuntimeException
import java.nio.FloatBuffer

class FrameInfo(val numPoints: Int, val cameraPose: Pose)

class PointCloudRenderer(
    context: Context,
    private val frameNum: Int,
    private val maxFramePointsNum: Int
) {

    companion object Info {
        const val TAG = "Renderer"
        const val VERT_SHADER_FILE = "point_cloud.vert"
        const val FRAG_SHADER_FILE = "point_cloud.frag"
    }

    private val frameInfos: Array<FrameInfo> = Array(frameNum) {
        FrameInfo(0, Pose.IDENTITY)
    }
    private var frameBufferCurrInd: Int = 0

    private val pointBuffer: Int

    private val program: Int

    private val positionAttribute: Int
    //private val colorAttribute: Int
    private val modelViewProjectionUniform: Int
    private val pointSizeUniform: Int
    private val confidenceThresholdUniform: Int
    private val cameraPositionUniform: Int

    init {

        val pbuffer = IntArray(1)
        GLES20.glGenBuffers(1, pbuffer, 0)
        pointBuffer = pbuffer[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pointBuffer)

        val pointBufferSize = PointCloudData.POINT_SIZE_BYTES * frameNum * maxFramePointsNum

        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, pointBufferSize, null, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        val vertShader = createShader(context, VERT_SHADER_FILE, GLES20.GL_VERTEX_SHADER)
        val fragShader = createShader(context, FRAG_SHADER_FILE, GLES20.GL_FRAGMENT_SHADER)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertShader)
        GLES20.glAttachShader(program, fragShader)
        GLES20.glLinkProgram(program)
        GLES20.glUseProgram(program)

        positionAttribute = GLES20.glGetAttribLocation(program, "a_Position")
        //colorAttribute = GLES20.glGetAttribLocation(program, "a_Color")
        modelViewProjectionUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection")
        pointSizeUniform = GLES20.glGetUniformLocation(program, "u_PointSize")
        confidenceThresholdUniform = GLES20.glGetUniformLocation(program, "u_ConfidenceThreshold")
        cameraPositionUniform = GLES20.glGetUniformLocation(program, "u_CameraPos")

        logIfGlError(TAG, "error on init")
    }

    fun addPoints(pointData: PointCloudData){
        val offset = maxFramePointsNum * frameBufferCurrInd * PointCloudData.POINT_SIZE_BYTES
        val pointNum = pointData.points.remaining()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pointBuffer)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, offset, pointNum, pointData.points)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        val modelMatrix = FloatArray(16)
        pointData.cameraPose.toMatrix(modelMatrix, 0)
        frameInfos[frameBufferCurrInd] = FrameInfo(pointNum, pointData.cameraPose)
        frameBufferCurrInd = (frameBufferCurrInd+1) % frameNum
    }

    fun draw(camera: Camera, confidenceThreshold: Float, pointSize: Float){
        val projectionMatrix = FloatArray(16)
        val viewMatrix = FloatArray(16)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
        camera.getViewMatrix(viewMatrix, 0)

        val modelMatrix = FloatArray(16)
        val modelView = FloatArray(16)
        val modelViewProjection = FloatArray(16)

        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionAttribute)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pointBuffer)
        GLES20.glVertexAttribPointer(
            positionAttribute, 4, GLES20.GL_FLOAT, false, PointCloudData.POINT_SIZE_BYTES, 0)

        logIfGlError(TAG, "render before loop")

        for (i in frameInfos.indices){
            val frameInfo = frameInfos[i]
            if (frameInfo.numPoints==0) {
                continue
            }

            frameInfo.cameraPose.toMatrix(modelMatrix,0)

            Matrix.multiplyMM(modelView, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, viewMatrix, 0)

            GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjection, 0)
            GLES20.glUniform1f(pointSizeUniform, pointSize)
            GLES20.glUniform1f(confidenceThresholdUniform, confidenceThreshold)

            GLES20.glDrawArrays(GLES20.GL_POINTS, i*maxFramePointsNum, frameInfo.numPoints)

            logIfGlError(TAG, "render end loop")
        }
        GLES20.glDisableVertexAttribArray(positionAttribute)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        logIfGlError(TAG, "render end")
    }
}

fun createShader(context: Context, filePath: String, type: Int): Int{
    var shaderSource: String
    context.assets.open(filePath).use { input ->
        InputStreamReader(input).use { reader ->
            shaderSource = reader.readText()
        }
    }
    val shader: Int = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, shaderSource)
    GLES20.glCompileShader(shader)

    val compileStatus = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

    if (compileStatus[0]==0){
        Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader))
        GLES20.glDeleteShader(shader)
        throw RuntimeException("Error creating shader. check logs")
    }

    return shader
}

fun logIfGlError(tag: String, msg: String){
    if (GLES20.glGetError() != GLES20.GL_NO_ERROR){
        Log.e(tag, msg)
    }
}