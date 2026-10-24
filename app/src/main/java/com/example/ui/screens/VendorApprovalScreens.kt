package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.UiState
import com.example.data.model.VendorStore
import com.example.data.util.ImageAndFormatUtils
import com.example.ui.components.ActivityBadgeChip
import com.example.ui.components.EmptyStateBox

private const val ADMIN_ACTIVATION_PHONE = "01556841974"
private const val ADMIN_WHATSAPP_INTL = "201556841974"

@Composable
fun VendorPendingApprovalScreen(
  store: VendorStore,
  onBrowseAsCustomer: () -> Unit,
  onSignOut: () -> Unit,
) {
  BackHandler { onBrowseAsCustomer() }
  val context = LocalContext.current

  val openWhatsAppSupport = {
    val msg =
      "السلام عليكم، أرجو تفعيل حساب محلي في تطبيق عروض القرية:\n" +
        "اسم المحل: ${store.storeName}\n" +
        "اسم صاحب المحل: ${store.ownerName.ifBlank { store.storeName }}\n" +
        "رقم الهاتف: ${store.phone}"
    val uri = Uri.parse("https://wa.me/$ADMIN_WHATSAPP_INTL?text=${Uri.encode(msg)}")
    runCatching {
      context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
  }

  val dialAdminPhone = {
    val uri = Uri.parse("tel:$ADMIN_ACTIVATION_PHONE")
    runCatching {
      context.startActivity(Intent(Intent.ACTION_DIAL, uri))
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
    // Modern Layered Header Banner
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            Brush.verticalGradient(
              colors =
                listOf(
                  Color(0xFF064E3B),
                  Color(0xFF047857),
                )
            )
          )
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.size(76.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.16f)),
        ) {
          Icon(
            imageVector = Icons.Filled.HourglassTop,
            contentDescription = stringResource(R.string.vendor_pending_title),
            tint = Color(0xFFFCD34D),
            modifier = Modifier.size(40.dp),
          )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
          color = Color(0xFFF59E0B),
          shape = RoundedCornerShape(50),
        ) {
          Text(
            text = "حالة الحساب: بانتظار التفعيل اليدوي",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
          )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = stringResource(R.string.vendor_pending_title),
          style = MaterialTheme.typography.headlineSmall,
          color = Color.White,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
        )
      }
    }

    Column(
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier =
        Modifier.widthIn(max = 560.dp)
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
      // Required Official Message Card
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("vendor_pending_message_card"),
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.padding(22.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.VerifiedUser,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp),
          )
          Text(
            text = stringResource(R.string.vendor_pending_message),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
          )
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text =
                "بمجرد تأكيد الإدارة لبيانات محلك، ستُفتح لك لوحة تحكم المحل وإضافة العروض تلقائياً في نفس اللحظة دون الحاجة لإعادة التسجيل.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(12.dp),
            )
          }
        }
      }

      // Prominent Contact Action Buttons (WhatsApp + Phone Call on 01556841974)
      Button(
        onClick = { openWhatsAppSupport() },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
        shape = MaterialTheme.shapes.medium,
        modifier =
          Modifier.fillMaxWidth()
            .height(58.dp)
            .testTag("pending_vendor_whatsapp_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Chat,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = stringResource(R.string.contact_admin_whatsapp),
          style = MaterialTheme.typography.titleMedium,
          color = Color.White,
          fontWeight = FontWeight.Bold,
        )
      }

      Button(
        onClick = { dialAdminPhone() },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = MaterialTheme.shapes.medium,
        modifier =
          Modifier.fillMaxWidth()
            .height(56.dp)
            .testTag("pending_vendor_call_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.Phone,
          contentDescription = null,
          modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = stringResource(R.string.contact_admin_call),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }

      // Submitted Store Summary Card
      Card(
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
          ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(16.dp),
        ) {
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "ملخص طلب تسجيل المحل",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            ActivityBadgeChip(activityKey = store.activityType)
          }
          HorizontalDivider()
          Text(
            text = "اسم النشاط التجاري: ${store.storeName}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
          )
          if (store.ownerName.isNotBlank()) {
            Text(
              text = "اسم صاحب المحل: ${store.ownerName}",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          Text(
            text = "رقم الهاتف المسجل: ${store.phone}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Secondary Utility Actions
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        OutlinedButton(
          onClick = onBrowseAsCustomer,
          modifier =
            Modifier.weight(1f)
              .height(48.dp)
              .testTag("pending_browse_as_customer_btn"),
        ) {
          Icon(
            imageVector = Icons.Filled.ShoppingBag,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("تصفح كعميل")
        }

        OutlinedButton(
          onClick = onSignOut,
          modifier =
            Modifier.weight(1f)
              .height(48.dp)
              .testTag("pending_vendor_sign_out_btn"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.sign_out))
        }
      }
    }
  }
}

@Composable
fun AdminPendingBannerCard(
  pendingCount: Int,
  onOpenPendingTab: () -> Unit,
) {
  if (pendingCount <= 0) return

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    shape = MaterialTheme.shapes.medium,
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .clickable(onClick = onOpenPendingTab)
        .testTag("admin_pending_vendors_banner"),
  ) {
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Icon(
          imageVector = Icons.Filled.HourglassTop,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = "طلبات التفعيل المعلقة ($pendingCount)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
          Text(
            text = "اضغط هنا لمراجعة وتفعيل المحلات الجديدة في القرية",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }
      Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(50),
      ) {
        Text(
          text = "مراجعة",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
    }
  }
}

@Composable
fun AdminPendingVendorsTab(
  pendingVendorsState: UiState<List<VendorStore>>,
  isBusy: Boolean,
  onApproveVendor: (VendorStore) -> Unit,
  onRejectVendor: (VendorStore) -> Unit,
) {
  val context = LocalContext.current
  var vendorToConfirmReject by remember { mutableStateOf<VendorStore?>(null) }

  Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
    Surface(
      color = MaterialTheme.colorScheme.primaryContainer,
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(14.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Storefront,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = stringResource(R.string.pending_vendor_requests_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
            text =
              "راجع بيانات صاحب المحل وتواصل معه للتأكيد، ثم اضغط (تفعيل الحساب) لفتح كافة صلاحيات المحل فوراً.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
    }

    when (pendingVendorsState) {
      is UiState.Loading -> {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
          CircularProgressIndicator()
        }
      }
      is UiState.Error -> {
        EmptyStateBox(
          icon = Icons.Filled.HourglassTop,
          title = "تعذر تحميل طلبات التفعيل",
          subtitle = pendingVendorsState.message,
        )
      }
      is UiState.Success -> {
        val pendingList = pendingVendorsState.data
        if (pendingList.isEmpty()) {
          EmptyStateBox(
            icon = Icons.Filled.CheckCircle,
            title = "لا توجد طلبات تفعيل معلقة حالياً",
            subtitle = "تمت مراجعة جميع المحلات المسجلة. أي طلب تسجيل محل جديد سيظهر هنا فوراً.",
          )
        } else {
          LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(pendingList, key = { "pending_vendor_${it.vendorId}" }) { vendor ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = MaterialTheme.shapes.large,
                modifier =
                  Modifier.fillMaxWidth()
                    .testTag("pending_vendor_card_${vendor.vendorId}"),
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.padding(16.dp),
                ) {
                  // Business Name + Activity Type
                  Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = vendor.storeName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                      )
                      Text(
                        text = vendor.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    ActivityBadgeChip(activityKey = vendor.activityType)
                  }

                  HorizontalDivider()

                  // Owner Name
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Filled.Person,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "صاحب المحل: ${vendor.ownerName.ifBlank { vendor.storeName }}",
                      style = MaterialTheme.typography.bodyLarge,
                      fontWeight = FontWeight.Medium,
                    )
                  }

                  // Phone Number + Quick Dial
                  Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = "رقم الهاتف: ${vendor.phone}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                    IconButton(
                      onClick = {
                        runCatching {
                          context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${vendor.phone}"))
                          )
                        }
                      },
                      modifier = Modifier.testTag("call_pending_vendor_${vendor.vendorId}"),
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = "اتصال بصاحب المحل",
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }

                  // Submission Date
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Filled.CalendarToday,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "تاريخ الطلب: ${ImageAndFormatUtils.formatTimestamp(vendor.createdAt)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }

                  Spacer(modifier = Modifier.height(4.dp))

                  // Two Clear Action Buttons: "تفعيل الحساب" & "رفض الطلب"
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Button(
                      onClick = { onApproveVendor(vendor) },
                      enabled = !isBusy,
                      colors =
                        ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.primary
                        ),
                      modifier =
                        Modifier.weight(1f)
                          .height(48.dp)
                          .testTag("approve_vendor_btn_${vendor.vendorId}"),
                    ) {
                      Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = stringResource(R.string.approve_vendor_account),
                        fontWeight = FontWeight.Bold,
                      )
                    }

                    OutlinedButton(
                      onClick = { vendorToConfirmReject = vendor },
                      enabled = !isBusy,
                      colors =
                        ButtonDefaults.outlinedButtonColors(
                          contentColor = MaterialTheme.colorScheme.error
                        ),
                      modifier =
                        Modifier.weight(1f)
                          .height(48.dp)
                          .testTag("reject_vendor_btn_${vendor.vendorId}"),
                    ) {
                      Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = stringResource(R.string.reject_vendor_request),
                        fontWeight = FontWeight.Bold,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  vendorToConfirmReject?.let { target ->
    AlertDialog(
      onDismissRequest = { vendorToConfirmReject = null },
      title = { Text("رفض طلب تفعيل (${target.storeName})؟") },
      text = {
        Text(
          text =
            "سيؤدي رفض الطلب إلى حذف طلب تسجيل المحل الخاص بـ (${target.ownerName.ifBlank { target.storeName }}) وإشعاره بالرفض.",
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onRejectVendor(target)
            vendorToConfirmReject = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("confirm_reject_vendor_btn"),
        ) {
          Text("تأكيد رفض الطلب")
        }
      },
      dismissButton = {
        TextButton(onClick = { vendorToConfirmReject = null }) { Text("تراجع") }
      },
    )
  }
}
