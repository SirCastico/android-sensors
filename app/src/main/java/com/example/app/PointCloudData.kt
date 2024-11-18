package com.example.app

import android.util.Log
import com.example.app.PointCloudHelper.convertDepthTo3dCameraSpacePointBuffer
import com.example.app.PointCloudHelper.convertDepthTo3dWorldSpacePointBuffer
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import org.apache.commons.math3.ml.clustering.Cluster
import org.apache.commons.math3.ml.clustering.Clusterable
import java.nio.FloatBuffer
import kotlin.math.abs


class PointCloudData(
    val points: FloatBuffer,
    val cameraAnchor: Anchor,
    val cameraTransf: FloatArray
){
    companion object Static{
        const val VALUES_PER_POINT = 4
        const val POINT_SIZE_BYTES = 4 * Float.SIZE_BYTES

        fun create(session: Session, frame: Frame, pointLimit: Int): PointCloudData? {
            try {
                val depthImage = frame.acquireRawDepthImage16Bits()
                val confidenceImage = frame.acquireRawDepthConfidenceImage()

                val intrinsics = frame.camera.textureIntrinsics

                //val points = convertDepthTo3dCameraSpacePointBuffer(
                //    depthImage, confidenceImage, intrinsics, pointLimit
                //)
                val ctransf = FloatArray(16)
                frame.camera.pose.toMatrix(ctransf,0)
                val points = convertDepthTo3dWorldSpacePointBuffer(
                    depthImage, confidenceImage, intrinsics, pointLimit, ctransf
                )

                //filterUsingPlanes(points, session.getAllTrackables())

                depthImage.close()
                confidenceImage.close()

                val cameraAnchor = session.createAnchor(frame.camera.pose)

                return PointCloudData(points, cameraAnchor, ctransf)
            } catch (e: NotYetAvailableException) {
                // This normally means that depth data is not available yet. This is normal so we will not
                // spam the logcat with this.
            }
            return null
        }
    }
}

class AABB{
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
}

@JvmInline
value class Point(val data: FloatArray) : Clusterable {
    override fun getPoint(): DoubleArray {
        return doubleArrayOf(data[0].toDouble(),data[1].toDouble(),data[2].toDouble())
    }
}

fun calculateAABBs(clusters: List<Cluster<Point>>): List<AABB>{
    val aabbs = MutableList(0){AABB()}
    for (cluster in clusters){
        val aabb = AABB()
        for (point in cluster.points){
            aabb.update(point.data[0], point.data[1], point.data[2])
        }
        aabbs.add(aabb)
    }
    return aabbs
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

fun getDepthBuffer(frame: Frame): FloatBuffer? {
    try {
        val depthImage = frame.acquireRawDepthImage16Bits()
        val confidenceImage = frame.acquireRawDepthConfidenceImage()



        depthImage.close()
        confidenceImage.close()

    } catch (e: NotYetAvailableException) {
        // This normally means that depth data is not available yet. This is normal so we will not
        // spam the logcat with this.
    }
    return null
}
