package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.roundToInt

enum class AccountRole(val firestoreValue: String, val arabicLabel: String) {
  CUSTOMER("customer", "عميل / زبون"),
  VENDOR("vendor", "صاحب محل / تجاري"),
  ADMIN("admin", "مدير المنصة");

  companion object {
    fun fromFirestore(value: String?): AccountRole =
      entries.firstOrNull { it.firestoreValue.equals(value, ignoreCase = true) } ?: CUSTOMER
  }
}

enum class ActivityType(val key: String, val arabicLabel: String) {
  RESTAURANT("RESTAURANT", "مطعم"),
  SUPERMARKET("SUPERMARKET", "سوبر ماركت"),
  CAFE("CAFE", "مقهى");

  companion object {
    fun fromKey(value: String?): ActivityType =
      entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: RESTAURANT
  }
}

enum class OrderStatus(val key: String, val arabicLabel: String) {
  PENDING("PENDING", "قيد الانتظار"),
  ACCEPTED("ACCEPTED", "مقبول وجاري التجهيز"),
  DELIVERED("DELIVERED", "تم التوصيل"),
  CANCELLED("CANCELLED", "ملغي");

  companion object {
    fun fromKey(value: String?): OrderStatus =
      entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: PENDING
  }
}

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val phone: String = "",
  val email: String = "",
  val role: String = "customer",
  @get:PropertyName("isApproved") @set:PropertyName("isApproved") var isApproved: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AdminSession(
  @DocumentId val id: String = "",
  val userId: String = "",
  val adminEmail: String = "",
  val role: String = "admin",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class VendorStore(
  @DocumentId val id: String = "",
  val vendorId: String = "",
  val ownerName: String = "",
  val storeName: String = "",
  val activityType: String = "RESTAURANT",
  val phone: String = "",
  val email: String = "",
  @get:PropertyName("isApproved") @set:PropertyName("isApproved") var isApproved: Boolean = false,
  @get:PropertyName("isSuspended") @set:PropertyName("isSuspended") var isSuspended: Boolean = false,
  val suspensionReason: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Offer(
  @DocumentId val id: String = "",
  val vendorId: String = "",
  val storeName: String = "",
  val activityType: String = "RESTAURANT",
  val title: String = "",
  val description: String = "",
  val originalPrice: Double = 0.0,
  val offerPrice: Double = 0.0,
  val stockQuantity: Long = 0L,
  @get:PropertyName("isActive") @set:PropertyName("isActive") var isActive: Boolean = true,
  @get:PropertyName("isVendorSuspended")
  @set:PropertyName("isVendorSuspended")
  var isVendorSuspended: Boolean = false,
  @get:PropertyName("isAvailableForCustomers")
  @set:PropertyName("isAvailableForCustomers")
  var isAvailableForCustomers: Boolean = true,
  val imageBase64: String = "",
  val defaultImageKey: String = "RESTAURANT",
  val lastOrderRefId: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val discountPercent: Int
    get() =
      if (originalPrice > 0 && offerPrice < originalPrice) {
        (((originalPrice - offerPrice) / originalPrice) * 100.0).roundToInt().coerceIn(1, 99)
      } else {
        0
      }
}

@IgnoreExtraProperties
data class Order(
  @DocumentId val id: String = "",
  val offerId: String = "",
  val offerTitle: String = "",
  val vendorId: String = "",
  val storeName: String = "",
  val activityType: String = "RESTAURANT",
  val customerId: String = "",
  val customerName: String = "",
  val customerPhone: String = "",
  val deliveryAddress: String = "",
  val notes: String = "",
  val quantity: Long = 1L,
  val unitOfferPrice: Double = 0.0,
  val totalPrice: Double = 0.0,
  val commissionAmount: Double = 0.0,
  val status: String = "PENDING",
  val cancellationReason: String = "",
  val dateKey: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AppNotification(
  @DocumentId val id: String = "",
  val recipientUid: String = "",
  val targetChannel: String = "VENDOR",
  val senderUid: String = "",
  val type: String = "NEW_ORDER",
  val title: String = "",
  val body: String = "",
  val orderId: String = "",
  val vendorId: String = "",
  val storeName: String = "",
  val customerName: String = "",
  val customerPhone: String = "",
  @get:PropertyName("isRead") @set:PropertyName("isRead") var isRead: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PlatformConfig(
  @DocumentId val id: String = "",
  val dailyCancelAlertLimit: Long = 3L,
  val commissionRatePercent: Long = 10L,
  val updatedBy: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CommissionSettlement(
  @DocumentId val id: String = "",
  val vendorId: String = "",
  val storeName: String = "",
  val amountCollected: Double = 0.0,
  val recordedBy: String = "",
  val note: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class VendorFinancialSummary(
  val vendor: VendorStore,
  val completedOrdersCount: Int,
  val totalCompletedSales: Double,
  val totalCommissionEarned: Double,
  val totalCashCollected: Double,
  val netCommissionDue: Double,
  val todayCancelledCount: Int,
  val totalCancelledCount: Int,
  val isExceedingDailyCancelLimit: Boolean,
)

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}
