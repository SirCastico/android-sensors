package com.example.app

import java.nio.FloatBuffer
import java.nio.IntBuffer


class NativeCode{
    companion object {
        init {
            System.loadLibrary("app")
        }
    }

    external fun getVal(): Int

    // 3d points, return buffers with cluster indexes associated with each point
    external fun cluster(pointBuffer: FloatBuffer, count: Int, eps: Float, nPts: Int): Array<AABB>?
}