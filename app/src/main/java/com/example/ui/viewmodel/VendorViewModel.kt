package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActivityType
import com.example.data.model.AppNotification
import com.example.data.model.Offer
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.UiState
import com.example.data.model.VendorStore
import com.example.data.repository.MarketplaceRepository
import com.example.data.util.SecurityUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VendorViewModel(
  private val repository: MarketplaceRepository,
  private val currentVendorId: String,
) : ViewModel() {

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _feedbackMessage = MutableStateFlow<String?>(null)
  val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

  val storeState: StateFlow<UiState<VendorStore?>> =
    repository
      .observeVendorStore(currentVendorId)
      .map<VendorStore?, UiState<VendorStore?>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing vendor store", error)
        emit(UiState.Error(error.localizedMessage ?: "تعذر تحميل بيانات المحل"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  val offersState: StateFlow<UiState<List<Offer>>> =
    repository
      .observeVendorOffers(currentVendorId)
      .map<List<Offer>, UiState<List<Offer>>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing vendor offers", error)
        emit(UiState.Error(error.localizedMessage ?: "تعذر تحميل عروض المحل"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  val ordersState: StateFlow<UiState<List<Order>>> =
    repository
      .observeVendorOrders(currentVendorId)
      .map<List<Order>, UiState<List<Order>>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing vendor orders", error)
        emit(UiState.Error(error.localizedMessage ?: "تعذر تحميل طلبات الزبائن"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  val notificationsState: StateFlow<UiState<List<AppNotification>>> =
    repository
      .observeUserNotifications(currentVendorId)
      .map<List<AppNotification>, UiState<List<AppNotification>>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing vendor notifications", error)
        emit(UiState.Error(error.localizedMessage ?: "تعذر تحميل إشعارات المحل"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  fun clearFeedback() {
    _feedbackMessage.value = null
  }

  fun publishNewOffer(
    vendorStore: VendorStore,
    title: String,
    description: String,
    originalPriceText: String,
    offerPriceText: String,
    stockText: String,
    imageBase64: String,
    defaultImageKey: ActivityType,
    onSuccess: () -> Unit,
  ) {
    if (!vendorStore.isApproved) {
      _feedbackMessage.value = "حساب المحل قيد المراجعة؛ يتطلب التفعيل التواصل مع الإدارة أولاً."
      return
    }
    if (vendorStore.isSuspended) {
      _feedbackMessage.value = "حساب المحل معلق من الإدارة؛ لا يمكن إضافة عروض جديدة حالياً."
      return
    }
    if (title.trim().length < 2) {
      _feedbackMessage.value = "يرجى إدخال اسم العرض"
      return
    }
    val originalPrice = originalPriceText.trim().toDoubleOrNull()
    val offerPrice = offerPriceText.trim().toDoubleOrNull()
    val stockQuantity = stockText.trim().toLongOrNull()

    if (originalPrice == null || originalPrice <= 0.0) {
      _feedbackMessage.value = "يرجى إدخال السعر الأصلي بشكل صحيح"
      return
    }
    if (offerPrice == null || offerPrice <= 0.0 || offerPrice > originalPrice) {
      _feedbackMessage.value = "سعر العرض يجب أن يكون أكبر من صفر وأقل من أو يساوي السعر الأصلي"
      return
    }
    if (stockQuantity == null || stockQuantity < 1L) {
      _feedbackMessage.value = "الكمية المتاحة يجب أن تكون 1 على الأقل"
      return
    }

    _isBusy.value = true
    viewModelScope.launch {
      val res =
        repository.createOffer(
          vendorStore = vendorStore,
          title = title,
          description = description.ifBlank { "عرض خاص من ${vendorStore.storeName}" },
          originalPrice = originalPrice,
          offerPrice = offerPrice,
          stockQuantity = stockQuantity,
          imageBase64 = imageBase64,
          defaultImageKey = defaultImageKey,
        )
      _isBusy.value = false
      res
        .onSuccess {
          _feedbackMessage.value = "تم نشر العرض بنجاح وسيظل متاحاً حتى انتهاء الكمية أو إيقافه!"
          onSuccess()
        }
        .onFailure { err -> _feedbackMessage.value = err.localizedMessage ?: "تعذر نشر العرض" }
    }
  }

  fun updateOfferStockOrToggle(offer: Offer, newStock: Long, newIsActive: Boolean) {
    _isBusy.value = true
    viewModelScope.launch {
      val res = repository.updateOfferStockAndState(offer, newStock, newIsActive)
      _isBusy.value = false
      res
        .onSuccess {
          _feedbackMessage.value =
            if (!newIsActive || newStock == 0L) "تم إيقاف/إخفاء العرض عن الزبائن"
            else "تم تحديث العرض والكمية المتاحة ($newStock)"
        }
        .onFailure { err -> _feedbackMessage.value = err.localizedMessage ?: "فشل تحديث العرض" }
    }
  }

  fun deleteOffer(offerId: String) {
    _isBusy.value = true
    viewModelScope.launch {
      val res = repository.deleteOffer(offerId)
      _isBusy.value = false
      res
        .onSuccess { _feedbackMessage.value = "تم حذف العرض نهائياً" }
        .onFailure { err -> _feedbackMessage.value = err.localizedMessage ?: "تعذر حذف العرض" }
    }
  }

  fun handleOrderAction(order: Order, targetStatus: OrderStatus, cancellationReason: String = "") {
    if (targetStatus == OrderStatus.CANCELLED && cancellationReason.trim().length < 2) {
      _feedbackMessage.value = "يرجى كتابة سبب إلغاء الطلب أولاً"
      return
    }
    val todayKey = SecurityUtils.currentDateKey()
    val currentOrders = (ordersState.value as? UiState.Success)?.data.orEmpty()
    val todayCancelledBefore =
      currentOrders.count { it.dateKey == todayKey && it.status == OrderStatus.CANCELLED.key }

    _isBusy.value = true
    viewModelScope.launch {
      val res =
        repository.updateVendorOrderStatus(
          order = order,
          newStatus = targetStatus,
          cancellationReason = cancellationReason,
          todayCancelledBeforeThis = todayCancelledBefore,
        )
      _isBusy.value = false
      res
        .onSuccess {
          _feedbackMessage.value =
            when (targetStatus) {
              OrderStatus.ACCEPTED -> "تم قبول الطلب وإشعار العميل"
              OrderStatus.DELIVERED -> "تم تأكيد توصيل الطلب واحتسابه ضمن المبيعات المكتملة"
              OrderStatus.CANCELLED -> "تم إلغاء الطلب وإعادة الكمية للمخزون وتسجيل السبب"
              OrderStatus.PENDING -> "تم تحديث الطلب"
            }
        }
        .onFailure { err -> _feedbackMessage.value = err.localizedMessage ?: "تعذر تحديث حالة الطلب" }
    }
  }

  fun markNotificationRead(notificationId: String) {
    viewModelScope.launch { repository.markNotificationRead(notificationId) }
  }

  private companion object {
    const val TAG = "VendorVM"
  }
}
