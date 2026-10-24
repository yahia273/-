package com.example.data.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
  CREATE("create"),
  UPDATE("update"),
  DELETE("delete"),
  LIST("list"),
  GET("get"),
  WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
  val auth = FirebaseAuth.getInstance()
  val currentUser = auth.currentUser

  val providerInfoList =
    currentUser?.providerData?.map { provider ->
      JSONObject().apply {
        put("providerId", provider.providerId)
        put("email", provider.email)
      }
    } ?: emptyList()

  val authInfoJson =
    JSONObject().apply {
      put("userId", currentUser?.uid)
      put("email", currentUser?.email)
      put("emailVerified", currentUser?.isEmailVerified)
      put("tenantId", currentUser?.tenantId)
      put("providerInfo", JSONArray(providerInfoList))
    }

  val errorInfoJson =
    JSONObject().apply {
      put("error", exception.message ?: exception.toString())
      put("operationType", operationType.value)
      put("path", path)
      put("authInfo", authInfoJson)
    }

  val jsonString = errorInfoJson.toString()
  Log.e("FirestoreError", "Firestore Error: $jsonString")
  return jsonString
}

object SecurityUtils {
  const val ADMIN_GOOGLE_EMAIL = "yahiakhamisasd@gmail.com"

  fun isAdminGoogleAccount(email: String?): Boolean {
    return email?.trim()?.lowercase(Locale.ROOT) == ADMIN_GOOGLE_EMAIL
  }

  fun currentDateKey(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return formatter.format(Date())
  }
}
