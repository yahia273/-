package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import com.example.R
import com.example.data.model.ActivityType
import com.google.firebase.Timestamp
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageAndFormatUtils {

  private val bitmapCache = LruCache<Int, Bitmap>(30)

  suspend fun compressUriToBase64(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
      runCatching {
          val inputStream = context.contentResolver.openInputStream(uri) ?: return@runCatching null
          val original = inputStream.use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
          val maxDim = 520
          val ratio =
            minOf(maxDim.toFloat() / original.width.toFloat(), maxDim.toFloat() / original.height.toFloat(), 1f)
          val targetW = (original.width * ratio).toInt().coerceAtLeast(1)
          val targetH = (original.height * ratio).toInt().coerceAtLeast(1)
          val scaled =
            if (targetW != original.width || targetH != original.height) {
              Bitmap.createScaledBitmap(original, targetW, targetH, true)
            } else {
              original
            }
          val out = ByteArrayOutputStream()
          scaled.compress(Bitmap.CompressFormat.JPEG, 68, out)
          val bytes = out.toByteArray()
          if (bytes.size > 300_000) {
            val outLow = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 48, outLow)
            Base64.encodeToString(outLow.toByteArray(), Base64.NO_WRAP)
          } else {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
          }
        }
        .getOrNull()
    }

  fun decodeBase64ToBitmap(base64: String): Bitmap? {
    if (base64.isBlank()) return null
    val key = base64.hashCode()
    bitmapCache.get(key)?.let {
      return it
    }
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bmp != null) {
          bitmapCache.put(key, bmp)
        }
        bmp
      }
      .getOrNull()
  }

  fun defaultDrawableForActivity(activityKey: String): Int {
    return when (ActivityType.fromKey(activityKey)) {
      ActivityType.RESTAURANT -> R.drawable.img_cat_restaurant
      ActivityType.SUPERMARKET -> R.drawable.img_cat_supermarket
      ActivityType.CAFE -> R.drawable.img_cat_cafe
    }
  }

  fun formatEgp(amount: Double): String {
    val isWhole = amount % 1.0 == 0.0
    val formatted =
      if (isWhole) {
        String.format(Locale.US, "%.0f", amount)
      } else {
        String.format(Locale.US, "%.2f", amount)
      }
    return "$formatted ج.م"
  }

  fun formatTimestamp(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    val sdf = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale("ar", "EG"))
    return sdf.format(date)
  }
}
