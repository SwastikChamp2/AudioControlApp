package com.example.audiocontrolapp

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import android.os.Build

//Test 1

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var detailedStatusText: TextView
    private val TAG = "AudioControlApp"
    private val REQUEST_CODE_SHIZUKU = 1000
    private val REQUEST_CODE_OVERLAY = 1001
    private var isShizukuBinderReceived = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        detailedStatusText = findViewById(R.id.detailedStatusText)
        val startButton: Button = findViewById(R.id.startServiceButton)
        val stopButton: Button = findViewById(R.id.stopServiceButton)
        val grantOverlayButton: Button = findViewById(R.id.grantOverlayButton)
        val grantShizukuButton: Button = findViewById(R.id.grantShizukuButton)


        updateStatusText("Initializing app...")

        // Shizuku binder listeners
        Shizuku.addBinderReceivedListener {
            runOnUiThread {
                isShizukuBinderReceived = true
                updateStatusText("Shizuku connected")
                checkAndRequestShizukuPermission()
            }
        }

        Shizuku.addBinderDeadListener {
            runOnUiThread {
                isShizukuBinderReceived = false
                updateStatusText("Shizuku disconnected")
                startShizukuBinderCheck()
            }
        }

        startShizukuBinderCheck()

        startButton.setOnClickListener {
            if (!checkPermissions()) {
                showToast("Please grant all permissions first")
                return@setOnClickListener
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(Intent(this, AudioControlService::class.java))
                } else {
                    startService(Intent(this, AudioControlService::class.java))
                }
                updateStatusText("Service started")
                showToast("Multi-audio enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Start service failed", e)
                showToast("Failed to start service: ${e.message}")
            }
        }

        stopButton.setOnClickListener {
            try {
                stopService(Intent(this, AudioControlService::class.java))
                updateStatusText("Service stopped")
                showToast("Multi-audio disabled")
            } catch (e: Exception) {
                Log.e(TAG, "Stop service failed", e)
                showToast("Failed to stop service")
            }
        }

        grantOverlayButton.setOnClickListener {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQUEST_CODE_OVERLAY)
            } catch (e: Exception) {
                Log.e(TAG, "Overlay permission failed", e)
                showToast("Failed to open settings")
            }
        }

        grantShizukuButton.setOnClickListener {
            if (!Shizuku.pingBinder()) {
                showToast("Shizuku service not running")
                return@setOnClickListener
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                showToast("Please grant permission in Shizuku app")
            } else {
                Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_SHIZUKU -> {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    showToast("Shizuku permission granted")
                    updateStatusText("Shizuku ready")
                } else {
                    showToast("Shizuku permission denied")
                }
            }
            REQUEST_CODE_OVERLAY -> {
                updateStatusText("Overlay permission updated")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Shizuku.removeBinderReceivedListener { }
        Shizuku.removeBinderDeadListener { }
        super.onDestroy()
    }

    private fun startShizukuBinderCheck() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (Shizuku.pingBinder()) {
                    isShizukuBinderReceived = true
                    checkAndRequestShizukuPermission()
                } else {
                    updateStatusText("Waiting for Shizuku...")
                    handler.postDelayed(this, 1000)
                }
            }
        }, 1000)
    }

    private fun checkAndRequestShizukuPermission() {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
        } else {
            updateStatusText("Shizuku ready")
        }
    }

    private fun checkPermissions(): Boolean {
        val overlayGranted = Settings.canDrawOverlays(this)
        val shizukuActive = Shizuku.pingBinder()
        val shizukuPermission = shizukuActive &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

        detailedStatusText.text = """
            Permissions:
            • Overlay: ${if (overlayGranted) "✅" else "❌"}
            • Shizuku: ${if (shizukuActive) "✅" else "❌"} 
            • Shizuku Perm: ${if (shizukuPermission) "✅" else "❌"}
        """.trimIndent()

        return overlayGranted && shizukuActive && shizukuPermission
    }

    private fun updateStatusText(message: String) {
        statusText.text = "Status: $message"
        Log.d(TAG, message)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
