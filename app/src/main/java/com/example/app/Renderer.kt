package com.example.app

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import com.example.app.PointCloudRenderer.Info.TAG
import com.google.ar.core.Anchor
import com.google.ar.core.Camera
import org.apache.commons.math3.ml.clustering.Cluster
import java.io.InputStreamReader
import java.lang.RuntimeException
import java.nio.FloatBuffer

data class FrameInfo(val numPoints: Int, val cameraAnchor: Anchor, val cameraTransf: FloatArray)


class ClusterFrameBuffer(
    private val frameNum: Int,
    private val maxFramePointsNum: Int
) : Collection<Point> {
    val clusterBuffer: FloatBuffer = FloatBuffer.allocate(frameNum * maxFramePointsNum * PointCloudData.VALUES_PER_POINT)
    val frameInfos: Array<FrameInfo?> = Array(frameNum) { null }
    private var frameBufferCurrInd: Int = 0

    // can't be 0 points
    fun addPoints(pointData: PointCloudData){
        val offset = maxFramePointsNum * frameBufferCurrInd
        val pointNum = pointData.points.remaining() / PointCloudData.VALUES_PER_POINT

        clusterBuffer.position(offset)
        clusterBuffer.put(pointData.points)
        clusterBuffer.rewind()

        frameInfos[frameBufferCurrInd]?.cameraAnchor?.detach()
        frameInfos[frameBufferCurrInd] = FrameInfo(pointNum, pointData.cameraAnchor, pointData.cameraTransf)
        frameBufferCurrInd = (frameBufferCurrInd+1) % frameNum
    }

    override val size: Int
        get() {
            var size: Int = 0
            for(fInfo in frameInfos){
                fInfo?.let {
                    size += it.numPoints
                }
            }
            return size
        }

    override fun isEmpty(): Boolean {
        for (fInfo in frameInfos) {
            if (fInfo != null){
                return false
            }
        }
        return true
    }

    class PointIter(private val fBuffer: ClusterFrameBuffer) : Iterator<Point> {
        private var frameInd = 0
        private var currInd = 0
        override fun hasNext(): Boolean {
            if(frameInd >= fBuffer.frameNum){
                return false
            }
            val fInfo: FrameInfo
            if (fBuffer.frameInfos[frameInd] == null) {
                return false
            } else {
                fInfo = fBuffer.frameInfos[frameInd]!!
            }
            return currInd <= (fInfo.numPoints * PointCloudData.VALUES_PER_POINT - 4)
        }

        override fun next(): Point {
            val x = fBuffer.clusterBuffer.get(currInd)
            val y = fBuffer.clusterBuffer.get(currInd+1)
            val z = fBuffer.clusterBuffer.get(currInd+2)
            val w = fBuffer.clusterBuffer.get(currInd+3)
            currInd += 4
            if (currInd >= (fBuffer.frameInfos[frameInd]!!.numPoints * PointCloudData.VALUES_PER_POINT)) {
                frameInd++
                currInd = frameInd * fBuffer.maxFramePointsNum * PointCloudData.VALUES_PER_POINT
            }
            return Point(floatArrayOf(x,y,z,w))
        }
    }

    override fun iterator(): Iterator<Point> {
        return PointIter(this)
    }

    override fun contains(element: Point): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<Point>): Boolean {
        TODO("Not yet implemented")
    }
}

class PointCloudClusterRenderer(
    context: Context,
) {

    companion object Info {
        const val TAG = "PointCloudClusterRenderer"
        const val VERT_SHADER_FILE = "point_cloud_cluster.vert"
        const val FRAG_SHADER_FILE = "point_cloud_cluster.frag"
    }

    private var clusterBuffers: IntArray = IntArray(0)
    private var clusterPointCount: IntArray = IntArray(0)
    private var clusterCount: Int = 0

    private val program: Int

    private val positionAttribute: Int
    //private val colorAttribute: Int
    private val modelViewProjectionUniform: Int
    private val pointSizeUniform: Int
    private val confidenceThresholdUniform: Int
    private val cameraPositionUniform: Int
    private val clusterColorUniform: Int

    init {
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
        clusterColorUniform = GLES20.glGetUniformLocation(program, "u_ClusterColor")

        logIfGlError(TAG, "error on init")
    }

    fun setPoints(clusterData: List<Cluster<Point>>){
        clusterCount = clusterData.size
        if (clusterData.size > clusterBuffers.size) {
            val oldSize = clusterBuffers.size
            clusterBuffers = clusterBuffers.copyOf(clusterData.size)
            clusterPointCount = clusterPointCount.copyOf(clusterData.size)
            GLES20.glGenBuffers(clusterData.size - oldSize, clusterBuffers, oldSize)
        }

        for (i in 0..clusterData.size) {
            val floatNum = clusterData[i].points.size*PointCloudData.VALUES_PER_POINT
            val buf = FloatBuffer.wrap(
                FloatArray(floatNum){
                    clusterData[it].points[it/4].data[it%4]
                })
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, clusterBuffers[i])
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, floatNum, buf, GLES20.GL_DYNAMIC_DRAW)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            clusterPointCount[i] = clusterData[i].points.size
        }
    }

    fun draw(camera: Camera, confidenceThreshold: Float, pointSize: Float){
        val projectionMatrix = FloatArray(16)
        val viewMatrix = FloatArray(16)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
        camera.getViewMatrix(viewMatrix, 0)

        val modelMatrix = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)
        val modelView = FloatArray(16)
        val modelViewProjection = FloatArray(16)

        val colorArray = arrayOf(
            FloatBuffer.wrap(floatArrayOf(1.0f,0.0f,0.0f,1.0f)),
            FloatBuffer.wrap(floatArrayOf(0.0f,1.0f,0.0f,1.0f)),
            FloatBuffer.wrap(floatArrayOf(0.0f,0.0f,1.0f,1.0f)),
            FloatBuffer.wrap(floatArrayOf(0.0f,0.0f,0.0f,1.0f)),
            FloatBuffer.wrap(floatArrayOf(1.0f,1.0f,1.0f,1.0f)),
        )

        for (i in 0..<clusterCount){
            GLES20.glUseProgram(program)
            GLES20.glEnableVertexAttribArray(positionAttribute)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, clusterBuffers[i])
            GLES20.glVertexAttribPointer(
                positionAttribute, 4, GLES20.GL_FLOAT, false, PointCloudData.POINT_SIZE_BYTES, 0)

            Matrix.multiplyMM(modelView, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelView, 0)

            GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjection, 0)
            GLES20.glUniform1f(pointSizeUniform, pointSize)
            GLES20.glUniform1f(confidenceThresholdUniform, confidenceThreshold)
            GLES20.glUniform4fv(clusterColorUniform, 1, colorArray[i%colorArray.size])

            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, clusterPointCount[i])

            GLES20.glDisableVertexAttribArray(positionAttribute)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            logIfGlError(PointCloudRenderer.TAG, "render end loop")
        }

        logIfGlError(TAG, "render end")
    }
}

class PointCloudRenderer(
    context: Context,
    private val frameNum: Int,
    private val maxFramePointsNum: Int
) {

    companion object Info {
        const val TAG = "PointCloudRenderer"
        const val VERT_SHADER_FILE = "point_cloud.vert"
        const val FRAG_SHADER_FILE = "point_cloud.frag"
    }

    private val frameInfos: Array<FrameInfo?> = Array(frameNum) { null }
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
        Log.d(TAG, "subbing point cloud data at $frameBufferCurrInd")
        val offset = maxFramePointsNum * frameBufferCurrInd * PointCloudData.POINT_SIZE_BYTES
        val pointNum = pointData.points.remaining() / PointCloudData.VALUES_PER_POINT
        val byteNum = pointData.points.remaining() * Float.SIZE_BYTES
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pointBuffer)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, offset, byteNum, pointData.points)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        frameInfos[frameBufferCurrInd]?.cameraAnchor?.detach()
        frameInfos[frameBufferCurrInd] = FrameInfo(pointNum, pointData.cameraAnchor, pointData.cameraTransf)
        frameBufferCurrInd = (frameBufferCurrInd+1) % frameNum
    }

    fun draw(camera: Camera, confidenceThreshold: Float, pointSize: Float){
        val projectionMatrix = FloatArray(16)
        val viewMatrix = FloatArray(16)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
        camera.getViewMatrix(viewMatrix, 0)

        var modelMatrix = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)

        val modelView = FloatArray(16)
        val modelViewProjection = FloatArray(16)


        logIfGlError(TAG, "render before loop")

        var renderNum = 0
        for (i in frameInfos.indices){
            frameInfos[i]?.let { frameInfo ->
                GLES20.glUseProgram(program)
                GLES20.glEnableVertexAttribArray(positionAttribute)
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pointBuffer)
                GLES20.glVertexAttribPointer(
                    positionAttribute, 4, GLES20.GL_FLOAT, false, PointCloudData.POINT_SIZE_BYTES, 0)

                //frameInfo.cameraAnchor.pose.toMatrix(modelMatrix,0)
                //modelMatrix = frameInfo.cameraTransf

                Matrix.multiplyMM(modelView, 0, viewMatrix, 0, modelMatrix, 0)
                Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelView, 0)

                GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjection, 0)
                GLES20.glUniform1f(pointSizeUniform, pointSize)
                GLES20.glUniform1f(confidenceThresholdUniform, confidenceThreshold)

                GLES20.glDrawArrays(GLES20.GL_POINTS, i*maxFramePointsNum, frameInfo.numPoints)

                GLES20.glDisableVertexAttribArray(positionAttribute)
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
                logIfGlError(TAG, "render end loop")
                renderNum++
            }
        }
        Log.d(TAG, "rendered $renderNum frames")

        logIfGlError(TAG, "render end")
    }
}

class SinglePointCloudRenderer(
    context: Context,
    maxFramePointsNum: Int
) {

    companion object Info {
        const val TAG = "SinglePointCloudRenderer"
        const val VERT_SHADER_FILE = "point_cloud.vert"
        const val FRAG_SHADER_FILE = "point_cloud.frag"
    }

    private val pointBuffer: Int
    private var numPoints: Int = 0
    private var cameraTransf: FloatArray = FloatArray(16)

    private val program: Int

    private val positionAttribute: Int
    private val modelViewProjectionUniform: Int
    private val pointSizeUniform: Int
    private val confidenceThresholdUniform: Int
    private val cameraPositionUniform: Int

    init {

        val pbuffer = IntArray(1)
        GLES20.glGenBuffers(1, pbuffer, 0)
        pointBuffer = pbuffer[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pointBuffer)

        val pointBufferSize = PointCloudData.POINT_SIZE_BYTES * maxFramePointsNum

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
        modelViewProjectionUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection")
        pointSizeUniform = GLES20.glGetUniformLocation(program, "u_PointSize")
        confidenceThresholdUniform = GLES20.glGetUniformLocation(program, "u_ConfidenceThreshold")
        cameraPositionUniform = GLES20.glGetUniformLocation(program, "u_CameraPos")

        logIfGlError(TAG, "error on init")
    }

    fun setPoints(pointData: PointCloudData){
        val byteNum = pointData.points.remaining() * Float.SIZE_BYTES
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pointBuffer)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, byteNum, pointData.points)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        numPoints = pointData.points.remaining() / PointCloudData.VALUES_PER_POINT
        cameraTransf = pointData.cameraTransf.copyOf()
    }

    fun draw(camera: Camera, confidenceThreshold: Float, pointSize: Float){
        val projectionMatrix = FloatArray(16)
        val viewMatrix = FloatArray(16)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
        camera.getViewMatrix(viewMatrix, 0)

        //val modelMatrix = cameraTransf.copyOf()
        val modelMatrix = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)

        val modelView = FloatArray(16)
        val modelViewProjection = FloatArray(16)

        logIfGlError(TAG, "render before loop")

        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionAttribute)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pointBuffer)
        GLES20.glVertexAttribPointer(
            positionAttribute, 4, GLES20.GL_FLOAT, false, PointCloudData.POINT_SIZE_BYTES, 0)

        Matrix.multiplyMM(modelView, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelView, 0)

        GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjection, 0)
        GLES20.glUniform1f(pointSizeUniform, pointSize)
        GLES20.glUniform1f(confidenceThresholdUniform, confidenceThreshold)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, numPoints)

        GLES20.glDisableVertexAttribArray(positionAttribute)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        logIfGlError(TAG, "render end")
    }
}

class DepthRenderer(
    context: Context,
    private val depthWidth: Int,
    private val depthHeight: Int,
) {

    companion object Info {
        const val TAG = "DepthRenderer"
        const val VERT_SHADER_FILE = "depth.vert"
        const val FRAG_SHADER_FILE = "depth.frag"
    }

    private val depthTex: Int

    private val program: Int

    init {

        val dTexArr = IntArray(1)
        GLES20.glGenTextures(1, dTexArr, 0)
        depthTex = dTexArr[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthTex)

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        //GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        //GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES30.GL_R32F, depthWidth, depthHeight,
            0, GLES30.GL_RED, GLES20.GL_FLOAT, null)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        val vertShader = createShader(context, VERT_SHADER_FILE, GLES20.GL_VERTEX_SHADER)
        val fragShader = createShader(context, FRAG_SHADER_FILE, GLES20.GL_FRAGMENT_SHADER)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertShader)
        GLES20.glAttachShader(program, fragShader)
        GLES20.glLinkProgram(program)
        GLES20.glUseProgram(program)

        GLES20.glDeleteShader(vertShader)
        GLES20.glDeleteShader(fragShader)

        logIfGlError(TAG, "error on init")
    }

    fun setDepthTex(depthBuffer: FloatBuffer){
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthTex)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES30.GL_R32F, depthWidth, depthHeight,
            0, GLES30.GL_RED, GLES20.GL_FLOAT, depthBuffer)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun draw(){

        GLES20.glUseProgram(program)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthTex)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)

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