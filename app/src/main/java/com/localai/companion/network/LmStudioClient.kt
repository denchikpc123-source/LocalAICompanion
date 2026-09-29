package com.localai.companion.network

import android.graphics.Bitmap
import android.util.Base64
import com.localai.companion.data.AppPreferences
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class LmStudioClient(private val prefs: AppPreferences) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getModels(): Result<List<String>> =
        suspendCancellableCoroutine { cont ->

            val request = Request.Builder()
                .url("${prefs.getBaseUrl()}/models")
                .build()

            val call = client.newCall(request)

            cont.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {

                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resume(Result.failure(e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        if (cont.isActive) {
                            cont.resume(
                                Result.failure(
                                    IOException(
                                        "HTTP Error: ${response.code} ${response.message}"
                                    )
                                )
                            )
                        }
                        return
                    }

                    try {
                        val responseBody = response.body?.string() ?: ""
                        val json = JSONObject(responseBody)
                        val data = json.optJSONArray("data") ?: JSONArray()

                        val models = mutableListOf<String>()

                        for (i in 0 until data.length()) {
                            val modelObj = data.optJSONObject(i)
                            val id = modelObj?.optString("id")

                            if (!id.isNullOrBlank()) {
                                models.add(id)
                            }
                        }

                        if (models.isEmpty()) {
                            if (cont.isActive) {
                                cont.resume(
                                    Result.failure(
                                        Exception("No models found on server.")
                                    )
                                )
                            }
                        } else {
                            if (cont.isActive) {
                                cont.resume(Result.success(models))
                            }
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) {
                            cont.resume(Result.failure(e))
                        }
                    }
                }
            })
        }

    suspend fun sendChat(
        text: String,
        history: JSONArray,
        cameraFrame: Bitmap?,
        screenFrame: Bitmap?
    ): Result<String> =
        suspendCancellableCoroutine { cont ->

            val model = prefs.selectedModel

            if (model.isBlank()) {
                if (cont.isActive) {
                    cont.resume(
                        Result.failure(
                            Exception("MODEL NOT SELECTED.")
                        )
                    )
                }
                return@suspendCancellableCoroutine
            }

            val payload = JSONObject()
            payload.put("model", model)
            payload.put("temperature", 0.7)

            val messages = JSONArray()

            for (i in 0 until history.length()) {
                messages.put(history.getJSONObject(i))
            }

            val currentMessage = JSONObject()
            currentMessage.put("role", "user")

            val contentArray = JSONArray()

            contentArray.put(
                JSONObject().apply {
                    put("type", "text")
                    put("text", text)
                }
            )

            cameraFrame?.let {
                contentArray.put(buildImageJson(it))
            }

            screenFrame?.let {
                contentArray.put(buildImageJson(it))
            }

            currentMessage.put("content", contentArray)
            messages.put(currentMessage)
            payload.put("messages", messages)

            val body = payload
                .toString()
                .toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("${prefs.getBaseUrl()}/chat/completions")
                .post(body)
                .build()

            val call = client.newCall(request)

            cont.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {

                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resume(Result.failure(e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        if (cont.isActive) {
                            cont.resume(
                                Result.failure(
                                    IOException(
                                        "HTTP Error: ${response.code} ${response.message}"
                                    )
                                )
                            )
                        }
                        return
                    }

                    try {
                        val responseBody = response.body?.string() ?: ""
                        val respJson = JSONObject(responseBody)
                        val choices = respJson.optJSONArray("choices")

                        if (choices != null && choices.length() > 0) {
                            val content = choices
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .optString("content", "")

                            if (cont.isActive) {
                                cont.resume(Result.success(content))
                            }
                        } else {
                            if (cont.isActive) {
                                cont.resume(
                                    Result.failure(
                                        Exception(
                                            "Invalid API Response: missing choices."
                                        )
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) {
                            cont.resume(Result.failure(e))
                        }
                    }
                }
            })
        }

    private fun buildImageJson(bitmap: Bitmap): JSONObject {
        val stream = ByteArrayOutputStream()

        val scaled = scaleBitmap(bitmap, 768)

        try {
            scaled.compress(
                Bitmap.CompressFormat.JPEG,
                70,
                stream
            )
        } finally {
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }

        val base64 = Base64.encodeToString(
            stream.toByteArray(),
            Base64.NO_WRAP
        )

        return JSONObject().apply {
            put("type", "image_url")
            put(
                "image_url",
                JSONObject().put(
                    "url",
                    "data:image/jpeg;base64,$base64"
                )
            )
        }
    }

    private fun scaleBitmap(
        bmp: Bitmap,
        maxSize: Int
    ): Bitmap {
        val ratio = minOf(
            maxSize.toFloat() / bmp.width,
            maxSize.toFloat() / bmp.height
        )

        if (ratio >= 1.0f) {
            return bmp
        }

        return Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * ratio).toInt(),
            (bmp.height * ratio).toInt(),
            true
        )
    }
}
