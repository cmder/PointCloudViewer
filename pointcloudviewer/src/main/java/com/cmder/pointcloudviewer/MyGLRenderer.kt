package com.cmder.pointcloudviewer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MyGLRenderer(plyVertices: List<FloatArray>) : GLSurfaceView.Renderer {
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var rotationX = 0.0f
    private var rotationY = 0.0f
    private var scale = 1.0f

    private val vertexBuffer: FloatBuffer
    private var program = 0
    private var positionHandle = 0

    private var boundingRadius = 1.0f // 点云的包围球半径

    // 顶点着色器代码
    private val vertexShaderCode = "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix * vPosition;" +
            "}"

    // 片段着色器代码
    private val fragmentShaderCode = "precision mediump float;" +
            "uniform vec4 vColor;" +
            "void main() {" +
            "  gl_FragColor = vColor;" +
            "}"

    private val vertices = FloatArray(plyVertices.size * 3) // 从PLY文件解析的顶点

    init {
        var sumX = 0f
        var sumY = 0f
        var sumZ = 0f
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var maxZ = Float.MIN_VALUE

        // 遍历点云，计算几何中心和包围盒
        for (i in plyVertices.indices) {
            val vertex = plyVertices[i]
            vertices[i * 3] = vertex[0]
            vertices[i * 3 + 1] = vertex[1]
            vertices[i * 3 + 2] = vertex[2]

            // 累加坐标值以计算平均值
            sumX += vertex[0]
            sumY += vertex[1]
            sumZ += vertex[2]

            // 更新包围盒
            minX = min(minX.toDouble(), vertex[0].toDouble()).toFloat()
            minY = min(minY.toDouble(), vertex[1].toDouble()).toFloat()
            minZ = min(minZ.toDouble(), vertex[2].toDouble()).toFloat()

            maxX = max(maxX.toDouble(), vertex[0].toDouble()).toFloat()
            maxY = max(maxY.toDouble(), vertex[1].toDouble()).toFloat()
            maxZ = max(maxZ.toDouble(), vertex[2].toDouble()).toFloat()
        }

        // 计算几何中心
        val centerX = sumX / plyVertices.size
        val centerY = sumY / plyVertices.size
        val centerZ = sumZ / plyVertices.size

        // 将几何中心平移到原点
        for (i in plyVertices.indices) {
            vertices[i * 3] -= centerX
            vertices[i * 3 + 1] -= centerY
            vertices[i * 3 + 2] -= centerZ
        }

        // 计算包围球半径
        val dx = maxX - minX
        val dy = maxY - minY
        val dz = maxZ - minZ
        boundingRadius = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat() / 2.0f

        val bb = ByteBuffer.allocateDirect(vertices.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(vertices)
        vertexBuffer.position(0)

        Matrix.setIdentityM(modelMatrix, 0) // 初始化模型矩阵为单位矩阵
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // 编译着色器
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        // 创建OpenGL程序并链接着色器
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
    }

    override fun onDrawFrame(gl: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // 使用程序
        GLES20.glUseProgram(program)

        // 设置视图矩阵，确保相机正对几何中心，距离足够显示整个点云
        val cameraDistance = boundingRadius * 2.5f // 让相机远离原点，确保点云完整显示
        Matrix.setLookAtM(
            viewMatrix, 0,
            0f, 0f, cameraDistance,  // 相机位置
            0f, 0f, 0f,  // 观察原点
            0f, 1f, 0f
        ) // 上向量

        // 计算投影矩阵和视图矩阵的乘积
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // 应用缩放和旋转变换
        Matrix.setIdentityM(modelMatrix, 0)

        // 旋转和缩放
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale)
        Matrix.rotateM(modelMatrix, 0, rotationX, 0f, 1f, 0f) // 绕Y轴旋转
        Matrix.rotateM(modelMatrix, 0, rotationY, 1f, 0f, 0f) // 绕X轴旋转

        // 将模型矩阵与MVP矩阵相乘
        val finalMatrix = FloatArray(16)
        Matrix.multiplyMM(finalMatrix, 0, mvpMatrix, 0, modelMatrix, 0)

        // 传递最终的MVP矩阵给着色器
        val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, finalMatrix, 0)

        // 获取顶点位置句柄
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")

        // 启用顶点属性数组
        GLES20.glEnableVertexAttribArray(positionHandle)

        // 准备顶点数据
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        // 设置颜色
        val colorHandle = GLES20.glGetUniformLocation(program, "vColor")
        GLES20.glUniform4fv(colorHandle, 1, floatArrayOf(0.0f, 1.0f, 0.0f, 1.0f), 0)

        // 绘制点云
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, vertices.size / 3)

        // 禁用顶点数组
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        // 设置投影矩阵
        val ratio = width.toFloat() / height
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 100f)
    }

    // 设置旋转角度
    fun setRotation(dx: Float, dy: Float) {
        rotationX += dx
        rotationY += dy
    }

    // 设置缩放比例
    fun setScale(scaleFactor: Float) {
        scale *= scaleFactor
        if (scale < 0.1f) scale = 0.1f // 防止缩放过小

        if (scale > 10.0f) scale = 10.0f // 防止缩放过大
    }

    companion object {
        // 加载着色器的方法
        fun loadShader(type: Int, shaderCode: String?): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}
