package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.data.model.AccountRole
import com.example.data.model.ActivityType
import com.example.data.util.SecurityUtils
import com.example.ui.components.activityIcon
import kotlinx.coroutines.delay

@Composable
fun RoleSelectionScreen(
  isSignedIn: Boolean,
  signedInEmail: String?,
  onSelectCustomer: () -> Unit,
  onSelectVendor: () -> Unit,
  onSelectAdminDashboard: (() -> Unit)? = null,
  onBeforeUnauthSignIn: ((AccountRole?) -> Unit)? = null,
  onSignOut: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var isSigningIn by remember { mutableStateOf(false) }
  var authError by remember { mutableStateOf<String?>(null) }

  // Attempt silent auto sign-in after initial window frame renders to avoid SurfaceSyncGroup delay
  LaunchedEffect(isSignedIn) {
    if (!isSignedIn) {
      delay(250)
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = scope,
      )
    }
  }

  val launchInteractiveGoogleSignIn: (AccountRole?) -> Unit = { roleHint ->
    if (!isSigningIn) {
      authError = null
      onBeforeUnauthSignIn?.invoke(roleHint)
      isSigningIn = true
      onGoogleSignInClicked(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = { isSigningIn = false },
        onAuthError = { msg ->
          isSigningIn = false
          authError = msg
        },
        scope = scope,
        onAuthCancelled = { isSigningIn = false },
      )
    }
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding(),
  ) {
    // Full-bleed Hero Header
    Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
      Image(
        painter = painterResource(id = R.drawable.img_village_hero),
        contentDescription = stringResource(R.string.app_name),
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors =
                  listOf(
                    Color.Black.copy(alpha = 0.25f),
                    Color(0xFF042F21).copy(alpha = 0.88f),
                  )
              )
            )
      )
      Column(
        verticalArrangement = Arrangement.Bottom,
        modifier =
          Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.secondary,
          shape = RoundedCornerShape(50),
        ) {
          Text(
            text = "سوق القرية الرقمي الموحد",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.displaySmall,
          color = Color.White,
        )
        Text(
          text = stringResource(R.string.welcome_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = Color.White.copy(alpha = 0.9f),
        )
      }
    }

    Column(
      modifier =
        Modifier.widthIn(max = 560.dp)
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (isSignedIn && !signedInEmail.isNullOrBlank()) {
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "مسجل الدخول حالياً:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Text(
                text = signedInEmail,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            if (onSignOut != null) {
              TextButton(onClick = onSignOut, modifier = Modifier.testTag("role_screen_sign_out")) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Logout,
                  contentDescription = stringResource(R.string.sign_out),
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.sign_out))
              }
            }
          }
        }
      }

      if (!authError.isNullOrBlank()) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = authError!!,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      if (!isSignedIn) {
        // Primary interactive Google Sign-In button:
        // - Enters Admin Dashboard automatically if Google email == yahiakhamisasd@gmail.com
        // - Enters directly as Customer for all other Google accounts
        Button(
          onClick = { launchInteractiveGoogleSignIn(null) },
          enabled = !isSigningIn,
          shape = MaterialTheme.shapes.medium,
          modifier =
            Modifier.fillMaxWidth()
              .height(56.dp)
              .testTag("google_sign_in_button"),
        ) {
          if (isSigningIn) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.5.dp,
            )
          } else {
            Text(
              text = stringResource(R.string.sign_in_with_google),
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }
      }

      // Show Admin Dashboard return button ONLY if already signed in as yahiakhamisasd@gmail.com
      if (
        isSignedIn &&
          SecurityUtils.isAdminGoogleAccount(signedInEmail) &&
          onSelectAdminDashboard != null
      ) {
        Button(
          onClick = onSelectAdminDashboard,
          shape = MaterialTheme.shapes.medium,
          modifier =
            Modifier.fillMaxWidth()
              .height(54.dp)
              .testTag("role_admin_portal_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.AdminPanelSettings,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "الدخول إلى لوحة تحكم الإدارة والعمولة",
            style = MaterialTheme.typography.titleMedium,
          )
        }
      }

      Text(
        text = if (isSignedIn) "اختر الواجهة المطلوبة" else "أو اختر نوع حسابك للتسجيل بحساب Google",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
      )

      RoleOptionCard(
        title = stringResource(R.string.role_customer_title),
        description = stringResource(R.string.role_customer_desc),
        icon = Icons.Filled.ShoppingBag,
        accentColor = MaterialTheme.colorScheme.primary,
        badgeText = "تصفح وطلب فوري",
        enabled = !isSigningIn,
        onClick = {
          if (isSignedIn) {
            onSelectCustomer()
          } else {
            launchInteractiveGoogleSignIn(AccountRole.CUSTOMER)
          }
        },
        testTag = "role_customer_card",
      )

      RoleOptionCard(
        title = stringResource(R.string.role_vendor_title),
        description = stringResource(R.string.role_vendor_desc),
        icon = Icons.Filled.Storefront,
        accentColor = MaterialTheme.colorScheme.secondary,
        badgeText = "مطاعم • سوبر ماركت • مقاهي",
        enabled = !isSigningIn,
        onClick = {
          if (isSignedIn) {
            onSelectVendor()
          } else {
            launchInteractiveGoogleSignIn(AccountRole.VENDOR)
          }
        },
        testTag = "role_vendor_card",
      )
    }
  }
}

@Composable
private fun RoleOptionCard(
  title: String,
  description: String,
  icon: ImageVector,
  accentColor: Color,
  badgeText: String,
  enabled: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    shape = MaterialTheme.shapes.large,
    modifier =
      Modifier.fillMaxWidth()
        .testTag(testTag)
        .clickable(enabled = enabled, onClick = onClick),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(20.dp),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.size(60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(accentColor.copy(alpha = 0.14f)),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = accentColor,
          modifier = Modifier.size(32.dp),
        )
      }
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Surface(
          color = accentColor.copy(alpha = 0.12f),
          shape = RoundedCornerShape(50),
        ) {
          Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun VendorOnboardingScreen(
  email: String,
  initialOwnerName: String,
  isSaving: Boolean,
  errorMessage: String?,
  onSubmit: (String, String, ActivityType, String) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler { onBack() }

  var ownerName by rememberSaveable { mutableStateOf(initialOwnerName) }
  var storeName by rememberSaveable { mutableStateOf("") }
  var selectedActivityKey by rememberSaveable { mutableStateOf(ActivityType.RESTAURANT.key) }
  var phone by rememberSaveable { mutableStateOf("") }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      IconButton(onClick = onBack, modifier = Modifier.testTag("vendor_onboard_back")) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
      }
      Text(text = "تقديم طلب تسجيل محل تجاري", style = MaterialTheme.typography.titleLarge)
    }

    Spacer(modifier = Modifier.height(16.dp))

    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(20.dp),
      ) {
        Text(
          text = "بيانات صاحب المحل والنشاط التجاري لطلب التفعيل",
          style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
          value = ownerName,
          onValueChange = { ownerName = it },
          label = { Text("اسم صاحب المحل ثلاثي أو ثنائي") },
          leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("vendor_owner_name_input"),
        )

        OutlinedTextField(
          value = storeName,
          onValueChange = { storeName = it },
          label = { Text("اسم المحل / المطعم / السوبر ماركت") },
          leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("vendor_store_name_input"),
        )

        Text(text = "اختر نوع النشاط التجاري:", style = MaterialTheme.typography.labelLarge)

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          ActivityType.entries.forEach { type ->
            val isSelected = selectedActivityKey == type.key
            Surface(
              color =
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
              shape = MaterialTheme.shapes.medium,
              modifier =
                Modifier.weight(1f)
                  .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color =
                      if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = MaterialTheme.shapes.medium,
                  )
                  .testTag("vendor_activity_${type.key}")
                  .clickable { selectedActivityKey = type.key },
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
              ) {
                Icon(
                  imageVector = activityIcon(type.key),
                  contentDescription = type.arabicLabel,
                  tint =
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = type.arabicLabel,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
                if (isSelected) {
                  Spacer(modifier = Modifier.height(2.dp))
                  Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                  )
                }
              }
            }
          }
        }

        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text("رقم هاتف المحل لاستقبال الطلبات") },
          placeholder = { Text("010XXXXXXXX") },
          leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("vendor_phone_input"),
        )

        OutlinedTextField(
          value = email,
          onValueChange = {},
          readOnly = true,
          enabled = false,
          label = { Text("البريد الإلكتروني الموثق") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        if (!errorMessage.isNullOrBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = errorMessage,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(10.dp),
            )
          }
        }

        Button(
          onClick = {
            onSubmit(ownerName, storeName, ActivityType.fromKey(selectedActivityKey), phone)
          },
          enabled = !isSaving,
          colors =
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
          modifier =
            Modifier.fillMaxWidth()
              .height(52.dp)
              .testTag("vendor_save_profile_button"),
        ) {
          if (isSaving) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = Color.White,
              strokeWidth = 2.dp,
            )
          } else {
            Text("إرسال طلب تسجيل المحل للمراجعة والتفعيل")
          }
        }
      }
    }
  }
}
