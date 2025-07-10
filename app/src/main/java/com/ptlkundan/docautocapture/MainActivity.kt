package com.ptlkundan.docautocapture

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.ptlkundan.docautocapture.Constants.LABELS_PATH
import com.ptlkundan.docautocapture.Constants.MODEL_PATH
import com.ptlkundan.docautocapture.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Detector.DetectorListener {

    private lateinit var binding: ActivityMainBinding
    private val isFrontCamera = false

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var detector: Detector? = null

    private lateinit var cameraExecutor: ExecutorService
    private var lastStableBitmap: Bitmap? = null
    private var hasCaptured = false
    private val resetCaptureDelay = 3000L
    private var lastDetectedCardBox: BoundingBox? = null
    private var stableCardFrameCount = 0
    private val requiredStableCardFrames = 3
    private var blurredFrameCount = 0
    private val maxAllowedBlurredFrames = 3
    private var captureDelayHandler: Handler? = null
    private var captureRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        cameraExecutor.execute {
            detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
        }

        // Initial UI setup (in onCreate or onViewCreated)
        binding.previewImage.setImageDrawable(null)
        binding.retakeButton.visibility = View.GONE

// Hide DotProgressBar (no need to set status or color while hidden)
        binding.stabilityProgress.stopAnimation()
        binding.stabilityProgress.visibility = View.GONE

// Retake Button Logic
        binding.retakeButton.setOnClickListener {
            // Reset detection state
            hasCaptured = false
            stableCardFrameCount = 0
            lastDetectedCardBox = null

            // Clear UI
            binding.previewImage.setImageDrawable(null)
            binding.overlay.clear()
            binding.retakeButton.visibility = View.GONE

            // Reset DotProgressBar
            binding.stabilityProgress.stopAnimation()
            binding.stabilityProgress.visibility = View.GONE

            // Optional: defer these until you actually want to show the progress bar
            // For example, when detection starts again:
            binding.stabilityProgress.updateStatus("Hold steady — detecting card")
            binding.stabilityProgress.setDotColor(Color.parseColor("#76FF03"))
        }



        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

        bindListeners()
    }

    private fun bindListeners() {
        binding.isGpu.setOnCheckedChangeListener { buttonView, isChecked ->
            cameraExecutor.submit {
                detector?.restart(isGpu = isChecked)
            }
            buttonView.setBackgroundColor(
                ContextCompat.getColor(
                    baseContext,
                    if (isChecked) R.color.orange else R.color.gray
                )
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetResolution(Size(1280, 720)) // or higher like 1920x1080
            .setTargetRotation(Surface.ROTATION_0)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720)) // or higher like 1920x1080
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            if (hasCaptured) {
                imageProxy.close()
                return@setAnalyzer
            }

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap()

            // Rotate bitmap based on camera orientation
            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            lastStableBitmap = rotatedBitmap.copy(rotatedBitmap.config, false)
            detector?.detect(rotatedBitmap)

            imageProxy.close()
        }


        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
        }
    }






    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            binding.inferenceTime.text = "$inferenceTime ms"
            binding.overlay.showBoundingBoxes = false
            binding.overlay.setResults(boundingBoxes, 1280, 720)
            binding.overlay.invalidate()
        }

        val bitmap = lastStableBitmap ?: return
        if (hasCaptured) return

        val cardBox = boundingBoxes
            .filter { it.clsName.equals("card", ignoreCase = true) }
            .maxByOrNull { it.cnf }
            ?.takeIf { box ->
                val width = box.x2 - box.x1
                val height = box.y2 - box.y1
                val aspectRatio = width / height
                val isValidAR = aspectRatio in 1.3f..1.7f
                val isConfident = box.cnf > 0.80f
                Log.d("AutoCapture", "AR=$aspectRatio, Conf=${box.cnf}")
                isValidAR && isConfident
            } ?: run {
            stableCardFrameCount = 0
            lastDetectedCardBox = null
            runOnUiThread {
                binding.stabilityProgress.updateStatus("Hold steady — detecting card")
                binding.stabilityProgress.setDotColor(Color.parseColor("#76FF03")) // Green
                binding.stabilityProgress.stopAnimation()
                binding.stabilityProgress.visibility = View.GONE
            }
            return
        }

        val isStable = lastDetectedCardBox?.let { prev ->
            val dx = kotlin.math.abs(prev.cx - cardBox.cx)
            val dy = kotlin.math.abs(prev.cy - cardBox.cy)
            dx < 0.02f && dy < 0.02f
        } ?: false

        if (isStable) {
            stableCardFrameCount++
        } else {
            stableCardFrameCount = 1
            lastDetectedCardBox = cardBox
            runOnUiThread {
                binding.stabilityProgress.visibility = View.VISIBLE
                binding.stabilityProgress.updateStatus("Hold steady — capturing...")
                binding.stabilityProgress.setDotColor(Color.YELLOW)
                binding.stabilityProgress.startAnimation()
            }
        }

        if (stableCardFrameCount < requiredStableCardFrames) return

        val quad = cardBox.toQuadrilateral(bitmap.width, bitmap.height)
        val boundingRect = quad.getBoundingRect()

        val safeRect = Rect(
            boundingRect.left.coerceAtLeast(0),
            boundingRect.top.coerceAtLeast(0),
            boundingRect.right.coerceAtMost(bitmap.width),
            boundingRect.bottom.coerceAtMost(bitmap.height)
        )

        if (safeRect.width() <= 0 || safeRect.height() <= 0) return

        val cropped = Bitmap.createBitmap(
            bitmap,
            safeRect.left,
            safeRect.top,
            safeRect.width(),
            safeRect.height()
        )

        if (isBlurred(cropped)) {
            Log.d("AutoCapture", "Skipped: Blurred image")
            stableCardFrameCount = 0
            runOnUiThread {
                binding.stabilityProgress.updateStatus("Image too blurry — hold steady")
                binding.stabilityProgress.setDotColor(Color.RED)
            }
            return
        }
        // ✅ Final capture begins
        hasCaptured = true

        val rotated = if (cropped.height > cropped.width) {
            val matrix = Matrix().apply { postRotate(90f) }
            Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
        } else {
            cropped
        }

        val finalBitmap = sharpenBitmap(rotated)

        runOnUiThread {
            binding.previewImage.setImageBitmap(finalBitmap)
            binding.retakeButton.visibility = View.VISIBLE
            binding.stabilityProgress.updateStatus("Card captured")
            binding.stabilityProgress.setDotColor(Color.CYAN)
            binding.stabilityProgress.stopAnimation()
            binding.stabilityProgress.visibility = View.GONE

            saveBitmapToGallery(finalBitmap)
        }
    }

    override fun onEmptyDetect() {
        runOnUiThread {
            binding.overlay.clear()
        }
    }

    private fun isBlurred(bitmap: Bitmap): Boolean {
        val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val grayscale = IntArray(64 * 64)
        scaled.getPixels(grayscale, 0, 64, 0, 0, 64, 64)

        var variance = 0.0
        var mean = 0.0
        for (pixel in grayscale) {
            val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3.0
            mean += gray
            variance += gray * gray
        }
        mean /= grayscale.size
        variance = variance / grayscale.size - mean * mean

        return variance < 100 // Tweak threshold as needed
    }

    private fun sharpenBitmap(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val kernel = arrayOf(
            floatArrayOf(0f, -1f, 0f),
            floatArrayOf(-1f, 5f, -1f),
            floatArrayOf(0f, -1f, 0f)
        )

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var r = 0f
                var g = 0f
                var b = 0f

                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = src.getPixel(x + kx, y + ky)
                        val weight = kernel[ky + 1][kx + 1]
                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                    }
                }

                val newR = r.coerceIn(0f, 255f).toInt()
                val newG = g.coerceIn(0f, 255f).toInt()
                val newB = b.coerceIn(0f, 255f).toInt()

                result.setPixel(x, y, Color.rgb(newR, newG, newB))
            }
        }

        return result
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename =
            "book_capture_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/BookCaptures")
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it).use { stream ->
                if (stream == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    Log.e(TAG, "Failed to save bitmap")
                    Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
        cameraExecutor.shutdown()
    }

    fun BoundingBox.toQuadrilateral(bitmapWidth: Int, bitmapHeight: Int): Quadrilateral {
        val x1 = (this.x1 * bitmapWidth).toInt()
        val y1 = (this.y1 * bitmapHeight).toInt()
        val x2 = (this.x2 * bitmapWidth).toInt()
        val y2 = (this.y2 * bitmapHeight).toInt()

        val topLeft = Point(x1, y1)
        val topRight = Point(x2, y1)
        val bottomRight = Point(x2, y2)
        val bottomLeft = Point(x1, y2)

        return Quadrilateral(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "Camera"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
