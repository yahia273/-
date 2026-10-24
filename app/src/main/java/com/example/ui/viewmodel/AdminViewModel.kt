package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppNotification
import com.example.data.model.CommissionSettlement
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.PlatformConfig
import com.example.data.model.UiState
import com.example.data.model.VendorFinancialSummary
import com.example.data.model.VendorStore
import com.example.data.repository.MarketplaceRepository
import com.example.data.util.SecurityUtils
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(private val repository: MarketplaceRepository) : ViewModel() {

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _feedbackMessage = MutableStateFlow<String?>(null)
  val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

  private val ordersResultFlow =
    repository
      .observeAllOrdersForAdmin()
      .map { Result.success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing admin orders", error)
        emit(Result.failure(error))
      }
      .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), replay = 1)

  private val vendorsResultFlow =
    repository
      .observeAllVendorsForAdmin()
      .map { Result.success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing admin vendors", error)
        emit(Result.failure(error))
      }
      .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), replay = 1)

  val ordersState: StateFlow<UiState<List<Order>>> =
    ordersResultFlow
      .map { result ->
        result.fold(
          onSuccess = { UiState.Success(it) },
          onFailure = { UiState.Error(it.localizedMessage ?: "تعذر تحميل سجل الطلبات الحية") },
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  val notificationsState: StateFlow<UiState<List<AppNotification>>> =
    repository
      .observeAdminNotifications()
      .map<List<AppNotification>, UiState<List<AppNotification>>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing admin notifications", error)
        emit(UiState.Error(error.localizedMessage ?: "تعذر تحميل إشعارات الإدارة"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  val configState: StateFlow<PlatformConfig> =
    repository
      .observePlatformConfig()
      .catch { error ->
        Log.w(TAG, "Error observing platform config", error)
        emit(PlatformConfig())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = PlatformConfig(),
      )

  val settlementsState: StateFlow<List<CommissionSettlement>> =
    repository
      .observeCommissionSettlementsForAdmin()
      .catch { error ->
        Log.w(TAG, "Error observing commission settlements", error)
        emit(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList(),
      )

  val pendingVendorsState: StateFlow<UiState<List<VendorStore>>> =
    vendorsResultFlow
      .map { result ->
        result.fold(
          onSuccess = { list -> UiState.Success(list.filter { !it.isApproved }) },
          onFailure = { UiState.Error(it.localizedMessage ?: "تعذر تحميل طلبات التفعيل المعلقة") },
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  val financialSummariesState: StateFlow<UiState<List<VendorFinancialSummary>>> =
    combine(vendorsResultFlow, ordersResultFlow, settlementsState, configState) {
        vendorsRes: Result<List<VendorStore>>,
        ordersRes: Result<List<Order>>,
        settlements: List<CommissionSettlement>,
        config: PlatformConfig ->
        val vendors =
          vendorsRes.getOrElse { err ->
            return@combine UiState.Error(err.localizedMessage ?: "تعذر تحميل بيانات المحلات")
          }
        val orders =
          ordersRes.getOrElse { err ->
            return@combine UiState.Error(err.localizedMessage ?: "تعذر تحميل بيانات الطلبات")
          }

        val todayKey = SecurityUtils.currentDateKey()
        val dailyLimit = config.dailyCancelAlertLimit.toInt().coerceAtLeast(1)

        val summaries =
          vendors
            .filter { it.isApproved }
            .map { vendor ->
              val storeOrders = orders.filter { it.vendorId == vendor.vendorId }
              val completedOrders = storeOrders.filter { it.status == OrderStatus.DELIVERED.key }
              val cancelledOrders = storeOrders.filter { it.status == OrderStatus.CANCELLED.key }
              val todayCancelled = cancelledOrders.count { it.dateKey == todayKey }

              val totalSales = roundCurrency(completedOrders.sumOf { it.totalPrice })
              val totalCommission = roundCurrency(completedOrders.sumOf { it.commissionAmount })
              val totalCollected =
                roundCurrency(
                  settlements.filter { it.vendorId == vendor.vendorId }.sumOf { it.amountCollected }
                )
              val netDue = roundCurrency((totalCommission - totalCollected).coerceAtLeast(0.0))

              VendorFinancialSummary(
                vendor = vendor,
                completedOrdersCount = completedOrders.size,
                totalCompletedSales = totalSales,
                totalCommissionEarned = totalCommission,
                totalCashCollected = totalCollected,
                netCommissionDue = netDue,
                todayCancelledCount = todayCancelled,
                totalCancelledCount = cancelledOrders.size,
                isExceedingDailyCancelLimit = todayCancelled >= dailyLimit,
              )
            }
            .sortedWith(
              compareByDescending<VendorFinancialSummary> { it.isExceedingDailyCancelLimit }
                .thenByDescending { it.netCommissionDue }
                .thenByDescending { it.totalCompletedSales }
            )
        UiState.Success(summaries)
      }
      .catch { error ->
        Log.w(TAG, "Error computing financial summaries", error)
        emit(UiState.Error(error.localizedMessage ?: "تعذر حساب الجدول المالي والعمولات"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  private fun roundCurrency(value: Double): Double = (value * 100.0).roundToLong() / 100.0

  fun clearFeedback() {
    _feedbackMessage.value = null
  }

  fun approveVendor(vendor: VendorStore) {
    _isBusy.value = true
    viewModelScope.launch {
      val res = repository.approveVendorAccount(vendor)
      _isBusy.value = false
      res
        .onSuccess {
          _feedbackMessage.value =
            "تم تفعيل حساب (${vendor.storeName}) بنجاح وفتح كافة الصلاحيات للمحل فوراً!"
        }
        .onFailure { err ->
          _feedbackMessage.value = err.localizedMessage ?: "تعذر تفعيل حساب المحل"
        }
    }
  }

  fun rejectVendor(vendor: VendorStore) {
    _isBusy.value = true
    viewModelScope.launch {
      val res = repository.rejectVendorRequest(vendor)
      _isBusy.value = false
      res
        .onSuccess {
          _feedbackMessage.value = "تم رفض وحذف طلب تفعيل (${vendor.storeName})"
        }
        .onFailure { err ->
          _feedbackMessage.value = err.localizedMessage ?: "تعذر رفض طلب التفعيل"
        }
    }
  }

  fun toggleSuspension(vendor: VendorStore, suspend: Boolean, reason: String) {
    _isBusy.value = true
    viewModelScope.launch {
      val res = repository.toggleVendorSuspension(vendor.vendorId, suspend, reason)
      _isBusy.value = false
      res
        .onSuccess {
          _feedbackMessage.value =
            if (suspend) "تم تعليق حساب (${vendor.storeName}) وإخفاء جميع عروضه عن الزبائن فوراً"
            else "تمت إعادة تفعيل حساب (${vendor.storeName}) بنجاح"
        }
        .onFailure { err -> _feedbackMessage.value = err.localizedMessage ?: "تعذر تحديث حالة المحل" }
    }
  }

  fun recordCashCollection(vendor: VendorStore, amountText: String, note: String, onDone: () -> Unit) {
    val amount = amountText.trim().toDoubleOrNull()
    if (amount == null || amount <= 0.0) {
      _feedbackMessage.value = "يرجى إدخال مبلغ التحصيل النقدي بشكل صحيح"
      return
    }
    _isBusy.value = true
    viewModelScope.launch {
      val res = repository.recordCashSettlement(vendor.vendorId, vendor.storeName, amount, note)
      _isBusy.value = false
      res
        .onSuccess {
          _feedbackMessage.value = "تم تسجيل تحصيل ${amount} ج.م كاش من ${vendor.storeName}"
          onDone()
        }
        .onFailure { err ->
          _feedbackMessage.value = err.localizedMessage ?: "تعذر تسجيل التحصيل النقدي"
        }
    }
  }

  fun updateDailyCancelLimit(newLimit: Int) {
    _isBusy.value = true
    viewModelScope.launch {
      val res = repository.updateDailyCancelThreshold(newLimit)
      _isBusy.value = false
      res
        .onSuccess {
          _feedbackMessage.value = "تم تحديث حد تنبيهات الإلغاء اليومية إلى ($newLimit إلغاءات/يوم)"
        }
        .onFailure { err ->
          _feedbackMessage.value = err.localizedMessage ?: "تعذر حفظ إعدادات الرقابة"
        }
    }
  }

  fun markNotificationRead(notificationId: String) {
    viewModelScope.launch { repository.markNotificationRead(notificationId) }
  }

  private companion object {
    const val TAG = "AdminVM"
  }
}
