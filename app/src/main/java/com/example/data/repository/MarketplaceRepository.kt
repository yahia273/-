package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.ActivityType
import com.example.data.model.AdminSession
import com.example.data.model.AppNotification
import com.example.data.model.CommissionSettlement
import com.example.data.model.Offer
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.PlatformConfig
import com.example.data.model.UserProfile
import com.example.data.model.VendorStore
import com.example.data.util.OperationType
import com.example.data.util.SecurityUtils
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MarketplaceRepository(private val db: FirebaseFirestore) {

  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("يجب تسجيل الدخول أولاً للوصول إلى قاعدة البيانات")
  }

  private fun roundCurrency(value: Double): Double = (value * 100.0).roundToLong() / 100.0

  private fun <T> Flow<T>.guardSignOutStreamError(
    operationType: OperationType,
    path: String,
  ): Flow<T> = flow {
    val startedAuthenticated = auth.currentUser != null
    emitAll(
      this@guardSignOutStreamError.catch { error ->
        if (startedAuthenticated && auth.currentUser == null) {
          Log.d("MarketplaceRepo", "Snapshot stream closed cleanly during sign-out: $path")
        } else {
          if (error is Exception) handleFirestoreError(error, operationType, path)
          throw error
        }
      }
    )
  }

  // --- 1. User Profile & Role Onboarding ---

  fun observeUserProfile(userId: String): Flow<UserProfile?> =
    db
      .collection("users")
      .document(userId)
      .snapshots()
      .map { snap ->
        if (snap.exists()) {
          snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      }
      .guardSignOutStreamError(OperationType.GET, "users/$userId")

  suspend fun saveCustomerProfile(name: String, phone: String, email: String): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      val docRef = db.collection("users").document(uid)
      try {
        val existing = docRef.get().await()
        if (existing.exists()) {
          docRef
            .update(
              mapOf(
                "name" to name.trim(),
                "phone" to phone.trim(),
                "role" to "customer",
                "isApproved" to true,
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        } else {
          docRef
            .set(
              mapOf(
                "userId" to uid,
                "name" to name.trim(),
                "phone" to phone.trim(),
                "email" to email.trim().ifEmpty { "customer@village.local" },
                "role" to "customer",
                "isApproved" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
            .await()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, docRef.path)
        throw e
      }
    }

  suspend fun saveVendorProfile(
    ownerName: String,
    storeName: String,
    activityType: ActivityType,
    phone: String,
    email: String,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val userRef = db.collection("users").document(uid)
    val vendorRef = db.collection("vendors").document(uid)
    val cleanOwnerName = ownerName.trim().ifEmpty { storeName.trim() }
    val cleanStoreName = storeName.trim()
    val cleanPhone = phone.trim()
    val cleanEmail = email.trim().ifEmpty { "vendor@village.local" }

    try {
      val existingUser = userRef.get().await()
      val existingVendor = vendorRef.get().await()
      val isNewVendorRequest = !existingVendor.exists()
      val preservedApproved =
        if (isNewVendorRequest) false else (existingVendor.getBoolean("isApproved") ?: false)

      val batch = db.batch()

      if (existingUser.exists()) {
        batch.update(
          userRef,
          mapOf(
            "name" to cleanOwnerName,
            "phone" to cleanPhone,
            "role" to "vendor",
            "isApproved" to preservedApproved,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        batch.set(
          userRef,
          mapOf(
            "userId" to uid,
            "name" to cleanOwnerName,
            "phone" to cleanPhone,
            "email" to cleanEmail,
            "role" to "vendor",
            "isApproved" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      if (existingVendor.exists()) {
        batch.update(
          vendorRef,
          mapOf(
            "ownerName" to cleanOwnerName,
            "storeName" to cleanStoreName,
            "activityType" to activityType.key,
            "phone" to cleanPhone,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        batch.set(
          vendorRef,
          mapOf(
            "vendorId" to uid,
            "ownerName" to cleanOwnerName,
            "storeName" to cleanStoreName,
            "activityType" to activityType.key,
            "phone" to cleanPhone,
            "email" to cleanEmail,
            "isApproved" to false,
            "isSuspended" to false,
            "suspensionReason" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )

        // Send instant notification to Admin Dashboard about the pending vendor request
        val adminReqNotifRef = db.collection("notifications").document()
        batch.set(
          adminReqNotifRef,
          mapOf(
            "recipientUid" to "ADMIN_BROADCAST",
            "targetChannel" to "ADMIN",
            "senderUid" to uid,
            "type" to "VENDOR_APPROVAL_REQUEST",
            "title" to "طلب تفعيل محل جديد: $cleanStoreName",
            "body" to
              "صاحب المحل: $cleanOwnerName | النشاط: ${activityType.arabicLabel} | الهاتف: $cleanPhone — بانتظار التفعيل اليدوي.",
            "orderId" to "",
            "vendorId" to uid,
            "storeName" to cleanStoreName,
            "customerName" to cleanOwnerName,
            "customerPhone" to cleanPhone,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, vendorRef.path)
      throw e
    }
  }

  // --- 2. Exclusive Admin Google Account Gate (yahiakhamisasd@gmail.com) ---

  fun observeAdminSession(userId: String): Flow<AdminSession?> =
    db
      .collection("admins")
      .document(userId)
      .snapshots()
      .map { snap ->
        if (snap.exists()) {
          snap.toObject(AdminSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      }
      .guardSignOutStreamError(OperationType.GET, "admins/$userId")

  suspend fun ensureAdminSessionRecorded(email: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val normalizedEmail = email.trim().lowercase()
    if (!SecurityUtils.isAdminGoogleAccount(normalizedEmail)) {
      throw IllegalArgumentException("هذا الحساب غير مصرح له بالدخول إلى لوحة الإدارة.")
    }

    val adminRef = db.collection("admins").document(uid)
    try {
      val snap = adminRef.get().await()
      if (!snap.exists()) {
        adminRef
          .set(
            mapOf(
              "userId" to uid,
              "adminEmail" to normalizedEmail,
              "role" to "admin",
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        adminRef
          .update(mapOf("updatedAt" to FieldValue.serverTimestamp()))
          .await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, adminRef.path)
      throw e
    }
  }

  // --- 3. Vendor Store & Persistent Quantity-Based Offers ---

  fun observeVendorStore(vendorId: String): Flow<VendorStore?> =
    db
      .collection("vendors")
      .document(vendorId)
      .snapshots()
      .map { snap ->
        if (snap.exists()) {
          snap.toObject(VendorStore::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      }
      .guardSignOutStreamError(OperationType.GET, "vendors/$vendorId")

  fun observeCustomerAvailableOffers(): Flow<List<Offer>> =
    db
      .collection("offers")
      .whereEqualTo("isAvailableForCustomers", true)
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(Offer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      }
      .guardSignOutStreamError(OperationType.LIST, "offers")

  fun observeVendorOffers(vendorId: String): Flow<List<Offer>> =
    db
      .collection("offers")
      .whereEqualTo("vendorId", vendorId)
      .snapshots()
      .map { snapshot ->
        snapshot
          .toObjects(Offer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      }
      .guardSignOutStreamError(OperationType.LIST, "offers")

  suspend fun createOffer(
    vendorStore: VendorStore,
    title: String,
    description: String,
    originalPrice: Double,
    offerPrice: Double,
    stockQuantity: Long,
    imageBase64: String,
    defaultImageKey: ActivityType,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    if (!vendorStore.isApproved) {
      throw IllegalStateException("حساب المحل قيد المراجعة ولم يتم تفعيله بعد من قبل الإدارة.")
    }
    if (vendorStore.isSuspended) {
      throw IllegalStateException("حساب المحل معلق حالياً من قبل الإدارة، لا يمكن نشر عروض جديدة.")
    }
    require(stockQuantity >= 1L) { "يجب أن تكون الكمية المتاحة 1 على الأقل" }
    require(offerPrice > 0.0 && offerPrice <= originalPrice) {
      "يجب أن يكون سعر العرض أقل من أو يساوي السعر الأصلي"
    }

    val offerRef = db.collection("offers").document()
    val payload =
      mapOf(
        "vendorId" to uid,
        "storeName" to vendorStore.storeName,
        "activityType" to vendorStore.activityType,
        "title" to title.trim(),
        "description" to description.trim(),
        "originalPrice" to roundCurrency(originalPrice),
        "offerPrice" to roundCurrency(offerPrice),
        "stockQuantity" to stockQuantity,
        "isActive" to true,
        "isVendorSuspended" to false,
        "isAvailableForCustomers" to (stockQuantity > 0L),
        "imageBase64" to imageBase64,
        "defaultImageKey" to defaultImageKey.key,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )

    try {
      offerRef.set(payload).await()
      offerRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, offerRef.path)
      throw e
    }
  }

  suspend fun updateOfferStockAndState(
    offer: Offer,
    newStockQuantity: Long,
    newIsActive: Boolean,
  ): Result<Unit> = runCatching {
    requireUserId()
    require(offer.id.isNotBlank()) { "معرف العرض مطلوب" }
    require(newStockQuantity >= 0L) { "الكمية لا يمكن أن تكون سالبة" }
    val docRef = db.collection("offers").document(offer.id)
    val available = newIsActive && !offer.isVendorSuspended && newStockQuantity > 0L
    try {
      docRef
        .update(
          mapOf(
            "stockQuantity" to newStockQuantity,
            "isActive" to newIsActive,
            "isAvailableForCustomers" to available,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteOffer(offerId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("offers").document(offerId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  // --- 4. Orders & Instant Dual Notifications (Vendor + Admin) ---

  suspend fun placeOrderWithDualNotifications(
    offer: Offer,
    customerName: String,
    customerPhone: String,
    deliveryAddress: String,
    notes: String,
    requestedQuantity: Long,
  ): Result<String> = runCatching {
    val customerUid = requireUserId()
    require(offer.id.isNotBlank()) { "معرف العرض غير صالح" }
    require(requestedQuantity >= 1L) { "الكمية المطلوبة يجب أن تكون 1 على الأقل" }

    val offerRef = db.collection("offers").document(offer.id)
    val orderRef = db.collection("orders").document()
    val vendorNotifRef = db.collection("notifications").document()
    val adminNotifRef = db.collection("notifications").document()

    val cleanName = customerName.trim()
    val cleanPhone = customerPhone.trim()
    val cleanAddress = deliveryAddress.trim()
    val cleanNotes = notes.trim()
    val dateKey = SecurityUtils.currentDateKey()

    try {
      db
        .runTransaction { tx ->
          val offerSnap = tx.get(offerRef)
          if (!offerSnap.exists()) {
            throw IllegalStateException("لم يعد هذا العرض موجوداً")
          }
          val isActive = offerSnap.getBoolean("isActive") ?: false
          val isSuspended = offerSnap.getBoolean("isVendorSuspended") ?: false
          val currentStock = offerSnap.getLong("stockQuantity") ?: 0L
          val unitPrice = offerSnap.getDouble("offerPrice") ?: offer.offerPrice
          val storeName = offerSnap.getString("storeName") ?: offer.storeName
          val vendorId = offerSnap.getString("vendorId") ?: offer.vendorId
          val activityType = offerSnap.getString("activityType") ?: offer.activityType
          val offerTitle = offerSnap.getString("title") ?: offer.title

          if (!isActive || isSuspended || currentStock <= 0L) {
            throw IllegalStateException("عذراً، نفدت كمية هذا العرض أو تم إيقافه للتو")
          }
          if (requestedQuantity > currentStock) {
            throw IllegalStateException("الكمية المتاحة حالياً هي $currentStock فقط")
          }

          val newStock = currentStock - requestedQuantity
          val stillAvailable = newStock > 0L
          val totalPrice = roundCurrency(unitPrice * requestedQuantity)
          val commissionAmount = roundCurrency(totalPrice * 0.10)

          // 1. Atomic stock decrement on Offer (hides automatically when newStock == 0)
          tx.update(
            offerRef,
            mapOf(
              "stockQuantity" to newStock,
              "isAvailableForCustomers" to stillAvailable,
              "lastOrderRefId" to orderRef.id,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )

          // 2. Create live Order document
          tx.set(
            orderRef,
            mapOf(
              "offerId" to offer.id,
              "offerTitle" to offerTitle,
              "vendorId" to vendorId,
              "storeName" to storeName,
              "activityType" to activityType,
              "customerId" to customerUid,
              "customerName" to cleanName,
              "customerPhone" to cleanPhone,
              "deliveryAddress" to cleanAddress,
              "notes" to cleanNotes,
              "quantity" to requestedQuantity,
              "unitOfferPrice" to roundCurrency(unitPrice),
              "totalPrice" to totalPrice,
              "commissionAmount" to commissionAmount,
              "status" to OrderStatus.PENDING.key,
              "cancellationReason" to "",
              "dateKey" to dateKey,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )

          val notifTitle = "طلب جديد: $offerTitle ($storeName)"
          val notifBody =
            "العميل: $cleanName | الهاتف: $cleanPhone | الكمية: $requestedQuantity | الإجمالي: $totalPrice ج.م | العنوان: $cleanAddress"

          // 3A. Instant Notification to Vendor Dashboard
          tx.set(
            vendorNotifRef,
            mapOf(
              "recipientUid" to vendorId,
              "targetChannel" to "VENDOR",
              "senderUid" to customerUid,
              "type" to "NEW_ORDER",
              "title" to notifTitle,
              "body" to notifBody,
              "orderId" to orderRef.id,
              "vendorId" to vendorId,
              "storeName" to storeName,
              "customerName" to cleanName,
              "customerPhone" to cleanPhone,
              "isRead" to false,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )

          // 3B. Identical Simultaneous Notification Copy to Admin Dashboard
          tx.set(
            adminNotifRef,
            mapOf(
              "recipientUid" to "ADMIN_BROADCAST",
              "targetChannel" to "ADMIN",
              "senderUid" to customerUid,
              "type" to "NEW_ORDER",
              "title" to notifTitle,
              "body" to notifBody,
              "orderId" to orderRef.id,
              "vendorId" to vendorId,
              "storeName" to storeName,
              "customerName" to cleanName,
              "customerPhone" to cleanPhone,
              "isRead" to false,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
        .await()
      orderRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, orderRef.path)
      throw e
    }
  }

  fun observeCustomerOrders(customerId: String): Flow<List<Order>> =
    db
      .collection("orders")
      .whereEqualTo("customerId", customerId)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      }
      .guardSignOutStreamError(OperationType.LIST, "orders")

  fun observeVendorOrders(vendorId: String): Flow<List<Order>> =
    db
      .collection("orders")
      .whereEqualTo("vendorId", vendorId)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      }
      .guardSignOutStreamError(OperationType.LIST, "orders")

  fun observeUserNotifications(userId: String): Flow<List<AppNotification>> =
    db
      .collection("notifications")
      .whereEqualTo("recipientUid", userId)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      }
      .guardSignOutStreamError(OperationType.LIST, "notifications")

  suspend fun updateVendorOrderStatus(
    order: Order,
    newStatus: OrderStatus,
    cancellationReason: String = "",
    todayCancelledBeforeThis: Int = 0,
  ): Result<Unit> = runCatching {
    val vendorUid = requireUserId()
    require(order.id.isNotBlank()) { "معرف الطلب مطلوب" }
    val cleanReason = if (newStatus == OrderStatus.CANCELLED) cancellationReason.trim() else ""
    if (newStatus == OrderStatus.CANCELLED) {
      require(cleanReason.length >= 2) { "يرجى كتابة سبب إلغاء الطلب بوضوح" }
    }

    val orderRef = db.collection("orders").document(order.id)
    val customerNotifRef = db.collection("notifications").document()

    try {
      // Read daily cancellation threshold if cancelling
      val dailyThreshold =
        if (newStatus == OrderStatus.CANCELLED) {
          val cfgSnap = db.collection("platform_config").document("settings").get().await()
          (cfgSnap.getLong("dailyCancelAlertLimit") ?: 3L).toInt()
        } else {
          3
        }

      // Restore offer stock if order is cancelled and offer still exists
      val offerRef = db.collection("offers").document(order.offerId)
      val offerSnap =
        if (newStatus == OrderStatus.CANCELLED) {
          offerRef.get().await()
        } else {
          null
        }

      val batch = db.batch()
      val orderUpdates = mutableMapOf<String, Any>(
        "status" to newStatus.key,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      if (newStatus == OrderStatus.CANCELLED) {
        orderUpdates["cancellationReason"] = cleanReason
      }
      batch.update(orderRef, orderUpdates)

      if (newStatus == OrderStatus.CANCELLED && offerSnap != null && offerSnap.exists()) {
        val currentStock = offerSnap.getLong("stockQuantity") ?: 0L
        val restoredStock = currentStock + order.quantity
        val isActive = offerSnap.getBoolean("isActive") ?: true
        val isSuspended = offerSnap.getBoolean("isVendorSuspended") ?: false
        batch.update(
          offerRef,
          mapOf(
            "stockQuantity" to restoredStock,
            "isAvailableForCustomers" to (isActive && !isSuspended && restoredStock > 0L),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      val statusTitle =
        when (newStatus) {
          OrderStatus.ACCEPTED -> "تم قبول طلبك من ${order.storeName}"
          OrderStatus.DELIVERED -> "تم توصيل طلبك بنجاح من ${order.storeName}"
          OrderStatus.CANCELLED -> "تم إلغاء طلبك من ${order.storeName}"
          OrderStatus.PENDING -> "تحديث حالة الطلب"
        }
      val statusBody =
        if (newStatus == OrderStatus.CANCELLED) {
          "العرض: ${order.offerTitle} | سبب الإلغاء: $cleanReason"
        } else {
          "العرض: ${order.offerTitle} | الحالة الحالية: ${newStatus.arabicLabel}"
        }

      batch.set(
        customerNotifRef,
        mapOf(
          "recipientUid" to order.customerId,
          "targetChannel" to "CUSTOMER",
          "senderUid" to vendorUid,
          "type" to "ORDER_STATUS",
          "title" to statusTitle,
          "body" to statusBody,
          "orderId" to order.id,
          "vendorId" to order.vendorId,
          "storeName" to order.storeName,
          "customerName" to order.customerName,
          "customerPhone" to order.customerPhone,
          "isRead" to false,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      if (newStatus == OrderStatus.CANCELLED) {
        val adminCancelNotifRef = db.collection("notifications").document()
        batch.set(
          adminCancelNotifRef,
          mapOf(
            "recipientUid" to "ADMIN_BROADCAST",
            "targetChannel" to "ADMIN",
            "senderUid" to vendorUid,
            "type" to "ORDER_CANCELLED",
            "title" to "إلغاء طلب لدى ${order.storeName}",
            "body" to
              "تم إلغاء طلب العميل ${order.customerName} (${order.customerPhone}) بقيمة ${order.totalPrice} ج.م | السبب: $cleanReason",
            "orderId" to order.id,
            "vendorId" to order.vendorId,
            "storeName" to order.storeName,
            "customerName" to order.customerName,
            "customerPhone" to order.customerPhone,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )

        val newTodayCount = todayCancelledBeforeThis + 1
        if (newTodayCount >= dailyThreshold) {
          val alertRef = db.collection("notifications").document()
          batch.set(
            alertRef,
            mapOf(
              "recipientUid" to "ADMIN_BROADCAST",
              "targetChannel" to "ADMIN",
              "senderUid" to vendorUid,
              "type" to "SUSPICIOUS_CANCELLATIONS",
              "title" to "تحذير إلغاءات مشبوهة: ${order.storeName}",
              "body" to
                "تجاوز المحل الحد اليومي بـ ($newTodayCount إلغاءات اليوم). يرجى مراجعة سجل الهاتف (${order.customerPhone}) لاحتمال التهرب من عمولة 10%.",
              "orderId" to order.id,
              "vendorId" to order.vendorId,
              "storeName" to order.storeName,
              "customerName" to order.customerName,
              "customerPhone" to order.customerPhone,
              "isRead" to false,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }

      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, orderRef.path)
      throw e
    }
  }

  suspend fun markNotificationRead(notificationId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("notifications").document(notificationId)
    try {
      docRef
        .update(mapOf("isRead" to true, "updatedAt" to FieldValue.serverTimestamp()))
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  // --- 5. Admin Dashboard Control, Suspension & 10% Commission Monitoring ---

  fun observeAllOrdersForAdmin(): Flow<List<Order>> =
    db
      .collection("orders")
      .snapshots()
      .map { snap ->
        snap
          .toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      }
      .guardSignOutStreamError(OperationType.LIST, "orders")

  fun observeAllVendorsForAdmin(): Flow<List<VendorStore>> =
    db
      .collection("vendors")
      .snapshots()
      .map { snap ->
        snap
          .toObjects(VendorStore::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      }
      .guardSignOutStreamError(OperationType.LIST, "vendors")

  fun observeAdminNotifications(): Flow<List<AppNotification>> =
    db
      .collection("notifications")
      .whereEqualTo("targetChannel", "ADMIN")
      .snapshots()
      .map { snap ->
        snap
          .toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      }
      .guardSignOutStreamError(OperationType.LIST, "notifications")

  fun observeCommissionSettlementsForAdmin(): Flow<List<CommissionSettlement>> =
    db
      .collection("commission_settlements")
      .snapshots()
      .map { snap ->
        snap
          .toObjects(
            CommissionSettlement::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
          .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
      }
      .guardSignOutStreamError(OperationType.LIST, "commission_settlements")

  fun observePlatformConfig(): Flow<PlatformConfig> =
    db
      .collection("platform_config")
      .document("settings")
      .snapshots()
      .map { snap ->
        if (snap.exists()) {
          snap.toObject(
            PlatformConfig::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          ) ?: PlatformConfig()
        } else {
          PlatformConfig()
        }
      }
      .guardSignOutStreamError(OperationType.GET, "platform_config/settings")

  suspend fun approveVendorAccount(vendor: VendorStore): Result<Unit> = runCatching {
    val adminUid = requireUserId()
    val targetVendorId = vendor.vendorId.ifBlank { vendor.id }
    require(targetVendorId.isNotBlank()) { "معرف المحل غير صالح" }

    val vendorRef = db.collection("vendors").document(targetVendorId)
    val userRef = db.collection("users").document(targetVendorId)
    val vendorNotifRef = db.collection("notifications").document()

    try {
      val userSnap = userRef.get().await()
      val batch = db.batch()
      batch.update(
        vendorRef,
        mapOf(
          "isApproved" to true,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      if (userSnap.exists()) {
        batch.update(
          userRef,
          mapOf(
            "isApproved" to true,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.set(
        vendorNotifRef,
        mapOf(
          "recipientUid" to targetVendorId,
          "targetChannel" to "VENDOR",
          "senderUid" to adminUid,
          "type" to "VENDOR_APPROVED",
          "title" to "تم تفعيل حساب محلك بنجاح!",
          "body" to
            "تمت الموافقة على تفعيل (${vendor.storeName}) من قبل الإدارة. يمكنك الآن نشر عروضك واستقبال طلبات أهل القرية.",
          "orderId" to "",
          "vendorId" to targetVendorId,
          "storeName" to vendor.storeName,
          "customerName" to vendor.ownerName,
          "customerPhone" to vendor.phone,
          "isRead" to false,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, vendorRef.path)
      throw e
    }
  }

  suspend fun rejectVendorRequest(vendor: VendorStore): Result<Unit> = runCatching {
    val adminUid = requireUserId()
    val targetVendorId = vendor.vendorId.ifBlank { vendor.id }
    require(targetVendorId.isNotBlank()) { "معرف المحل غير صالح" }

    val vendorRef = db.collection("vendors").document(targetVendorId)
    val userRef = db.collection("users").document(targetVendorId)
    val vendorNotifRef = db.collection("notifications").document()

    try {
      val userSnap = userRef.get().await()
      val batch = db.batch()
      batch.delete(vendorRef)
      if (userSnap.exists()) {
        batch.update(
          userRef,
          mapOf(
            "role" to "customer",
            "isApproved" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.set(
        vendorNotifRef,
        mapOf(
          "recipientUid" to targetVendorId,
          "targetChannel" to "VENDOR",
          "senderUid" to adminUid,
          "type" to "VENDOR_REJECTED",
          "title" to "تم رفض طلب تفعيل (${vendor.storeName})",
          "body" to
            "تعذر قبول طلب تفعيل المحل حالياً. لمزيد من التفاصيل يرجى التواصل مع إدارة التطبيق على 01556841974.",
          "orderId" to "",
          "vendorId" to targetVendorId,
          "storeName" to vendor.storeName,
          "customerName" to vendor.ownerName,
          "customerPhone" to vendor.phone,
          "isRead" to false,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, vendorRef.path)
      throw e
    }
  }

  suspend fun toggleVendorSuspension(
    vendorId: String,
    suspend: Boolean,
    reason: String,
  ): Result<Unit> = runCatching {
    requireUserId()
    val vendorRef = db.collection("vendors").document(vendorId)
    try {
      val offersSnap = db.collection("offers").whereEqualTo("vendorId", vendorId).get().await()
      val batch = db.batch()
      batch.update(
        vendorRef,
        mapOf(
          "isSuspended" to suspend,
          "suspensionReason" to if (suspend) reason.trim().ifEmpty { "إيقاف إداري لمخالفة سياسة المنصة" } else "",
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      for (doc in offersSnap.documents) {
        val isActive = doc.getBoolean("isActive") ?: true
        val stock = doc.getLong("stockQuantity") ?: 0L
        batch.update(
          doc.reference,
          mapOf(
            "isVendorSuspended" to suspend,
            "isAvailableForCustomers" to (isActive && !suspend && stock > 0L),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, vendorRef.path)
      throw e
    }
  }

  suspend fun updateDailyCancelThreshold(newLimit: Int): Result<Unit> = runCatching {
    val adminUid = requireUserId()
    val safeLimit = newLimit.coerceIn(1, 50)
    val docRef = db.collection("platform_config").document("settings")
    try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef
          .update(
            mapOf(
              "dailyCancelAlertLimit" to safeLimit,
              "updatedBy" to adminUid,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .set(
            mapOf(
              "dailyCancelAlertLimit" to safeLimit,
              "commissionRatePercent" to 10,
              "updatedBy" to adminUid,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  suspend fun recordCashSettlement(
    vendorId: String,
    storeName: String,
    amountCollected: Double,
    note: String,
  ): Result<Unit> = runCatching {
    val adminUid = requireUserId()
    require(amountCollected > 0.0) { "المبلغ المحصل يجب أن يكون أكبر من صفر" }
    val docRef = db.collection("commission_settlements").document()
    try {
      docRef
        .set(
          mapOf(
            "vendorId" to vendorId,
            "storeName" to storeName,
            "amountCollected" to roundCurrency(amountCollected),
            "recordedBy" to adminUid,
            "note" to note.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }
}
