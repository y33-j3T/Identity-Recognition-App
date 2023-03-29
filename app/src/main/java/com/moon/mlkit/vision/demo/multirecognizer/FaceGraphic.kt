/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.moon.mlkit.vision.demo.multirecognizer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.face.FaceLandmark.LandmarkType
import com.moon.mlkit.vision.demo.GraphicOverlay
import com.moon.mlkit.vision.demo.GraphicOverlay.Graphic
import java.util.*
import kotlin.math.abs
import kotlin.math.max

/**
 * Graphic instance for rendering face position, contour, and landmarks within the associated
 * graphic overlay view.
 */
class FaceGraphic constructor(
  overlay: GraphicOverlay?,
  private val face: Face,
  private val faceName: String,
  private val faceDist: Double,
  private val earName: String,
  private val earDist: Double
) : Graphic(overlay) {
  private val facePositionPaint: Paint
  private val numColors = COLORS.size
  private val idPaints = Array(numColors) { Paint() }
  private val boxPaints = Array(numColors) { Paint() }
  private val labelPaints = Array(numColors) { Paint() }

  init {
    val selectedColor = Color.WHITE
    facePositionPaint = Paint()
    facePositionPaint.color = selectedColor
    for (i in 0 until numColors) {
      idPaints[i] = Paint()
      idPaints[i].color = COLORS[i][0]
      idPaints[i].textSize = ID_TEXT_SIZE
      boxPaints[i] = Paint()
      boxPaints[i].color = COLORS[i][1]
      boxPaints[i].style = Paint.Style.STROKE
      boxPaints[i].strokeWidth = BOX_STROKE_WIDTH
      labelPaints[i] = Paint()
      labelPaints[i].color = COLORS[i][1]
      labelPaints[i].style = Paint.Style.FILL
    }
  }

  /** Draws the face annotations for position on the supplied canvas. */
  override fun draw(canvas: Canvas) {
    // Draws a circle at the position of the detected face, with the face's track id below.
    val x = translateX(face.boundingBox.centerX().toFloat())
    val y = translateY(face.boundingBox.centerY().toFloat())
//    canvas.drawCircle(x, y, FACE_POSITION_RADIUS, facePositionPaint)

    // Calculate positions.
    val left = x - scale(face.boundingBox.width() / 2.0f)
    val top = y - scale(face.boundingBox.height() / 2.0f)
    val right = x + scale(face.boundingBox.width() / 2.0f)
    val bottom = y + scale(face.boundingBox.height() / 2.0f)

    val lineHeight = ID_TEXT_SIZE + BOX_STROKE_WIDTH
    var yLabelOffset: Float = if (face.trackingId == null) 0f else -lineHeight

    // Decide color based on face ID
    val colorID = if (face.trackingId == null) 0 else abs(face.trackingId!! % NUM_COLORS)

    // Calculate width and height of label box
    var textWidth = idPaints[colorID].measureText("ID: " + face.trackingId)
    if (face.smilingProbability != null) {
      yLabelOffset -= lineHeight
      textWidth = max(
        textWidth,
        idPaints[colorID].measureText(
          String.format(Locale.US, "Happiness: %.2f", face.smilingProbability)
        )
      )
    }
    if (face.leftEyeOpenProbability != null) {
      yLabelOffset -= lineHeight
      textWidth =
        max(
          textWidth,
          idPaints[colorID].measureText(
            String.format(Locale.US, "Left eye open: %.2f", face.leftEyeOpenProbability)
          )
        )
    }
    if (face.rightEyeOpenProbability != null) {
      yLabelOffset -= lineHeight
      textWidth =
        max(
          textWidth,
          idPaints[colorID].measureText(
            String.format(Locale.US, "Right eye open: %.2f", face.rightEyeOpenProbability)
          )
        )
    }

    yLabelOffset -= 3 * lineHeight // x, y, z euler labels
    textWidth =
      max(
        textWidth,
        idPaints[colorID].measureText(
          String.format(Locale.US, "EulerX: %.2f", face.headEulerAngleX)
        )
      )
    textWidth =
      max(
        textWidth,
        idPaints[colorID].measureText(
          String.format(Locale.US, "EulerY: %.2f", face.headEulerAngleY)
        )
      )
    textWidth =
      max(
        textWidth,
        idPaints[colorID].measureText(
          String.format(Locale.US, "EulerZ: %.2f", face.headEulerAngleZ)
        )
      )

    yLabelOffset -= lineHeight  // faceName label
    textWidth =
      max(
        textWidth,
        idPaints[colorID].measureText(
          String.format(Locale.US, "Name: %s", faceName)
        )
      )

    yLabelOffset -= lineHeight  // faceDist label
    textWidth =
      max(
        textWidth,
        idPaints[colorID].measureText(
          String.format(Locale.US, "Dissimilarity: %.4f", faceDist)
        )
      )

    // Draw labels
    canvas.drawRect(
      left - BOX_STROKE_WIDTH,
      top + yLabelOffset,
      left + textWidth + 2 * BOX_STROKE_WIDTH,
      top,
      labelPaints[colorID]
    )
    yLabelOffset += ID_TEXT_SIZE
    canvas.drawRect(left, top, right, bottom, boxPaints[colorID])
    if (face.trackingId != null) {
      canvas.drawText("ID: " + face.trackingId, left, top + yLabelOffset, idPaints[colorID])
      yLabelOffset += lineHeight
    }

    // Draws all face contours.
//    for (contour in face.allContours) {
//      for (point in contour.points) {
//        canvas.drawCircle(
//          translateX(point.x),
//          translateY(point.y),
//          FACE_POSITION_RADIUS,
//          facePositionPaint
//        )
//      }
//    }

    // Draws smiling and left/right eye open probabilities.
    if (face.smilingProbability != null) {
      canvas.drawText(
        "Smiling: " + String.format(Locale.US, "%.2f", face.smilingProbability),
        left,
        top + yLabelOffset,
        idPaints[colorID]
      )
      yLabelOffset += lineHeight
    }

//    val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
//    if (face.leftEyeOpenProbability != null) {
//      canvas.drawText(
//        "Left eye open: " + String.format(Locale.US, "%.2f", face.leftEyeOpenProbability),
//        left,
//        top + yLabelOffset,
//        idPaints[colorID]
//      )
//      yLabelOffset += lineHeight
//    }
//    if (leftEye != null) {
//      val leftEyeLeft =
//        translateX(leftEye.position.x) - idPaints[colorID].measureText("Left Eye") / 2.0f
//      canvas.drawRect(
//        leftEyeLeft - BOX_STROKE_WIDTH,
//        translateY(leftEye.position.y) + ID_Y_OFFSET - ID_TEXT_SIZE,
//        leftEyeLeft + idPaints[colorID].measureText("Left Eye") + BOX_STROKE_WIDTH,
//        translateY(leftEye.position.y) + ID_Y_OFFSET + BOX_STROKE_WIDTH,
//        labelPaints[colorID]
//      )
//      canvas.drawText(
//        "Left Eye",
//        leftEyeLeft,
//        translateY(leftEye.position.y) + ID_Y_OFFSET,
//        idPaints[colorID]
//      )
//    }
//
//    val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
//    if (face.rightEyeOpenProbability != null) {
//      canvas.drawText(
//        "Right eye open: " + String.format(Locale.US, "%.2f", face.rightEyeOpenProbability),
//        left,
//        top + yLabelOffset,
//        idPaints[colorID]
//      )
//      yLabelOffset += lineHeight
//    }
//    if (rightEye != null) {
//      val rightEyeLeft =
//        translateX(rightEye.position.x) - idPaints[colorID].measureText("Right Eye") / 2.0f
//      canvas.drawRect(
//        rightEyeLeft - BOX_STROKE_WIDTH,
//        translateY(rightEye.position.y) + ID_Y_OFFSET - ID_TEXT_SIZE,
//        rightEyeLeft + idPaints[colorID].measureText("Right Eye") + BOX_STROKE_WIDTH,
//        translateY(rightEye.position.y) + ID_Y_OFFSET + BOX_STROKE_WIDTH,
//        labelPaints[colorID]
//      )
//      canvas.drawText(
//        "Right Eye",
//        rightEyeLeft,
//        translateY(rightEye.position.y) + ID_Y_OFFSET,
//        idPaints[colorID]
//      )
//    }



    canvas.drawText("EulerX: " + face.headEulerAngleX, left, top + yLabelOffset, idPaints[colorID])
    yLabelOffset += lineHeight
    canvas.drawText("EulerY: " + face.headEulerAngleY, left, top + yLabelOffset, idPaints[colorID])
    yLabelOffset += lineHeight
    canvas.drawText("EulerZ: " + face.headEulerAngleZ, left, top + yLabelOffset, idPaints[colorID])
    yLabelOffset += lineHeight

    // Draw identity
    canvas.drawText("Name: $faceName", left, top + yLabelOffset, idPaints[colorID])
    yLabelOffset += lineHeight
    canvas.drawText("Dissimilarity: " + String.format("%.4f", faceDist), left, top + yLabelOffset, idPaints[colorID])
    yLabelOffset += lineHeight

    // Draw facial landmarks
    if (face.headEulerAngleY < 0) {  // face turn left
      drawFaceLandmark(canvas, FaceLandmark.RIGHT_EAR)
    } else {  // face turn right
      drawFaceLandmark(canvas, FaceLandmark.LEFT_EAR)
    }
  }

  private fun drawFaceLandmark(canvas: Canvas, @LandmarkType landmarkType: Int) {
    val faceLandmark = face.getLandmark(landmarkType) ?: return
    val colorID = if (face.trackingId == null) 0 else abs(face.trackingId!! % NUM_COLORS)

    canvas.drawCircle(
      translateX(faceLandmark.position.x),
      translateY(faceLandmark.position.y),
      FACE_POSITION_RADIUS,
      facePositionPaint
    )

    var earLeft: Float? = null
    val earTop: Float = translateY(faceLandmark.position.y) - scale(face.boundingBox.height() * EAR_HEIGHT_PROPORTION / 2.0f)
    var earRight: Float? = null
    val earBottom: Float = translateY(faceLandmark.position.y) + scale(face.boundingBox.height() * EAR_HEIGHT_PROPORTION / 2.0f)
    if (landmarkType == FaceLandmark.RIGHT_EAR) {
      if (isImageFlipped) {
        earLeft = translateX(faceLandmark.position.x) - scale(face.boundingBox.width() * EAR_WIDTH_PROPORTION * 0.75f)
        earRight = translateX(faceLandmark.position.x) + scale(face.boundingBox.width() * EAR_WIDTH_PROPORTION * 0.25f)
      } else {
        earLeft = translateX(faceLandmark.position.x) - scale(face.boundingBox.width() * EAR_WIDTH_PROPORTION * 0.25f)
        earRight = translateX(faceLandmark.position.x) + scale(face.boundingBox.width() * EAR_WIDTH_PROPORTION * 0.75f)
      }
    } else if (landmarkType == FaceLandmark.LEFT_EAR) {
      if (isImageFlipped) {
        earLeft = translateX(faceLandmark.position.x) - scale(face.boundingBox.width() * EAR_WIDTH_PROPORTION * 0.25f)
        earRight = translateX(faceLandmark.position.x) + scale(face.boundingBox.width() * EAR_WIDTH_PROPORTION * 0.75f)
      } else {
        earLeft = translateX(faceLandmark.position.x) - scale(face.boundingBox.width() * EAR_WIDTH_PROPORTION * 0.75f)
        earRight = translateX(faceLandmark.position.x) + scale(face.boundingBox.width() * EAR_WIDTH_PROPORTION * 0.25f)
      }
    }

    if (landmarkType == FaceLandmark.LEFT_EAR || landmarkType == FaceLandmark.RIGHT_EAR) {
      canvas.drawRect(earLeft!!, earTop, earRight!!, earBottom, boxPaints[colorID])

      var earTextWidth = idPaints[colorID].measureText("Name: $earName")
      earTextWidth = max(
        earTextWidth,
        idPaints[colorID].measureText("Dissimilarity: " + String.format("%.4f", earDist))
      )

      val leftEarLeft = translateX(faceLandmark.position.x) - earTextWidth / 2.0f
      canvas.drawRect(
        leftEarLeft - BOX_STROKE_WIDTH,
        translateY(faceLandmark.position.y) + ID_Y_OFFSET - ID_TEXT_SIZE,
        leftEarLeft + earTextWidth + BOX_STROKE_WIDTH,
        translateY(faceLandmark.position.y) + 2 * ID_Y_OFFSET + BOX_STROKE_WIDTH,
        labelPaints[colorID]
      )
      canvas.drawText("Name: $earName", leftEarLeft, translateY(faceLandmark.position.y) + ID_Y_OFFSET, idPaints[colorID])
      canvas.drawText("Dissimilarity: " + String.format("%.4f", earDist), leftEarLeft, translateY(faceLandmark.position.y) + 2 * ID_Y_OFFSET, idPaints[colorID])
    }
  }

  companion object {
    private const val FACE_POSITION_RADIUS = 8.0f
    private const val ID_TEXT_SIZE = 30.0f
    private const val ID_Y_OFFSET = 40.0f
    private const val BOX_STROKE_WIDTH = 5.0f
    private const val NUM_COLORS = 10
    private val COLORS =
      arrayOf(
        intArrayOf(Color.BLACK, Color.WHITE),
        intArrayOf(Color.WHITE, Color.MAGENTA),
        intArrayOf(Color.BLACK, Color.LTGRAY),
        intArrayOf(Color.WHITE, Color.RED),
        intArrayOf(Color.WHITE, Color.BLUE),
        intArrayOf(Color.WHITE, Color.DKGRAY),
        intArrayOf(Color.BLACK, Color.CYAN),
        intArrayOf(Color.BLACK, Color.YELLOW),
        intArrayOf(Color.WHITE, Color.BLACK),
        intArrayOf(Color.BLACK, Color.GREEN)
      )

    // ears
    const val EAR_WIDTH_PROPORTION = 0.20f  // ear-face width ratio
    const val EAR_HEIGHT_PROPORTION = 0.33f  // ear-face height ratio
  }
}
