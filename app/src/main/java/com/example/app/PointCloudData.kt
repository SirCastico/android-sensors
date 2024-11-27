package com.example.app

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.example.app.PointCloudHelper.convertDepthTo3dCameraSpacePointBufferFiltered
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import org.apache.commons.math3.ml.clustering.Cluster
import org.apache.commons.math3.ml.clustering.Clusterable
import java.io.Closeable
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class PointCloudData(
    val points: FloatBuffer,
    val cameraAnchor: Anchor
) : Closeable{
    companion object Static{
        const val VALUES_PER_POINT = 4
        const val POINT_SIZE_BYTES = 4 * Float.SIZE_BYTES

        fun create(session: Session, frame: Frame, pointLimit: Int): PointCloudData? {
            try {
                val depthImage = frame.acquireRawDepthImage16Bits()
                val confidenceImage = frame.acquireRawDepthConfidenceImage()

                val intrinsics = frame.camera.textureIntrinsics

                val ctransf = FloatArray(16)
                frame.camera.pose.toMatrix(ctransf,0)

                //val points = convertDepthTo3dCameraSpacePointBuffer(
                //    depthImage, confidenceImage, intrinsics, pointLimit,
                //)
                val points = convertDepthTo3dCameraSpacePointBufferFiltered(
                    depthImage, confidenceImage, intrinsics, pointLimit,
                    ctransf, session.getAllTrackables(Plane::class.java)
                )
                //val points = convertDepthTo3dWorldSpacePointBuffer(
                //    depthImage, confidenceImage, intrinsics, pointLimit, ctransf
                //)
                //filterUsingPlanes(points, session.getAllTrackables(Plane::class.java))

                depthImage.close()
                confidenceImage.close()

                val cameraAnchor = session.createAnchor(frame.camera.pose)

                return PointCloudData(points, cameraAnchor)
            } catch (e: NotYetAvailableException) {
                // This normally means that depth data is not available yet. This is normal so we will not
                // spam the logcat with this.
            }
            return null
        }
    }

    override fun close() {
        cameraAnchor.detach()
    }

    fun serializeToFile(fileOut: FileOutputStream) {
        val modelMat = FloatArray(16)
        cameraAnchor.pose.toMatrix(modelMat,0)
        //Matrix.setIdentityM(modelMat,0)

        while (points.hasRemaining()){
            val pModel = FloatArray(4)
            val pWorld = FloatArray(4)
            points.get(pModel)
            val confidence = pModel[3]
            pModel[3] = 1.0f
            Matrix.multiplyMV(pWorld,0,modelMat,0,pModel,0)

            fileOut.write("${pWorld[0]} ${pWorld[1]} ${pWorld[2]} ${confidence}\n".toByteArray())
        }

    }
}

class GPUPointCloud(pointBuffer: FloatBuffer) : Closeable{
    val gpuBuffer: Int
    var pointNum: Int = pointBuffer.remaining() / PointCloudData.VALUES_PER_POINT

    init {
        val pbuffer = IntArray(1)
        GLES20.glGenBuffers(1, pbuffer, 0)
        gpuBuffer = pbuffer[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gpuBuffer)

        val pointBufferSize = PointCloudData.POINT_SIZE_BYTES * pointNum

        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, pointBufferSize, pointBuffer, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun update(pointBuffer: FloatBuffer){
        pointNum = pointBuffer.remaining() / PointCloudData.VALUES_PER_POINT

        val pointBufferSize = PointCloudData.POINT_SIZE_BYTES * pointNum
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, pointBufferSize, pointBuffer, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    override fun close() {
        GLES20.glDeleteBuffers(1, intArrayOf(gpuBuffer), 0)
    }
}

class Ray(val origin: FloatArray, val direction: FloatArray)

class AABB{
    companion object STATIC {
        const val LINE_BUFFER_VERT_NUM = 24
        const val LINE_BUFFER_VERT_FLOATS = 4
        const val LINE_BUFFER_BYTE_NUM = LINE_BUFFER_VERT_NUM * LINE_BUFFER_VERT_FLOATS * Float.SIZE_BYTES

    }
    var sx: Float = Float.MAX_VALUE
    var sy: Float = Float.MAX_VALUE
    var sz: Float = Float.MAX_VALUE
    var bx: Float = Float.MIN_VALUE
    var by: Float = Float.MIN_VALUE
    var bz: Float = Float.MIN_VALUE

    fun update(x: Float, y: Float, z: Float){
        if(x < sx) sx = x
        if(y < sy) sy = y
        if(z < sz) sz = z
        if(x > bx) bx = x
        if(y > by) by = y
        if(z > bz) bz = z
    }

    fun volume() : Float{
        return (bx - sx) * (by - sy) * (bz - sz)
    }

    fun getLineBuffer() : FloatBuffer {
        // y is up?
        return FloatBuffer.wrap(floatArrayOf(
            bx,by,bz,1.0f,
            sx,by,bz,1.0f,

            bx,by,bz,1.0f,
            bx,sy,bz,1.0f,

            bx,by,bz,1.0f,
            bx,by,sz,1.0f,

            sx,sy,bz,1.0f,
            bx,sy,bz,1.0f,

            sx,sy,bz,1.0f,
            sx,by,bz,1.0f,

            sx,sy,bz,1.0f,
            sx,sy,sz,1.0f,

            sx,by,sz,1.0f,
            bx,by,sz,1.0f,

            sx,by,sz,1.0f,
            sx,sy,sz,1.0f,

            sx,by,sz,1.0f,
            sx,by,bz,1.0f,

            bx,sy,sz,1.0f,
            sx,sy,sz,1.0f,

            bx,sy,sz,1.0f,
            bx,by,sz,1.0f,

            bx,sy,sz,1.0f,
            bx,sy,bz,1.0f,
        ))
    }

    fun intersect(ray: Ray) : Boolean {
        val tx1 = (sx - ray.origin[0]) / ray.direction[0]
        val tx2 = (bx - ray.origin[0]) / ray.direction[0]

        var tmin = min(tx1,tx2)
        var tmax = max(tx1,tx2)

        var ty1 = (sy - ray.origin[1]) / ray.direction[1]
        var ty2 = (by - ray.origin[1]) / ray.direction[1]

        tmin = max(tmin,min(ty1,ty2))
        tmax = min(tmax,max(ty1,ty2))

        val tz1 = (sz - ray.origin[2]) * ray.direction[2]
        val tz2 = (bz - ray.origin[2]) * ray.direction[2]

        tmin = max(tmin,min(tz1,tz2))
        tmax = min(tmax,max(tz1,tz2))

        return tmax >= tmin;
    }
}

class AABBGPUList(aabbs: List<AABB>) : Closeable{
    val gpuBuffer: Int
    var aabbNum: Int = aabbs.size

    init {
        val pbuffer = IntArray(1)
        GLES20.glGenBuffers(1, pbuffer, 0)
        gpuBuffer = pbuffer[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gpuBuffer)

        val bufferSize = AABB.LINE_BUFFER_BYTE_NUM * aabbNum

        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, bufferSize, null, GLES20.GL_DYNAMIC_DRAW)

        for(aabbInd in aabbs.indices){
            GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER,
                AABB.LINE_BUFFER_BYTE_NUM*aabbInd,
                AABB.LINE_BUFFER_BYTE_NUM,
                aabbs[aabbInd].getLineBuffer()
            )
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun update(aabbs: List<AABB>){
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gpuBuffer)
        if(aabbs.size>aabbNum){
            aabbNum = aabbs.size
            val bufferSize = AABB.LINE_BUFFER_BYTE_NUM * aabbNum
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, bufferSize, null, GLES20.GL_DYNAMIC_DRAW)
        }
        for(aabbInd in aabbs.indices){
            GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER,
                AABB.LINE_BUFFER_BYTE_NUM*aabbInd,
                AABB.LINE_BUFFER_BYTE_NUM,
                aabbs[aabbInd].getLineBuffer()
            )
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    override fun close() {
        GLES20.glDeleteBuffers(1, intArrayOf(gpuBuffer), 0)
    }
}

fun filterUsingPlanes(points: FloatBuffer, allPlanes: Collection<Plane>) {
    val planeNormal = FloatArray(3)

    // Allocate the output buffer.
    val numPoints: Int = points.remaining() / PointCloudData.VALUES_PER_POINT
    var filteredPoints = 0

    // Check each plane against each point.
    for (plane in allPlanes) {
        if (plane.trackingState !== TrackingState.TRACKING || plane.getSubsumedBy() != null) {
            continue
        }

        // Compute the normal vector of the plane.
        val planePose: Pose = plane.getCenterPose()
        planePose.getTransformedAxis(1, 1.0f, planeNormal, 0)

        // Filter points that are too close to the plane.
        for (index in 0 until numPoints) {
            // Retrieves the next point.
            val x: Float = points.get(PointCloudData.VALUES_PER_POINT * index)
            val y: Float = points.get(PointCloudData.VALUES_PER_POINT * index + 1)
            val z: Float = points.get(PointCloudData.VALUES_PER_POINT * index + 2)

            // Transform point to be in world coordinates, to match plane info.
            val distance =
                ((x - planePose.tx()) * planeNormal[0] + (y - planePose.ty()) * planeNormal[1] + (z - planePose.tz()) * planeNormal[2])
            // Controls the size of objects detected.
            // Smaller values mean smaller objects will be kept.
            // Larger values will only allow detection of larger objects, but also helps reduce noise.
            if (abs(distance.toDouble()) > 0.03) {
                continue  // Keep this point, since it's far enough away from the plane.
            }

            // Invalidate points that are too close to planar surfaces.
            points.put(PointCloudData.VALUES_PER_POINT * index, 0.0f)
            points.put(PointCloudData.VALUES_PER_POINT * index + 1, 0.0f)
            points.put(PointCloudData.VALUES_PER_POINT * index + 2, 0.0f)
            points.put(PointCloudData.VALUES_PER_POINT * index + 3, 0.0f)
            filteredPoints++
        }
    }
    Log.d("PlaneFilterer", "filtered $filteredPoints points")
}

