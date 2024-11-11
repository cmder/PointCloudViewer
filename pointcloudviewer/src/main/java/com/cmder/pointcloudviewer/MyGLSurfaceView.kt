package com.cmder.pointcloudviewer

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import java.io.IOException

class MyGLSurfaceView(context: Context?) : GLSurfaceView(context) {
    private val renderer: MyGLRenderer
    private val gestureDetector: GestureDetector
    private val scaleGestureDetector: ScaleGestureDetector

    init {
        setEGLContextClientVersion(2) // OpenGL ES 2.0

        val vertices: List<FloatArray>

        //        try {
//            vertices = PLYParser.readPLY(context, "pointcloud.ply");
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
        try {
            vertices = DepthMapLoader.loadDepthMapAndGeneratePointCloud(context!!, "depthmap.png")
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        renderer = MyGLRenderer(vertices)

        gestureDetector = GestureDetector(context, GestureListener())
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())

        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 传递事件给手势检测器
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        private var previousX = 0f
        private var previousY = 0f

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val dx = e2.x - previousX
            val dy = e2.y - previousY
            renderer.setRotation(dx, dy)
            requestRender() // 触发重新绘制
            previousX = e2.x
            previousY = e2.y
            return true
        }
    }

    private inner class ScaleListener : SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            renderer.setScale(scaleFactor)
            requestRender() // 触发重新绘制
            return true
        }
    }
}

