package com.photobooth.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

object FaceFilterHelper {

    private const val TAG = "FaceFilterHelper"

    fun applyFaceFilter(bitmap: Bitmap, faces: List<Face>, filterBitmap: Bitmap, filterType: String): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
        Log.d(TAG, "Applying $filterType filter to ${faces.size} faces")
        for (face in faces) {
            try {
                when (filterType) {
                    "hat"      -> applyHat(canvas, face, filterBitmap, paint)
                    "ears"     -> applyEars(canvas, face, filterBitmap, paint)
                    "full"     -> applyFullFaceFilter(canvas, face, filterBitmap, paint)
                    "mask"     -> applyMask(canvas, face, filterBitmap, paint)
                    "nose"     -> applyNose(canvas, face, filterBitmap, paint)
                    "glasses"  -> applyGlasses(canvas, face, filterBitmap, paint)
                    "mustache" -> applyMustache(canvas, face, filterBitmap, paint)
                    else       -> Log.w(TAG, "Unknown filter type: $filterType")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying filter to face: ${e.message}", e)
            }
        }
        return result
    }

    private fun applyMustache(canvas: Canvas, face: Face, filterBitmap: Bitmap, paint: Paint) {
        val noseBase   = face.getLandmark(FaceLandmark.NOSE_BASE) ?: return
        val mouthLeft  = face.getLandmark(FaceLandmark.MOUTH_LEFT) ?: return
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT) ?: return
        val centerX = noseBase.position.x
        val centerY = (noseBase.position.y + (mouthLeft.position.y + mouthRight.position.y) / 2f) / 2f
        val mouthWidth = Math.abs(mouthRight.position.x - mouthLeft.position.x)
        val filterWidth = mouthWidth * 1.3f
        val filterHeight = filterBitmap.height * filterWidth / filterBitmap.width
        drawRotatedFilter(canvas, filterBitmap, centerX, centerY, filterWidth, filterHeight, face.headEulerAngleZ, paint)
    }

    private fun applyHat(canvas: Canvas, face: Face, filterBitmap: Bitmap, paint: Paint) {
        val bounds = face.boundingBox
        val centerX = bounds.exactCenterX()
        val centerY = bounds.top - bounds.height() * 0.3f
        val filterWidth = bounds.width() * 1.2f
        val filterHeight = filterBitmap.height * filterWidth / filterBitmap.width
        drawRotatedFilter(canvas, filterBitmap, centerX, centerY, filterWidth, filterHeight, face.headEulerAngleZ, paint)
    }

    private fun applyGlasses(canvas: Canvas, face: Face, filterBitmap: Bitmap, paint: Paint) {
        val leftEye  = face.getLandmark(FaceLandmark.LEFT_EYE) ?: return
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE) ?: return
        val centerX = (leftEye.position.x + rightEye.position.x) / 2f
        val centerY = (leftEye.position.y + rightEye.position.y) / 2f
        val eyeDistance = Math.abs(rightEye.position.x - leftEye.position.x)
        val filterWidth = eyeDistance * 2.5f
        val filterHeight = filterBitmap.height * filterWidth / filterBitmap.width
        drawRotatedFilter(canvas, filterBitmap, centerX, centerY, filterWidth, filterHeight, face.headEulerAngleZ, paint)
    }

    private fun applyMask(canvas: Canvas, face: Face, filterBitmap: Bitmap, paint: Paint) {
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE) ?: return
        val bounds = face.boundingBox
        val centerX = bounds.exactCenterX()
        val centerY = noseBase.position.y + bounds.height() * 0.2f
        val filterWidth = bounds.width() * 0.9f
        val filterHeight = filterBitmap.height * filterWidth / filterBitmap.width
        drawRotatedFilter(canvas, filterBitmap, centerX, centerY, filterWidth, filterHeight, face.headEulerAngleZ, paint)
    }

    private fun applyEars(canvas: Canvas, face: Face, filterBitmap: Bitmap, paint: Paint) {
        val bounds = face.boundingBox
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val filterWidth = bounds.width() * 1.8f
        val filterHeight = filterBitmap.height * filterWidth / filterBitmap.width
        drawRotatedFilter(canvas, filterBitmap, centerX, centerY, filterWidth, filterHeight, face.headEulerAngleZ, paint)
    }

    private fun applyNose(canvas: Canvas, face: Face, filterBitmap: Bitmap, paint: Paint) {
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE) ?: return
        val bounds = face.boundingBox
        val centerX = noseBase.position.x
        val centerY = noseBase.position.y
        val filterWidth = bounds.width() * 0.4f
        val filterHeight = filterBitmap.height * filterWidth / filterBitmap.width
        drawRotatedFilter(canvas, filterBitmap, centerX, centerY, filterWidth, filterHeight, face.headEulerAngleZ, paint)
    }

    private fun applyFullFaceFilter(canvas: Canvas, face: Face, filterBitmap: Bitmap, paint: Paint) {
        val bounds = face.boundingBox
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val filterWidth = bounds.width() * 1.1f
        val filterHeight = bounds.height() * 1.1f
        drawRotatedFilter(canvas, filterBitmap, centerX, centerY, filterWidth, filterHeight, face.headEulerAngleZ, paint)
    }

    private fun drawRotatedFilter(
        canvas: Canvas, filterBitmap: Bitmap,
        centerX: Float, centerY: Float,
        width: Float, height: Float,
        rotationZ: Float, paint: Paint
    ) {
        val matrix = Matrix()
        val scaleX = width / filterBitmap.width
        val scaleY = height / filterBitmap.height
        matrix.postScale(scaleX, scaleY)
        matrix.postRotate(rotationZ, width / 2f, height / 2f)
        matrix.postTranslate(centerX - width / 2f, centerY - height / 2f)
        canvas.drawBitmap(filterBitmap, matrix, paint)
    }
}
