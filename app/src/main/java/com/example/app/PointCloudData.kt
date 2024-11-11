package com.example.app

import com.example.app.PointCloudHelper.convertDepthTo3dCameraSpacePointBuffer
import com.example.app.PointCloudHelper.convertDepthTo3dWorldSpacePointBuffer
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.FloatBuffer


class PointCloudData(
    val points: FloatBuffer,
    val cameraPose: Pose,
){
    companion object Static{
        const val VALUES_PER_POINT = 4
        const val POINT_SIZE_BYTES = 4 * Float.SIZE_BYTES

        fun create(frame: Frame, cameraPose: Pose, pointLimit: Int): PointCloudData? {
            try {
                val depthImage = frame.acquireRawDepthImage16Bits()
                val confidenceImage = frame.acquireRawDepthConfidenceImage()

                val intrinsics = frame.camera.textureIntrinsics
                //val transform = FloatArray(16)
                //cameraPose.toMatrix(transform,0)

                val points = convertDepthTo3dCameraSpacePointBuffer(
                    depthImage, confidenceImage, intrinsics, pointLimit
                )

                depthImage.close()
                confidenceImage.close()

                return PointCloudData(points, cameraPose)
            } catch (e: NotYetAvailableException) {
                // This normally means that depth data is not available yet. This is normal so we will not
                // spam the logcat with this.
            }
            return null
        }
    }
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
