package com.example.sooo

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

// Helper function to format score to percentage
fun Float.toPercentString(): String {
    return "${(this * 100).toInt()}%"
}

class MainActivity : ComponentActivity(), AudioClassificationHelper.AudioClassificationListener {

    private lateinit var audioHelper: AudioClassificationHelper
    private var classificationResult by mutableStateOf("Presiona para grabar y analizar un audio")
    private var isRecording by mutableStateOf(false)
    private var hasInitializationFailed by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Inicia la grabación después de obtener el permiso
            isRecording = true
            audioHelper.startRecording()
            classificationResult = "Grabando...\n(Presiona de nuevo para detener y analizar)"
        } else {
            classificationResult = "El permiso para grabar fue denegado."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioHelper = AudioClassificationHelper(this, this)

        setContent {
            MainScreen(
                result = classificationResult,
                isRecording = isRecording,
                isButtonEnabled = !hasInitializationFailed,
                onButtonClick = {
                    if (isRecording) {
                        // Si está grabando, detiene y analiza en una corrutina
                        lifecycleScope.launch {
                            audioHelper.stopRecordingAndAnalyze()
                        }
                        isRecording = false
                        classificationResult = "Analizando..."
                    } else {
                        // Si no está grabando, pide permiso y empieza
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) {
            lifecycleScope.launch {
                audioHelper.stopRecordingAndAnalyze()
            }
            isRecording = false
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            classificationResult = "ERROR FATAL:\n$error"
            hasInitializationFailed = true
        }
    }

    override fun onResult(results: Map<String, Float>, inferenceTime: Long) {
        val topResult = results.entries.maxByOrNull { it.value }

        val resultStr = if (topResult != null) {
            val interestingLabels = setOf("Perro", "Gato", "Leon")
            // Aumentamos el umbral de confianza al 75% para evitar falsos positivos
            if (topResult.key in interestingLabels && topResult.value > 0.75f) {
                "¡Resultado: ${topResult.key}! (${topResult.value.toPercentString()})"
            } else {
                "No se escuchó ningún sonido de animal."
            }
        } else {
            "No se obtuvieron resultados."
        }

        runOnUiThread {
            classificationResult = resultStr
        }
    }
}

@Composable
fun MainScreen(result: String, isRecording: Boolean, isButtonEnabled: Boolean, onButtonClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = result, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Button(
            onClick = onButtonClick,
            enabled = isButtonEnabled,
            modifier = Modifier.padding(top = 24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color.Gray else Color.Red
            )
        ) {
            Text(text = if (isRecording) "Detener y Analizar" else "Grabar Sonido")
        }
    }
}
