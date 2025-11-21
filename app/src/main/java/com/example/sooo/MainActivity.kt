package com.example.sooo

import android.Manifest
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.toColor
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), AudioClassificationHelper.AudioClassificationListener {

    private lateinit var audioHelper: AudioClassificationHelper
    private lateinit var resultTextView: TextView
    private lateinit var recordButton: Button

    private var isRecording = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecordingProcess()
        } else {
            resultTextView.text = "El permiso para grabar fue denegado."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultTextView = findViewById(R.id.result_text)
        recordButton = findViewById(R.id.record_button)

        audioHelper = AudioClassificationHelper(this, this)

        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecordingAndAnalyzeProcess()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startRecordingProcess() {
        isRecording = true
        audioHelper.startRecording()
        resultTextView.text = "Grabando...\n(Presiona de nuevo para detener y analizar)"
        recordButton.text = "Detener y Analizar"
    }

    private fun stopRecordingAndAnalyzeProcess() {
        lifecycleScope.launch {
            audioHelper.stopRecordingAndAnalyze()
        }
        isRecording = false
        resultTextView.text = "Analizando..."
        recordButton.text = "Grabar Sonido"
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) {
            stopRecordingAndAnalyzeProcess()
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            resultTextView.text = "ERROR FATAL:\n$error"
            recordButton.isEnabled = false
        }
    }

    override fun onResult(results: Map<String, Float>, inferenceTime: Long) {
        val topResult = results.entries.maxByOrNull { it.value }

        val resultStr = if (topResult != null) {
            val interestingLabels = setOf("Perro", "Gato", "Leon")
            val confidence = topResult.value
            val label = topResult.key

            if (label in interestingLabels && confidence > 0.75f) {
                "¡Resultado: $label! (${(confidence * 100).toInt()}%)"
            } else {
                "No se escuchó ningún sonido de animal."
            }
        } else {
            "No se obtuvieron resultados."
        }

        runOnUiThread {
            resultTextView.text = resultStr
        }
    }
}
