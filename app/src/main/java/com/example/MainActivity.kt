package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.model.AccountRole
import com.example.data.repository.MarketplaceRepository
import com.example.data.util.LocalNotificationHelper
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.CustomerMainScreen
import com.example.ui.screens.RoleSelectionScreen
import com.example.ui.screens.VendorDashboardScreen
import com.example.ui.screens.VendorOnboardingScreen
import com.example.ui.screens.VendorPendingApprovalScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.CustomerViewModel
import com.example.ui.viewmodel.SessionDestination
import com.example.ui.viewmodel.SessionViewModel
import com.example.ui.viewmodel.VendorViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Holds a session-scoped [ViewModelStore] tied to the active Firebase UID.
 * Clearing this store synchronously before calling `Firebase.auth.signOut()` cancels all
 * `viewModelScope` coroutines and detaches every Firestore `.snapshots()` listener while the
 * user's auth token is still valid, preventing unauthenticated PERMISSION_DENIED errors on logout.
 */
class AuthSessionStoreHolder : ViewModel() {
  private var activeUid: String? = null
  private var activeStore: ViewModelStore? = null

  fun getOrCreateStore(uid: String): ViewModelStore {
    if (activeUid != uid || activeStore == null) {
      activeStore?.clear()
      activeUid = uid
      activeStore = ViewModelStore()
    }
    return activeStore!!
  }

  fun clearSessionStore() {
    activeStore?.clear()
    activeStore = null
    activeUid = null
  }

  override fun onCleared() {
    clearSessionStore()
    super.onCleared()
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    LocalNotificationHelper.ensureChannels(this)
    enableEdgeToEdge()
    setContent { MyApplicationTheme { VillageOffersApp() } }
  }
}

@Composable
fun VillageOffersApp(auth: FirebaseAuth = Firebase.auth) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val parentOwner = LocalViewModelStoreOwner.current
  val sessionHolder: AuthSessionStoreHolder = viewModel()

  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  var unauthSelectedRoleName by rememberSaveable { mutableStateOf<String?>(null) }
  var isSigningOut by remember { mutableStateOf(false) }

  val user = currentUser
  LaunchedEffect(user?.uid) {
    if (user == null) {
      sessionHolder.clearSessionStore()
      isSigningOut = false
    }
  }

  if (user == null || isSigningOut) {
    if (isSigningOut) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
      ) {
        CircularProgressIndicator()
      }
    } else {
      // Unauthenticated session: Interactive Google Sign-In + Customer/Vendor role selection.
      // Signing in with yahiakhamisasd@gmail.com automatically routes to Admin Dashboard;
      // signing in with any other Google account routes directly as Customer (or Vendor if selected).
      RoleSelectionScreen(
        isSignedIn = false,
        signedInEmail = null,
        onSelectCustomer = {},
        onSelectVendor = {},
        onBeforeUnauthSignIn = { roleHint ->
          unauthSelectedRoleName = roleHint?.firestoreValue
        },
      )
    }
  } else {
    val sessionStore = remember(user.uid) { sessionHolder.getOrCreateStore(user.uid) }
    val sessionStoreOwner =
      remember(sessionStore, parentOwner) {
        object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
          override val viewModelStore: ViewModelStore = sessionStore
          override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() =
              (parentOwner as? HasDefaultViewModelProviderFactory)?.defaultViewModelProviderFactory
                ?: ViewModelProvider.NewInstanceFactory()
          override val defaultViewModelCreationExtras: CreationExtras
            get() =
              (parentOwner as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
                ?: CreationExtras.Empty
        }
      }

    val performFullSignOut: () -> Unit = {
      if (!isSigningOut) {
        isSigningOut = true
        unauthSelectedRoleName = null
        // 1. Synchronously cancel all authenticated ViewModels & schedule ListenerRegistration.remove()
        sessionHolder.clearSessionStore()
        // 2. Yield on Main Looper so all Firestore snapshot listeners detach BEFORE auth.signOut()
        scope.launch {
          yield()
          withContext(Dispatchers.Main) { yield() }
          signOut(
            credentialManager = credentialManager,
            onSignOutComplete = { isSigningOut = false },
            scope = scope,
          )
        }
      }
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides sessionStoreOwner) {
      val initialPreferredRole = unauthSelectedRoleName?.let { AccountRole.fromFirestore(it) }
      val sessionViewModel: SessionViewModel =
        viewModel(
          key = "session_${user.uid}",
          factory =
            viewModelFactory {
              initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SessionViewModel(
                  repository = MarketplaceRepository(db),
                  currentUserId = user.uid,
                  userEmail = user.email ?: "",
                  userDisplayName = user.displayName ?: "",
                  initialPreferredRole = initialPreferredRole,
                )
              }
            },
        )

      val destination by sessionViewModel.destination.collectAsStateWithLifecycle()
      val actionLoading by sessionViewModel.actionLoading.collectAsStateWithLifecycle()
      val actionError by sessionViewModel.actionError.collectAsStateWithLifecycle()

      when (val dest = destination) {
        is SessionDestination.Loading -> {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
          ) {
            CircularProgressIndicator()
          }
        }

        is SessionDestination.RoleSelection -> {
          RoleSelectionScreen(
            isSignedIn = true,
            signedInEmail = user.email,
            onSelectCustomer = { sessionViewModel.chooseRole(AccountRole.CUSTOMER) },
            onSelectVendor = { sessionViewModel.chooseRole(AccountRole.VENDOR) },
            onSelectAdminDashboard = { sessionViewModel.chooseRole(AccountRole.ADMIN) },
            onSignOut = performFullSignOut,
          )
        }

        is SessionDestination.VendorOnboarding -> {
          VendorOnboardingScreen(
            email = dest.email,
            initialOwnerName = dest.initialOwnerName,
            isSaving = actionLoading,
            errorMessage = actionError,
            onSubmit = sessionViewModel::submitVendorProfile,
            onBack = { sessionViewModel.chooseRole(AccountRole.CUSTOMER) },
          )
        }

        is SessionDestination.VendorPendingApproval -> {
          VendorPendingApprovalScreen(
            store = dest.store,
            onBrowseAsCustomer = { sessionViewModel.chooseRole(AccountRole.CUSTOMER) },
            onSignOut = performFullSignOut,
          )
        }

        is SessionDestination.CustomerReady -> {
          val customerViewModel: CustomerViewModel =
            viewModel(
              key = "customer_${user.uid}",
              factory =
                viewModelFactory {
                  initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    val db =
                      FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                    CustomerViewModel(MarketplaceRepository(db), user.uid, user.email ?: "")
                  }
                },
            )
          CustomerMainScreen(
            userProfile = dest.profile,
            viewModel = customerViewModel,
            onSwitchRole = sessionViewModel::openRoleSwitcher,
            onSignOut = performFullSignOut,
          )
        }

        is SessionDestination.VendorReady -> {
          val vendorViewModel: VendorViewModel =
            viewModel(
              key = "vendor_${user.uid}",
              factory =
                viewModelFactory {
                  initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    val db =
                      FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                    VendorViewModel(MarketplaceRepository(db), user.uid)
                  }
                },
            )
          VendorDashboardScreen(
            initialStore = dest.store,
            viewModel = vendorViewModel,
            onSwitchRole = sessionViewModel::openRoleSwitcher,
            onSignOut = performFullSignOut,
          )
        }

        is SessionDestination.AdminReady -> {
          val adminViewModel: AdminViewModel =
            viewModel(
              key = "admin_${user.uid}",
              factory =
                viewModelFactory {
                  initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    val db =
                      FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                    AdminViewModel(MarketplaceRepository(db))
                  }
                },
            )
          AdminDashboardScreen(
            viewModel = adminViewModel,
            onSwitchView = sessionViewModel::openRoleSwitcher,
            onSignOut = performFullSignOut,
          )
        }
      }
    }
  }
}
