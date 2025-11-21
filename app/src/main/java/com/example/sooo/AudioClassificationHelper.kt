package com.example.sooo

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class AudioClassificationHelper(
    val context: Context,
    val listener: AudioClassificationListener,
    var modelPath: String = "tflite_learn_833095_8.tflite",
    var numThreads: Int = 2
) {

    private var interpreter: Interpreter? = null
    private var labels = listOf<String>()

    private lateinit var recorder: AudioRecord
    private var recordingThread: Thread? = null
    private var audioBuffer: ByteArrayOutputStream? = null

    @Volatile
    private var isRecording = false

    init {
        try {
            val model = FileUtil.loadMappedFile(context, modelPath)
            val options = Interpreter.Options().setNumThreads(numThreads)
            interpreter = Interpreter(model, options)
            labels = FileUtil.loadLabels(context, "labels.txt")
        } catch (e: Exception) {
            listener.onError("No se pudo inicializar el modelo: ${e.message}")
            Log.e("AudioClassificationHelper", "Error initializing TFLite interpreter", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (interpreter == null) {
            listener.onError("El clasificador no fue inicializado.")
            return
        }

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        
        recorder = AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            listener.onError("No se pudo inicializar AudioRecord.")
            return
        }

        audioBuffer = ByteArrayOutputStream()
        recorder.startRecording()
        
        isRecording = true
        recordingThread = thread(start = true) {
            val readBuffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = recorder.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    audioBuffer?.write(readBuffer, 0, read)
                }
            }
        }
    }

    suspend fun stopRecordingAndAnalyze() = withContext(Dispatchers.IO) {
        if (!isRecording) {
            return@withContext
        }

        isRecording = false
        recorder.stop()
        recordingThread?.join()
        recorder.release()

        val recordedBytes = audioBuffer?.toByteArray() ?: return@withContext
        audioBuffer = null

        val inputShape = interpreter!!.getInputTensor(0).shape()
        val inputSize = inputShape.last()

        val shortBuffer = ByteBuffer.wrap(recordedBytes).order(ByteOrder.nativeOrder()).asShortBuffer()
        val floatInput = FloatArray(shortBuffer.remaining()) {
            shortBuffer.get(it) / 32767.0f
        }

        val finalInput = if (floatInput.size >= inputSize) {
            floatInput.copyOfRange(floatInput.size - inputSize, floatInput.size)
        } else {
            FloatArray(inputSize).apply { floatInput.copyInto(this) }
        }

        val inputBuffer = ByteBuffer.allocateDirect(inputSize * 4).apply {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().put(finalInput)
        }

        val outputTensor = interpreter!!.getOutputTensor(0)
        val outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), outputTensor.dataType())
        
        val startTime = SystemClock.uptimeMillis()

        interpreter?.run(inputBuffer, outputBuffer.buffer)

        val inferenceTime = SystemClock.uptimeMillis() - startTime

        val result = TensorLabel(labels, outputBuffer).mapWithFloatValue
        
        withContext(Dispatchers.Main) {
            listener.onResult(result, inferenceTime)
        }
    }

    interface AudioClassificationListener {
        fun onError(error: String)
        fun onResult(results: Map<String, Float>, inferenceTime: Long)
    }
}