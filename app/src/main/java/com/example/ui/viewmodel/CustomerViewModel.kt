package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppNotification
import com.example.data.model.Offer
import com.example.data.model.Order
import com.example.data.model.UiState
import com.example.data.repository.MarketplaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustomerViewModel(
  private val repository: MarketplaceRepository,
  private val currentUserId: String,
  private val userEmail: String = "",
) : ViewModel() {

  private val _selectedCategory = MutableStateFlow("ALL")
  val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _orderSubmitting = MutableStateFlow(false)
  val orderSubmitting: StateFlow<Boolean> = _orderSubmitting.asStateFlow()

  private val _feedbackMessage = MutableStateFlow<String?>(null)
  val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

  private val rawOffersFlow = repository.observeCustomerAvailableOffers()

  val offersState: StateFlow<UiState<List<Offer>>> =
    combine(rawOffersFlow, _selectedCategory, _searchQuery) { offers, category, query ->
        offers.filter { offer ->
          val matchesCat = category == "ALL" || offer.activityType.equals(category, ignoreCase = true)
          val cleanQuery = query.trim()
          val matchesSearch =
            cleanQuery.isEmpty() ||
              offer.title.contains(cleanQuery, ignoreCase = true) ||
              offer.storeName.contains(cleanQuery, ignoreCase = true) ||
              offer.description.contains(cleanQuery, ignoreCase = true)
          matchesCat && matchesSearch
        }
      }
      .map<List<Offer>, UiState<List<Offer>>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing customer offers", error)
        emit(UiState.Error(error.localizedMessage ?: "تعذر تحميل عروض القرية"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  val myOrdersState: StateFlow<UiState<List<Order>>> =
    repository
      .observeCustomerOrders(currentUserId)
      .map<List<Order>, UiState<List<Order>>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing customer orders", error)
        emit(UiState.Error(error.localizedMessage ?: "تعذر تحميل طلباتك"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  val notificationsState: StateFlow<UiState<List<AppNotification>>> =
    repository
      .observeUserNotifications(currentUserId)
      .map<List<AppNotification>, UiState<List<AppNotification>>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing customer notifications", error)
        emit(UiState.Error(error.localizedMessage ?: "تعذر تحميل الإشعارات"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  fun setCategory(categoryKey: String) {
    _selectedCategory.value = categoryKey
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun clearFeedback() {
    _feedbackMessage.value = null
  }

  fun placeOrder(
    offer: Offer,
    customerName: String,
    customerPhone: String,
    deliveryAddress: String,
    notes: String,
    quantity: Long,
    onOrderCreated: () -> Unit,
  ) {
    if (customerName.trim().length < 2) {
      _feedbackMessage.value = "يرجى إدخال اسم المستلم"
      return
    }
    if (customerPhone.trim().length < 8) {
      _feedbackMessage.value = "يرجى إدخال رقم هاتف صحيح للتوصيل"
      return
    }
    if (deliveryAddress.trim().length < 4) {
      _feedbackMessage.value = "يرجى كتابة عنوان التوصيل داخل القرية أو علامة مميزة"
      return
    }
    if (quantity < 1L || quantity > offer.stockQuantity) {
      _feedbackMessage.value = "الكمية المطلوبة تتجاوز المخزون المتاح (${offer.stockQuantity})"
      return
    }

    _orderSubmitting.value = true
    viewModelScope.launch {
      val result =
        repository.placeOrderWithDualNotifications(
          offer = offer,
          customerName = customerName,
          customerPhone = customerPhone,
          deliveryAddress = deliveryAddress,
          notes = notes,
          requestedQuantity = quantity,
        )
      _orderSubmitting.value = false
      result
        .onSuccess {
          // Persist customer contact details for convenience on future orders
          launch {
            repository.saveCustomerProfile(customerName, customerPhone, userEmail)
          }
          _feedbackMessage.value = "تم تأكيد طلبك بنجاح وإرسال إشعار فوري للمحل والإدارة!"
          onOrderCreated()
        }
        .onFailure { err ->
          _feedbackMessage.value = err.localizedMessage ?: "تعذر إتمام الطلب، يرجى المحاولة مجدداً"
        }
    }
  }

  fun markNotificationRead(notificationId: String) {
    viewModelScope.launch { repository.markNotificationRead(notificationId) }
  }

  private companion object {
    const val TAG = "CustomerVM"
  }
}
