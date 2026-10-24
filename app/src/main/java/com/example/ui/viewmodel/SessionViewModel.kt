package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AccountRole
import com.example.data.model.ActivityType
import com.example.data.model.AdminSession
import com.example.data.model.UserProfile
import com.example.data.model.VendorStore
import com.example.data.repository.MarketplaceRepository
import com.example.data.util.SecurityUtils
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SessionDestination {
  data object Loading : SessionDestination
  data object RoleSelection : SessionDestination
  data class VendorOnboarding(val email: String, val initialOwnerName: String) : SessionDestination
  data class VendorPendingApproval(val store: VendorStore) : SessionDestination
  data class CustomerReady(val profile: UserProfile) : SessionDestination
  data class VendorReady(val profile: UserProfile?, val store: VendorStore) : SessionDestination
  data class AdminReady(val session: AdminSession) : SessionDestination
}

class SessionViewModel(
  private val repository: MarketplaceRepository,
  private val currentUserId: String,
  private val userEmail: String,
  private val userDisplayName: String,
  initialPreferredRole: AccountRole?,
) : ViewModel() {

  val isAdminAccount: Boolean = SecurityUtils.isAdminGoogleAccount(userEmail)

  // If signed in as yahiakhamisasd@gmail.com, always route directly to Admin Dashboard on login.
  // If signed in with any other Google account, block ADMIN and default to CUSTOMER unless VENDOR was chosen.
  private val sanitizedInitialRole: AccountRole? =
    when {
      isAdminAccount -> AccountRole.ADMIN
      initialPreferredRole == AccountRole.VENDOR -> AccountRole.VENDOR
      initialPreferredRole == AccountRole.CUSTOMER -> AccountRole.CUSTOMER
      else -> null
    }

  private val _preferredRole = MutableStateFlow(sanitizedInitialRole)
  private val _forceRoleChooser = MutableStateFlow(false)

  private val _actionLoading = MutableStateFlow(false)
  val actionLoading: StateFlow<Boolean> = _actionLoading.asStateFlow()

  private val _actionError = MutableStateFlow<String?>(null)
  val actionError: StateFlow<String?> = _actionError.asStateFlow()

  private val autoCustomerProfileCreated = AtomicBoolean(false)

  init {
    if (isAdminAccount) {
      viewModelScope.launch {
        repository.ensureAdminSessionRecorded(userEmail).onFailure { e ->
          Log.w(TAG, "Failed recording admin session document", e)
        }
      }
    }
  }

  private fun defaultCustomerName(): String {
    val cleanDisplay = userDisplayName.trim()
    if (cleanDisplay.length >= 2) return cleanDisplay
    val emailPrefix = userEmail.substringBefore('@').trim()
    if (emailPrefix.length >= 2) return emailPrefix
    return "عميل القرية"
  }

  private fun ensureCustomerProfileInBackground(existingProfile: UserProfile?) {
    if (existingProfile == null && autoCustomerProfileCreated.compareAndSet(false, true)) {
      viewModelScope.launch {
        repository
          .saveCustomerProfile(
            name = defaultCustomerName(),
            phone = "",
            email = userEmail,
          )
          .onFailure { e ->
            autoCustomerProfileCreated.set(false)
            Log.w(TAG, "Failed auto-creating customer profile", e)
          }
      }
    }
  }

  private val profileFlow =
    repository.observeUserProfile(currentUserId).catch { e ->
      Log.w(TAG, "Error observing userProfile", e)
      emit(null)
    }

  private val vendorFlow =
    repository.observeVendorStore(currentUserId).catch { e ->
      Log.w(TAG, "Error observing vendorStore", e)
      emit(null)
    }

  private val adminFlow =
    if (isAdminAccount) {
      repository.observeAdminSession(currentUserId).catch { e ->
        Log.w(TAG, "Error observing adminSession", e)
        emit(null)
      }
    } else {
      MutableStateFlow(null)
    }

  val destination: StateFlow<SessionDestination> =
    combine(profileFlow, vendorFlow, adminFlow, _preferredRole, _forceRoleChooser) {
        profile,
        vendor,
        admin,
        preferredRole,
        forceChooser ->
        if (forceChooser) {
          return@combine SessionDestination.RoleSelection
        }

        // 1. Admin route: strictly and exclusively for yahiakhamisasd@gmail.com
        if (isAdminAccount && (preferredRole == null || preferredRole == AccountRole.ADMIN)) {
          val activeAdminSession =
            admin
              ?: AdminSession(
                id = currentUserId,
                userId = currentUserId,
                adminEmail = SecurityUtils.ADMIN_GOOGLE_EMAIL,
                role = "admin",
              )
          return@combine SessionDestination.AdminReady(activeAdminSession)
        }

        // 2. Vendor route: if user selected Vendor or already registered a store (with no customer override)
        val wantsVendor =
          preferredRole == AccountRole.VENDOR ||
            (preferredRole == null &&
              !isAdminAccount &&
              (profile?.role == "vendor" || vendor != null))
        if (wantsVendor) {
          return@combine when {
            vendor == null ->
              SessionDestination.VendorOnboarding(
                email = userEmail,
                initialOwnerName = defaultCustomerName(),
              )
            !vendor.isApproved -> SessionDestination.VendorPendingApproval(store = vendor)
            else -> SessionDestination.VendorReady(profile = profile, store = vendor)
          }
        }

        // 3. Default for every non-admin Google login -> Enter directly as Customer!
        ensureCustomerProfileInBackground(profile)
        val effectiveCustomerProfile =
          profile
            ?: UserProfile(
              id = currentUserId,
              userId = currentUserId,
              name = defaultCustomerName(),
              phone = "",
              email = userEmail,
              role = "customer",
            )
        SessionDestination.CustomerReady(effectiveCustomerProfile)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = SessionDestination.Loading,
      )

  fun chooseRole(role: AccountRole) {
    _actionError.value = null
    // Guard: non-admin Google accounts can NEVER select ADMIN; redirect them to CUSTOMER
    val allowedRole =
      if (role == AccountRole.ADMIN && !isAdminAccount) {
        AccountRole.CUSTOMER
      } else {
        role
      }
    _preferredRole.value = allowedRole
    _forceRoleChooser.value = false
  }

  fun openRoleSwitcher() {
    _actionError.value = null
    _forceRoleChooser.value = true
  }

  fun submitVendorProfile(
    ownerName: String,
    storeName: String,
    activityType: ActivityType,
    phone: String,
  ) {
    if (ownerName.trim().length < 2) {
      _actionError.value = "يرجى إدخال اسم صاحب المحل"
      return
    }
    if (storeName.trim().length < 2) {
      _actionError.value = "يرجى إدخال اسم المحل التجاري"
      return
    }
    if (phone.trim().length < 8) {
      _actionError.value = "يرجى إدخال رقم هاتف المحل للتواصل"
      return
    }
    _actionLoading.value = true
    _actionError.value = null
    viewModelScope.launch {
      val res = repository.saveVendorProfile(ownerName, storeName, activityType, phone, userEmail)
      _actionLoading.value = false
      res
        .onSuccess {
          _preferredRole.value = AccountRole.VENDOR
          _forceRoleChooser.value = false
        }
        .onFailure { err -> _actionError.value = err.localizedMessage ?: "تعذر حفظ بيانات المحل" }
    }
  }

  private companion object {
    const val TAG = "SessionVM"
  }
}
