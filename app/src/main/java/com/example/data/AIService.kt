package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

import okhttp3.CertificatePinner

object AIService {
    private val certificatePinner = CertificatePinner.Builder()
        .add("generativelanguage.googleapis.com", "sha256/wexkXgR+pYt3E6gL09kO3dZ2Wj32w1hW2I5rV5P0aU8=")
        .add("generativelanguage.googleapis.com", "sha256/8Rw90Ej3Ttt8RRkrg+WYDS9n7yX3zPeO2lzSR7pXwG8=")
        .add("generativelanguage.googleapis.com", "sha256/Ko8tivDrEjiY90yGasP6ZpBU4jwXvHqVvQI0GS3GNnM=")
        .build()

    private val client = OkHttpClient.Builder()
        .certificatePinner(certificatePinner)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val MODEL_NAME = "gemini-3.5-flash"

    suspend fun getDiagnostics(systemLogs: List<String>, operatorPrompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "ERROR: Clave API de Gemini no configurada. Configure la clave en la sección de Secretos."
        }

        val prompt = """
            Eres un sistema de Inteligencia Artificial Avanzado integrado en el núcleo táctico Sentinel de Seguridad Expres.
            Analiza los siguientes registros del sistema actual y responde al operador con un reporte profesional, detallado y accionable.
            
            REGISTROS RECIENTES:
            ${systemLogs.joinToString("\n")}
            
            SOLICITUD DEL OPERADOR:
            $operatorPrompt
            
            Por favor, estructura tu respuesta de manera profesional e incluye:
            1. DIAGNÓSTICO DE INTEGRIDAD: Evaluación de riesgos y estabilidad del sistema.
            2. ALERTA DE FUTUROS ERRORES: Pronóstico detallado de posibles fallas basadas en patrones de sensores/logs (ej. base de datos, GPS, transmisiones, cámaras).
            3. RECOMENDACIÓN TÁCTICA: Qué acciones inmediatas debe tomar el operador.
        """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val escapedPrompt = prompt.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")

        val jsonRequest = """
            {
              "contents": [
                {
                  "parts": [
                    {
                      "text": "$escapedPrompt"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val requestBody = jsonRequest.toRequestBody(mediaType)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e("AIService", "API Error: ${response.code} - $errBody")
                    return@withContext "Error de red/API (Código ${response.code}). Asegúrese de tener conexión a Internet y una API Key válida."
                }
                val responseBody = response.body?.string() ?: return@withContext "Respuesta vacía del servidor."
                val jsonObject = JSONObject(responseBody)
                val text = jsonObject.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                text
            }
        } catch (e: Exception) {
            Log.e("AIService", "Exception: ", e)
            "Error al consultar el asistente de IA: ${e.localizedMessage}"
        }
    }
}
