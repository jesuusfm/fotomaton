package com.photobooth.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import java.nio.ByteBuffer

class YuvToRgbConverter(context: Context) {
    private val rs = RenderScript.create(context)
    private val scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

    private var pixelCount: Int = -1
    private lateinit var yuvBuffer: ByteBuffer
    private lateinit var inputAllocation: Allocation
    private lateinit var outputAllocation: Allocation

    @Synchronized
    fun yuvToRgb(image: Image, output: Bitmap) {
        if (image.format != ImageFormat.YUV_420_888) {
            throw IllegalArgumentException("Invalid image format")
        }

        val imageWidth = image.width
        val imageHeight = image.height

        if (pixelCount != imageWidth * imageHeight) {
            pixelCount = imageWidth * imageHeight
            yuvBuffer = ByteBuffer.allocateDirect(pixelCount * 3 / 2)
            
            inputAllocation = Allocation.createSized(rs, Element.U8(rs), yuvBuffer.capacity())
            outputAllocation = Allocation.createFromBitmap(rs, output)
        }

        yuvBuffer.clear()
        imageToByteBuffer(image, yuvBuffer)
        
        inputAllocation.copyFrom(yuvBuffer.array())
        scriptYuvToRgb.setInput(inputAllocation)
        scriptYuvToRgb.forEach(outputAllocation)
        outputAllocation.copyTo(output)
    }

    private fun imageToByteBuffer(image: Image, buffer: ByteBuffer) {
        val imageCrop = image.cropRect
        val imagePlanes = image.planes

        val rowData = ByteArray(imagePlanes[0].rowStride)
        for (planeIndex in imagePlanes.indices) {
            val plane = imagePlanes[planeIndex]
            val planeBuffer = plane.buffer

            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            val planeCrop = if (planeIndex == 0) {
                imageCrop
            } else {
                Rect(
                    imageCrop.left / 2,
                    imageCrop.top / 2,
                    imageCrop.right / 2,
                    imageCrop.bottom / 2
                )
            }

            val planeWidth = planeCrop.width()
            val planeHeight = planeCrop.height()

            planeBuffer.position(rowStride * planeCrop.top + pixelStride * planeCrop.left)
            for (row in 0 until planeHeight) {
                val length: Int
                if (pixelStride == 1 && rowStride == planeWidth) {
                    length = planeWidth
                    buffer.put(planeBuffer)
                } else {
                    length = (planeWidth - 1) * pixelStride + 1
                    planeBuffer.get(rowData, 0, length)
                    for (col in 0 until planeWidth) {
                        buffer.put(rowData[col * pixelStride])
                    }
                }
                if (row < planeHeight - 1) {
                    planeBuffer.position(planeBuffer.position() + rowStride - length)
                }
            }
        }
    }
}
