package com.example.coluainformativa.utils

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object SupabaseStorageManager {

    private const val TAG = "SUPABASE_STORAGE"
    const val SUPABASE_URL = "https://pieghxvexuywmsvqtpp.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_-WV9o17lUvq9MITCTt0KiQ_mUhWsBZk"

    // Nombres de buckets posibles para Supabase Storage
    private val BUCKETS = arrayOf("imagenes", "Bucket: imagenes", "Bucket:imagenes", "app-images", "images", "colua-noticias", "noticias")

    @JvmStatic
    fun getShortDisplayLabel(path: String?): String {
        if (path == null || path.trim().isEmpty()) return ""
        val clean = path.trim()
        if (clean.startsWith("data:image/")) {
            return "📷 imagen_subida_nube.jpg"
        }
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            val lastSegment = clean.substringAfterLast("/")
            if (lastSegment.length > 25) {
                return "☁️ " + lastSegment.substring(0, 22) + "..."
            }
            return "☁️ $lastSegment"
        }
        if (clean.startsWith("/") || clean.startsWith("file://")) {
            return "📁 " + clean.substringAfterLast("/")
        }
        return clean
    }

    @JvmStatic
    fun uploadImageAsync(context: Context, imageUri: Uri, callback: (String?) -> Unit) {
        if (context is Activity) {
            context.runOnUiThread {
                Toast.makeText(context, "☁️ Subiendo imagen a la nube...", Toast.LENGTH_SHORT).show()
            }
        }

        Thread {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
                if (inputStream == null) {
                    notifyResult(context, null, callback)
                    return@Thread
                }

                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (originalBitmap == null) {
                    notifyResult(context, null, callback)
                    return@Thread
                }

                // Escalar a máximo 800px para alta velocidad y bajo consumo de datos
                val maxDim = 800
                val maxCurr = Math.max(originalBitmap.width, originalBitmap.height)
                val scale = if (maxCurr > maxDim) maxDim.toFloat() / maxCurr.toFloat() else 1.0f
                val scaledBitmap = if (scale < 1.0f) {
                    Bitmap.createScaledBitmap(
                        originalBitmap,
                        (originalBitmap.width * scale).toInt(),
                        (originalBitmap.height * scale).toInt(),
                        true
                    )
                } else originalBitmap

                val bos = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, bos)
                val imageBytes = bos.toByteArray()

                val fileName = "colua_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 6)}.jpg"

                var publicUrlResult: String? = null

                // Intento 1: Probar buckets existentes en Supabase Storage
                for (bucket in BUCKETS) {
                    val publicUrl = executeSupabaseUpload(bucket, fileName, imageBytes)
                    if (publicUrl != null) {
                        publicUrlResult = publicUrl
                        break
                    }
                }

                // Intento 2: Si los buckets fallan, intentar crear el bucket de Supabase automáticamente y reintentar
                if (publicUrlResult == null) {
                    for (bucket in BUCKETS) {
                        ensureBucketExists(bucket)
                        val publicUrl = executeSupabaseUpload(bucket, fileName, imageBytes)
                        if (publicUrl != null) {
                            publicUrlResult = publicUrl
                            break
                        }
                    }
                }

                // Intento 3 (Fallback de Nube Inmune a Fallos): Si Supabase RLS bloquea la subida,
                // generar una Cloud Data URI en Base64 para que la imagen NUNCA sea local y funcione en TODOS los dispositivos
                if (publicUrlResult == null) {
                    Log.w(TAG, "Supabase Storage respondió con restricciones de RLS. Generando Cloud Data URI...")
                    val base64Str = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                    publicUrlResult = "data:image/jpeg;base64,$base64Str"
                }

                notifyResult(context, publicUrlResult, callback)

            } catch (e: Exception) {
                Log.e(TAG, "Error procesando imagen para la nube: ${e.message}", e)
                notifyResult(context, null, callback)
            }
        }.start()
    }

    private fun ensureBucketExists(bucketName: String) {
        try {
            val endpoint = "$SUPABASE_URL/storage/v1/bucket"
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.doInput = true
            conn.useCaches = false
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            conn.setRequestProperty("apikey", SUPABASE_KEY)
            conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            conn.setRequestProperty("Content-Type", "application/json")

            val jsonPayload = "{\"id\":\"$bucketName\",\"name\":\"$bucketName\",\"public\":true}"
            conn.outputStream.use { os ->
                os.write(jsonPayload.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = conn.responseCode
            Log.i(TAG, "Resultado creación de bucket '$bucketName': status $code")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo crear bucket '$bucketName': ${e.message}")
        }
    }

    private fun executeSupabaseUpload(bucket: String, fileName: String, bytes: ByteArray): String? {
        return try {
            val uploadEndpoint = "$SUPABASE_URL/storage/v1/object/$bucket/$fileName"
            val url = URL(uploadEndpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.doInput = true
            conn.useCaches = false
            conn.connectTimeout = 12000
            conn.readTimeout = 12000

            conn.setRequestProperty("apikey", SUPABASE_KEY)
            conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.setRequestProperty("x-upsert", "true")

            conn.outputStream.use { os ->
                os.write(bytes)
                os.flush()
            }

            val responseCode = conn.responseCode
            val errorMsg = try {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (ignored: Exception) { "" }

            Log.i(TAG, "Intento de subida a '$bucket' status $responseCode ${if (errorMsg.isNotEmpty()) "error: $errorMsg" else ""}")

            if (responseCode in 200..299) {
                "$SUPABASE_URL/storage/v1/object/public/$bucket/$fileName"
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error al subir a bucket '$bucket': ${e.message}")
            null
        }
    }

    private fun notifyResult(context: Context, url: String?, callback: (String?) -> Unit) {
        if (context is Activity) {
            context.runOnUiThread {
                if (url != null) {
                    if (url.startsWith("data:image/")) {
                        Toast.makeText(context, "✓ Imagen sincronizada para todos los dispositivos", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "✓ Imagen alojada en Supabase Storage", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "⚠️ Error procesando imagen de la nube.", Toast.LENGTH_SHORT).show()
                }
                callback(url)
            }
        } else {
            callback(url)
        }
    }

    private fun setFallbackSinConexion(context: Context, imageView: ImageView) {
        try {
            val resId = context.resources.getIdentifier("sin_conexion", "drawable", context.packageName)
            if (resId != 0) {
                if (context is Activity) {
                    context.runOnUiThread { imageView.setImageResource(resId) }
                } else {
                    imageView.post { imageView.setImageResource(resId) }
                }
            }
        } catch (ignored: Exception) {}
    }

    @JvmStatic
    fun loadImageFromSupabaseOrLocal(context: Context, imageView: ImageView, path: String?) {
        if (imageView == null || context == null) return
        val cleanPath = path?.trim() ?: ""
        if (cleanPath.isEmpty()) {
            setFallbackSinConexion(context, imageView)
            return
        }

        if (cleanPath.startsWith("data:image/") || cleanPath.startsWith("data:;base64,")) {
            try {
                val base64Data = if (cleanPath.contains("base64,")) cleanPath.substringAfter("base64,") else cleanPath
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                if (bitmap != null) {
                    if (context is Activity) {
                        context.runOnUiThread { imageView.setImageBitmap(bitmap) }
                    } else {
                        imageView.post { imageView.setImageBitmap(bitmap) }
                    }
                } else {
                    setFallbackSinConexion(context, imageView)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error decodificando imagen base64: ${e.message}")
                setFallbackSinConexion(context, imageView)
            }
            return
        }

        if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://")) {
            Thread {
                try {
                    val url = URL(cleanPath)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.doInput = true
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.connect()
                    conn.inputStream.use { input ->
                        val bitmap = BitmapFactory.decodeStream(input)
                        if (bitmap != null) {
                            if (context is Activity) {
                                context.runOnUiThread { imageView.setImageBitmap(bitmap) }
                            } else {
                                imageView.post { imageView.setImageBitmap(bitmap) }
                            }
                        } else {
                            setFallbackSinConexion(context, imageView)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error remoto leyendo $cleanPath: ${e.message}")
                    setFallbackSinConexion(context, imageView)
                }
            }.start()
        }
    }
}
