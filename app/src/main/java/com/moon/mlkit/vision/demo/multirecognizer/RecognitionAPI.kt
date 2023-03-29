package com.moon.mlkit.vision.demo.multirecognizer

import android.util.Base64
import android.util.Log
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import com.moon.mlkit.vision.demo.BitmapUtils
import com.moon.mlkit.vision.demo.FrameMetadata
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import kotlin.math.roundToInt
import com.google.gson.GsonBuilder

class RecognitionAPI {
  companion object {
  private val client = OkHttpClient()
  private const val URL_SAGEMAKER = "https://d4nlkzrs4e.execute-api.ap-southeast-1.amazonaws.com/test/identity_recognition"
//  private const val URL_SAGEMAKER = "http://172.31.42.221:8080/invocations"
  var image: ByteArray? = null
  var jsonResponse: JSONObject? = null

    private fun buildContentBodyFace(faces: List<Face>): JSONObject {
      val faceIds = faces.map {
        it.trackingId ?: -1
      }
      val faceLocations = faces.map {
        listOf( // originally in (left, top, right, bottom) format
          it.boundingBox.top,
          it.boundingBox.right,
          it.boundingBox.bottom,
          it.boundingBox.left
        )
      }

      return JSONObject()
        .put("face-ids", faceIds)
        .put("face-locations", faceLocations)
    }

    private fun buildContentBodyEar(faces: List<Face>): JSONObject {
      val faceIds = faces.map {
        it.trackingId ?: -1
      }

      val earLocations = faces.map { face ->
        val faceLandmark: FaceLandmark? = if (face.headEulerAngleY < 0) {
          face.getLandmark(FaceLandmark.RIGHT_EAR)
        } else {
          face.getLandmark(FaceLandmark.LEFT_EAR)
        }

        var earLeft: Int? = null
        val earTop: Int = (faceLandmark!!.position.y - (face.boundingBox.height() * FaceGraphic.EAR_HEIGHT_PROPORTION / 2.0f)).roundToInt()
        var earRight: Int? = null
        val earBottom: Int = (faceLandmark.position.y + (face.boundingBox.height() * FaceGraphic.EAR_HEIGHT_PROPORTION / 2.0f)).roundToInt()

        if (faceLandmark.landmarkType == FaceLandmark.RIGHT_EAR) {
          earLeft = (faceLandmark.position.x - (face.boundingBox.width() * FaceGraphic.EAR_WIDTH_PROPORTION * 0.25f)).roundToInt()
          earRight = (faceLandmark.position.x + (face.boundingBox.width() * FaceGraphic.EAR_WIDTH_PROPORTION * 0.75f)).roundToInt()
        } else if (faceLandmark.landmarkType == FaceLandmark.LEFT_EAR) {
          earLeft = (faceLandmark.position.x - (face.boundingBox.width() * FaceGraphic.EAR_WIDTH_PROPORTION * 0.75f)).roundToInt()
          earRight = (faceLandmark.position.x + (face.boundingBox.width() * FaceGraphic.EAR_WIDTH_PROPORTION * 0.25f)).roundToInt()
        }

        listOf(
          earTop,
          earRight,
          earBottom,
          earLeft
        )
      }

      return JSONObject()
        .put("face-ids", faceIds)
        .put("ear-locations", earLocations)
    }

    private fun buildContentBodyBarcode(barcodes: List<Barcode>): JSONObject {
      val barcodeValues = barcodes.map {
        it.rawValue
      }
      return JSONObject()
        .put("barcode-values", barcodeValues)
    }

    // -----------------Common functions-----------------------
    fun requestRecognition(items: List<List<Any>?>): JSONObject? {
      val contentBody = JSONObject()
      val url = URL_SAGEMAKER

      contentBody.put("image", Base64.encodeToString(image!!, Base64.DEFAULT))

      for (item in items) {
        if (item != null && item[0] is Face) {
          Log.i("RecognitionAPI","Face & Ear API!")
          contentBody.put("faces", buildContentBodyFace(item as List<Face>))
          contentBody.put("ears", buildContentBodyEar(item as List<Face>))
        } else if (item != null && item[0] is Barcode) {
          Log.i("RecognitionAPI","Barcode API!")
          contentBody.put("barcodes", buildContentBodyBarcode(item as List<Barcode>))
        }
      }

      /* content body example
      {
        "image": "\/9j...",
        "faces": {
          "face-ids": "[0]",
          "face-locations": "[[250, 444, 653, 41]]"
        },
        "ears": {
          "face-ids": "[0]",
          "ear-locations": "[[250, 444, 653, 41]]"
        },
        "barcodes": {
          "barcode-values": "https://www.example.com"
        }
      */

      if (contentBody.has("faces"))
        Log.i("RecognitionAPI", contentBody.getJSONObject("faces").toString())
      if (contentBody.has("ears"))
        Log.i("RecognitionAPI", contentBody.getJSONObject("ears").toString())
      if (contentBody.has("barcodes"))
        Log.i("RecognitionAPI", contentBody.getJSONObject("barcodes").toString())

      val requestBody = buildRequestBody(contentBody)
      val request = buildRequest(url, requestBody)
      jsonResponse = postRequest(request)
      return jsonResponse
    }

    private fun postRequest(request: Request): JSONObject? {
      client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
          e.printStackTrace()
        }

        override fun onResponse(call: Call, response: Response) {
          if (!response.isSuccessful) throw IOException("Unexpected code $response")
          jsonResponse = JSONObject(response.body()!!.string())
        }
      })

      return jsonResponse
    }

    private fun buildRequest(url: String, data: RequestBody): Request {
      return Request.Builder()
        .url(url)
        .header("Content-Type", "application/json")
        .post(data)
        .build()
    }

    private fun buildRequestBody(contentBody: JSONObject): RequestBody {
      return RequestBody.create(
        MediaType.parse("application/json"),
        contentBody.toString()
      )
    }

    // -----------------Code for storing image to send-----------------------
    fun storeImage(image: InputImage) {
      val frameMetadata = FrameMetadata.Builder()
        .setWidth(image.width)
        .setHeight(image.height)
        .setRotation(image.rotationDegrees)
        .build()
      val nv21Buffer =
        BitmapUtils.yuv420ThreePlanesToNV21(image.planes, image.width, image.height)
      val bitmapImage = BitmapUtils.getBitmap(nv21Buffer, frameMetadata)
      val jpgImage = BitmapUtils.bitmapToJPEG(bitmapImage!!)
      this.image = jpgImage
    }
  }
}