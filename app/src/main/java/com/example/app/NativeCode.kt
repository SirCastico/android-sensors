package com.example.app

import java.nio.FloatBuffer
import java.nio.IntBuffer

class ClusterResults(val aabb: AABB, val pointCount: Int)

class NativeCode{
    companion object {
        init {
            System.loadLibrary("app")
        }
    }

    // buffer of 3d points, return cluster aabbs
    external fun cluster(pointBuffer: FloatBuffer, count: Int, eps: Float, nPts: Int): Array<ClusterResults>?
}