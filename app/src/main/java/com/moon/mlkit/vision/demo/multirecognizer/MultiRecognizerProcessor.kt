package com.moon.mlkit.vision.demo.multirecognizer

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase
import com.google.mlkit.vision.common.InputImage
import com.moon.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.face.*
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import org.json.JSONArray
import org.json.JSONObject
import java.util.*


class MultiRecognizerProcessor(
  context: Context,
  faceDetectorOptions: FaceDetectorOptions?,
  barcodeScannerOptions: BarcodeScannerOptions?,
  earDetectorOptions: CustomObjectDetectorOptions
) : ProcessorBase<Any>(context) {

  private val faceDetector: FaceDetector
  private val barcodeScanner: BarcodeScanner
  private val earDetector: ObjectDetector

  // image to store
  var image: ByteArray? = null

  init {
    faceDetector = FaceDetection.getClient(
      faceDetectorOptions ?: FaceDetectorOptions.Builder().build()
    )
    barcodeScanner = BarcodeScanning.getClient(
      barcodeScannerOptions ?: BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    )
    earDetector = ObjectDetection.getClient(earDetectorOptions)
  }

  override fun stop() {
    super.stop()
    faceDetector.close()
    barcodeScanner.close()
    earDetector.close()
  }

  override fun detectInImage(image: InputImage): Task<MutableList<Task<*>>> {
    RecognitionAPI.storeImage(image) // store image to send to recognition API
    return Tasks.whenAllComplete(
      faceDetector.process(image),
      barcodeScanner.process(image),
//      earDetector.process(image)
    )
  }

  private var jsonResponse: JSONObject? = null
  private var recognizedFaces: JSONArray = JSONArray()
  private var recognizedBarcodes: JSONArray = JSONArray()
  private var recognizedEars: JSONArray = JSONArray()
  override fun onSuccess(tasks: MutableList<Task<*>>, graphicOverlay: GraphicOverlay) {
    var faces: List<Face>? = null
    var barcodes: List<Barcode>? = null
    var ears: List<DetectedObject>? = null

    for (task in tasks) {
      if (task.result !is MutableList<*>) continue
      val resultList = task.result as List<*>

      if (resultList.isNotEmpty() && resultList[0] is Face) {
        // handle face result
        faces = resultList as List<Face>
      } else if (resultList.isNotEmpty() && resultList[0] is Barcode) {
        // handle barcode result
        barcodes = resultList as List<Barcode>
      } else if (resultList.isNotEmpty() && resultList[0] is DetectedObject) {
        // handle ear result
        ears = resultList as List<DetectedObject>
      }
    }

    // call API
    if (faces != null || barcodes != null)
      jsonResponse = RecognitionAPI.requestRecognition(listOf(faces, barcodes))

    // call encoder for face


    // match encoded face from database


    // return closest face



    /* display face, ear */
    // face
    if (jsonResponse?.has("faces") == true) {
      recognizedFaces = jsonResponse?.getJSONArray("faces") ?: JSONArray()
      Log.i("Faces detected", faces.toString())
      Log.i("Faces received", recognizedFaces.toString()) // eg. [{"distance":0.53...,"name":"jo..."}]
    }

    val faceMap = mutableMapOf<Int, Pair<String, Double>>() // face id to name, distance

    for (i in 0 until recognizedFaces.length()) {
      val recognizedFace = recognizedFaces.getJSONObject(i)

      val faceId = recognizedFace.getInt(("id"))
      val faceName = recognizedFace.getString("name")
      val faceDist = recognizedFace.getDouble("distance")
      faceMap[faceId] = Pair(faceName, faceDist)
    }

    // ear
    if (jsonResponse?.has("ears") == true) {
      recognizedEars = jsonResponse?.getJSONArray("ears") ?: JSONArray()
      Log.i("Ears received", recognizedEars.toString()) // eg. [{"distance":0.53...,"name":"jo..."}]
    }
    val earMap = mutableMapOf<Int, Pair<String, Double>>() // ear id to name, distance

    for (i in 0 until recognizedEars.length()) {
      val recognizedEar = recognizedEars.getJSONObject(i)

      val faceId = recognizedEar.getInt(("id"))
      val earName = recognizedEar.getString("name")
      val earDist = recognizedEar.getDouble("distance")
      earMap[faceId] = Pair(earName, earDist)
    }

    // together
    if (faces != null) {
      for (face in faces) {
        val id = face.trackingId
        val faceName = faceMap[id]?.first ?: "Unsent"
        val faceDist = faceMap[id]?.second ?: -1.0
        val earName = earMap[id]?.first ?: "Unsent"
        val earDist = earMap[id]?.second ?: -1.0
        graphicOverlay.add(FaceGraphic(graphicOverlay, face, faceName, faceDist, earName, earDist))
        logExtrasForTesting(face)
      }
    }

    /* display barcode */
    if (jsonResponse?.has("barcodes") == true) {
      recognizedBarcodes = jsonResponse?.getJSONArray("barcodes") ?: JSONArray()
      Log.i("Barcodes detected", barcodes.toString())
      Log.i("Barcodes received", recognizedBarcodes.toString())
    }

    val barcodeMap = mutableMapOf<String, String>() // barcode value to content
    for (i in 0 until recognizedBarcodes.length()) {
      val recognizedBarcode = recognizedBarcodes.getJSONObject(i)
      val barcodeValue = recognizedBarcode.getString("value")
      val barcodeContext = recognizedBarcode.getString("content")
      barcodeMap[barcodeValue] = barcodeContext
    }

    if (barcodes != null) {
      for (barcode in barcodes) {
        val barcodeValue = barcode.rawValue
        val barcodeContext = barcodeMap[barcodeValue] ?: "Unsent"
        graphicOverlay.add(BarcodeGraphic(graphicOverlay, barcode, barcodeContext))
        logExtrasForTesting(barcode)
      }
    }
  }

  @SuppressLint("LongLogTag")
  override fun onFailure(e: Exception) {
    Log.e(TAG, "Something failed $e")
  }

  companion object {
    private const val TAG = "MultiRecognizerProcessor"
    private fun logExtrasForTesting(face: Face?) {
      if (face != null) {
        Log.v(
          MANUAL_TESTING_LOG,
          "face bounding box: " + face.boundingBox.flattenToString()
        )
        Log.v(
          MANUAL_TESTING_LOG,
          "face Euler Angle X: " + face.headEulerAngleX
        )
        Log.v(
          MANUAL_TESTING_LOG,
          "face Euler Angle Y: " + face.headEulerAngleY
        )
        Log.v(
          MANUAL_TESTING_LOG,
          "face Euler Angle Z: " + face.headEulerAngleZ
        )
        // All landmarks
        val landMarkTypes = intArrayOf(
          FaceLandmark.MOUTH_BOTTOM,
          FaceLandmark.MOUTH_RIGHT,
          FaceLandmark.MOUTH_LEFT,
          FaceLandmark.RIGHT_EYE,
          FaceLandmark.LEFT_EYE,
          FaceLandmark.RIGHT_EAR,
          FaceLandmark.LEFT_EAR,
          FaceLandmark.RIGHT_CHEEK,
          FaceLandmark.LEFT_CHEEK,
          FaceLandmark.NOSE_BASE
        )
        val landMarkTypesStrings = arrayOf(
          "MOUTH_BOTTOM",
          "MOUTH_RIGHT",
          "MOUTH_LEFT",
          "RIGHT_EYE",
          "LEFT_EYE",
          "RIGHT_EAR",
          "LEFT_EAR",
          "RIGHT_CHEEK",
          "LEFT_CHEEK",
          "NOSE_BASE"
        )
        for (i in landMarkTypes.indices) {
          val landmark = face.getLandmark(landMarkTypes[i])
          if (landmark == null) {
            Log.v(
              MANUAL_TESTING_LOG,
              "No landmark of type: " + landMarkTypesStrings[i] + " has been detected"
            )
          } else {
            val landmarkPosition = landmark.position
            val landmarkPositionStr =
              String.format(Locale.US, "x: %f , y: %f", landmarkPosition.x, landmarkPosition.y)
            Log.v(
              MANUAL_TESTING_LOG,
              "Position for face landmark: " +
                      landMarkTypesStrings[i] +
                      " is :" +
                      landmarkPositionStr
            )
          }
        }
        Log.v(
          MANUAL_TESTING_LOG,
          "face left eye open probability: " + face.leftEyeOpenProbability
        )
        Log.v(
          MANUAL_TESTING_LOG,
          "face right eye open probability: " + face.rightEyeOpenProbability
        )
        Log.v(
          MANUAL_TESTING_LOG,
          "face smiling probability: " + face.smilingProbability
        )
        Log.v(
          MANUAL_TESTING_LOG,
          "face tracking id: " + face.trackingId
        )
      }
    }

    private fun logExtrasForTesting(barcode: Barcode?) {
      if (barcode != null) {
        Log.v(
          MANUAL_TESTING_LOG,
          String.format(
            "Detected barcode's bounding box: %s",
            barcode.boundingBox!!.flattenToString()
          )
        )
        Log.v(
          MANUAL_TESTING_LOG,
          String.format(
            "Expected corner point size is 4, get %d",
            barcode.cornerPoints!!.size
          )
        )
        for (point in barcode.cornerPoints!!) {
          Log.v(
            MANUAL_TESTING_LOG,
            String.format(
              "Corner point is located at: x = %d, y = %d",
              point.x,
              point.y
            )
          )
        }
        Log.v(
          MANUAL_TESTING_LOG,
          "barcode display value: " + barcode.displayValue
        )
        Log.v(
          MANUAL_TESTING_LOG,
          "barcode raw value: " + barcode.rawValue
        )
        val dl = barcode.driverLicense
        if (dl != null) {
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license city: " + dl.addressCity
          )
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license state: " + dl.addressState
          )
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license street: " + dl.addressStreet
          )
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license zip code: " + dl.addressZip
          )
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license birthday: " + dl.birthDate
          )
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license document type: " + dl.documentType
          )
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license expiry date: " + dl.expiryDate
          )
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license first name: " + dl.firstName
          )
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license middle name: " + dl.middleName
          )
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license last name: " + dl.lastName
          )
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license gender: " + dl.gender
          )
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license issue date: " + dl.issueDate
          )
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license issue country: " + dl.issuingCountry
          )
          Log.v(
            MANUAL_TESTING_LOG,
            "driver license number: " + dl.licenseNumber
          )
        }
      }
    }

    private fun logExtrasForTesting(ear: DetectedObject?) {
      // log something in the future?
    }
  }
}

