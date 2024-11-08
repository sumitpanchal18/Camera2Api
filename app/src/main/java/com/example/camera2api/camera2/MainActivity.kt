package com.example.camera2api.camera2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.camera2api.R
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    private val cameraManager by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var imageReader: ImageReader? = null

    private val surfaceView: SurfaceView by lazy { findViewById(R.id.surfaceView) }
    private lateinit var captureButton: Button

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceHolder = surfaceView.holder
        surfaceHolder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    openCamera()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                // After surface is created or resized, we need to update the ImageReader dimensions
                imageReader?.close()  // Close previous ImageReader (if any)
                imageReader = ImageReader.newInstance(width, height, android.graphics.ImageFormat.JPEG, 1)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                closeCamera()
            }
        })

        // Initialize the button for capturing the picture
        captureButton = findViewById(R.id.captureButton)
        captureButton.setOnClickListener {
            captureImage()
        }
    }

    private fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

            val handler = Handler(Looper.getMainLooper())
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e("Camera2", "Error opening camera: $error")
                }
            }, handler)
        } catch (e: Exception) {
            Log.e("Camera2", "Exception opening camera", e)
        }
    }

    private fun startPreview(camera: CameraDevice) {
        try {
            val surface = surfaceHolder?.surface

            // Make sure we create a capture request for the preview
            val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface!!)
            }

            // Start preview with a capture session
            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    try {
                        session.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    } catch (e: CameraAccessException) {
                        Log.e("Camera2", "Failed to start preview", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("Camera2", "Failed to configure the camera capture session")
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.e("Camera2", "Error starting preview", e)
        }
    }

    private fun captureImage() {
        try {
            if (cameraDevice == null || imageReader == null) return

            // Create a capture request for still image capture
            val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                val surface = imageReader?.surface
                addTarget(surface!!)
            }

            val captureListener = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    Log.d("Camera2", "Image captured!")
                    saveImageToStorage()
                }
            }

            cameraCaptureSession?.stopRepeating()
            cameraCaptureSession?.capture(captureRequestBuilder.build(), captureListener, null)

        } catch (e: CameraAccessException) {
            Log.e("Camera2", "Error capturing image", e)
        }
    }

    private fun saveImageToStorage() {
        try {
            val image = imageReader?.acquireLatestImage()
            if (image == null) return

            // Get the image buffer and convert it to a Bitmap
            val buffer: ByteBuffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            val file = File(filesDir, "captured_image.jpg")
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                Toast.makeText(this, "Image saved to: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
            }

            image.close() // Close the image when done
        } catch (e: Exception) {
            Log.e("Camera2", "Error saving image", e)
        }
    }

    private fun closeCamera() {
        try {
            cameraCaptureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
        } catch (e: Exception) {
            Log.e("Camera2", "Error closing camera", e)
        }
    }

    override fun onPause() {
        super.onPause()
        closeCamera()
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
