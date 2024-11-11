package com.cmder.pointcloudviewer

import android.content.Context
import android.graphics.BitmapFactory
import java.io.IOException

class DepthMapLoader {
    companion object {
        // 从 assets 或其他来源加载 PNG 深度图并生成点云
        @Throws(IOException::class)
        fun loadDepthMapAndGeneratePointCloud(
            context: Context,
            assetFileName: String?
        ): List<FloatArray> {
            val `is` = context.assets.open(assetFileName!!)
            val bitmap = BitmapFactory.decodeStream(`is`)
            val width = bitmap.width
            val height = bitmap.height
            val depthMap = Array(height) { FloatArray(width) }

            // 遍历图像的像素，提取红色通道值作为深度值
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    val red = (pixel shr 16) and 0xff // 提取红色通道的值

                    // 将红色通道的值归一化到 0.0 到 1.0 之间
                    val depthValue = red / 255.0f

                    depthMap[y][x] = depthValue
                }
            }

            // 使用提供的相机内参生成点云
            val fx = 238.31752014160156f
            val fy = 238.31752014160156f
            val cx = 158.93447875976562f
            val cy = 100.52653503417969f

            // 生成点云
            return convertDepthMapToPointCloud(depthMap, fx, fy, cx, cy)
        }

        // 将深度图转换为点云的 List<float[]>
        private fun convertDepthMapToPointCloud(
            depthMap: Array<FloatArray>,
            fx: Float,
            fy: Float,
            cx: Float,
            cy: Float
        ): List<FloatArray> {
            val pointCloud: MutableList<FloatArray> = ArrayList()

            val height = depthMap.size // 深度图的高度
            val width = depthMap[0].size // 深度图的宽度

            // 遍历每个像素，将其转换为3D点
            for (v in 0 until height) {
                for (u in 0 until width) {
                    val z = depthMap[v][u] * 10 // 获取深度值并假设最大深度为10（可以根据需要调整）

                    // 跳过没有深度值的像素
                    if (z == 0f) continue

                    // 使用相机内参将2D像素坐标 (u, v) 和深度 z 转换为 3D世界坐标 (X, Y, Z)
                    val x = (u - cx) * z / fx
                    val y = (v - cy) * z / fy

                    // 将点加入点云列表
                    pointCloud.add(floatArrayOf(x, -y, -z)) // 根据需求翻转 y 和 z 轴
                }
            }

            return pointCloud
        }
    }
}
