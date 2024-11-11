package com.cmder.pointcloudviewer

import android.content.Context
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class PLYParser {
    companion object {
        @Throws(IOException::class)
        fun readPLY(context: Context, assetFileName: String?): List<FloatArray> {
            val vertices: MutableList<FloatArray> = ArrayList()

            val `is` = context.assets.open(assetFileName!!)
            val br = BufferedReader(InputStreamReader(`is`))

            var line: String
            var headerEnded = false
            while ((br.readLine().also { line = it }) != null) {
                if (headerEnded) {
                    val parts =
                        line.trim { it <= ' ' }.split("\\s+".toRegex())
                            .dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    val x = parts[0].toFloat()
                    val y = parts[1].toFloat()
                    val z = parts[2].toFloat()
                    vertices.add(floatArrayOf(x, y, z))
                } else if (line.startsWith("end_header")) {
                    headerEnded = true
                }
            }
            br.close()
            return vertices
        }
    }
}

