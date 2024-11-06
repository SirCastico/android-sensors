package com.example.app

import com.google.ar.core.CameraIntrinsics
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.exceptions.NotYetAvailableException
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.experimental.and

class DepthData(
    /** depth buffer millimeters  */
    private val depth: ShortBuffer,
    private val depthWidth: Int,
    private val depthHeight: Int,
    private val depthConfidence: FloatBuffer,
    /** Buffer of RGB color values.  */
    private val colors: FloatBuffer,
    /** The timestamp in nanoseconds when the raw depth image was observed.  */
    private val timestamp: Long,
    private val cameraIntrinsics: CameraIntrinsics,
    private val cameraPose: Pose
) {

    fun serializeToFile(fileOut: FileOutputStream) {
        val camMat = FloatArray(16)
        this.cameraPose.toMatrix(camMat, 0)
        var camMatStr = ""

        for (v in camMat) {
            camMatStr += "${v}\n"
        }

        val intrinsicsDimensions: IntArray = this.cameraIntrinsics.getImageDimensions()
        val fx: Float =
            cameraIntrinsics.getFocalLength()[0] * depthWidth / intrinsicsDimensions[0]
        val fy: Float =
            cameraIntrinsics.getFocalLength()[1] * depthHeight / intrinsicsDimensions[1]
        val cx: Float =
            cameraIntrinsics.getPrincipalPoint()[0] * depthWidth / intrinsicsDimensions[0]
        val cy: Float =
            cameraIntrinsics.getPrincipalPoint()[1] * depthHeight / intrinsicsDimensions[1]

        val header = "depth-size\n${this.depthWidth} ${this.depthHeight}\n" +
                "timestamp\n${this.timestamp}\n" +
                "intrinsics\n$fx $fy $cx $cy\n" +
                "camera-pose\n${camMatStr}\n"

        fileOut.write(header.toByteArray())

        fileOut.write("depth\n".toByteArray())
        while (this.depth.hasRemaining()){
            fileOut.write("${this.depth.get()}\n".toByteArray())
        }

        fileOut.write("confidence\n".toByteArray())
        while (this.depthConfidence.hasRemaining()){
            fileOut.write("${this.depthConfidence.get()}\n".toByteArray())
        }

        fileOut.write("colors\n".toByteArray())
        while (this.colors.hasRemaining()){
            fileOut.write("${this.colors.get()} ${this.colors.get()} ${this.colors.get()}\n".toByteArray())
        }
    }
}

fun createDepthData(frame: Frame): DepthData? {
    try {
        frame.acquireCameraImage().use { cameraImage ->
            frame.acquireRawDepthImage16Bits().use { depthImage ->
                frame.acquireRawDepthConfidenceImage().use { confidenceImage ->
                    val intrinsics = frame.camera.textureIntrinsics
                    val depthBuf =
                        depthImage.planes[0].buffer.order(ByteOrder.nativeOrder())
                            .asShortBuffer().asReadOnlyBuffer()

                    val depth = ShortBuffer.allocate(depthBuf.remaining())
                    depth.put(depthBuf)
                    depth.rewind()

                    val imageRegionCoordinates =
                        PointCloudHelper.getImageCoordinatesForFullTexture(frame)

                    val colors = PointCloudHelper.convertImageToColorBufferDepthSized(
                        cameraImage,
                        depthImage,
                        imageRegionCoordinates
                    )

                    val depthConfidencePlane = confidenceImage.planes[0]
                    val depthConfidenceBuf =
                        depthConfidencePlane.buffer.order(ByteOrder.nativeOrder()).asReadOnlyBuffer()

                    val depthConfidence =
                        FloatBuffer.allocate(depthConfidenceBuf.remaining())

                    for (y in 0..<depthImage.height){
                        for (x in 0..<depthImage.width) {
                            val confidenceVal: Byte =
                                depthConfidenceBuf.get(
                                    y * depthConfidencePlane.rowStride +
                                            x * depthConfidencePlane.pixelStride
                                )
                            val confidenceNormalized =
                                ((confidenceVal and 0xff.toByte()).toFloat()) / 255.0f

                            depthConfidence.put(confidenceNormalized)
                        }
                    }
                    depthConfidence.rewind()
                    return DepthData(
                        depth, depthImage.width, depthImage.height,
                        depthConfidence, colors, depthImage.timestamp, intrinsics,
                        frame.camera.pose
                    )
                }
            }
        }
    } catch (e: NotYetAvailableException) {
        // This normally means that depth data is not available yet. This is normal so we will not
        // spam the logcat with this.
    }

    return null
}
