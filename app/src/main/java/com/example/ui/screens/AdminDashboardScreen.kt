package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.CommissionSettlement
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.PlatformConfig
import com.example.data.model.UiState
import com.example.data.model.VendorFinancialSummary
import com.example.data.model.VendorStore
import com.example.data.util.ImageAndFormatUtils
import com.example.data.util.LocalNotificationHelper
import com.example.ui.components.ActivityBadgeChip
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.MetricStatCard
import com.example.ui.components.NotificationItemCard
import com.example.ui.components.OrderStatusPill
import com.example.ui.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
  viewModel: AdminViewModel,
  onSwitchView: () -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
  val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
  val pendingVendorsState by viewModel.pendingVendorsState.collectAsStateWithLifecycle()
  val financialState by viewModel.financialSummariesState.collectAsStateWithLifecycle()
  val config by viewModel.configState.collectAsStateWithLifecycle()
  val settlements by viewModel.settlementsState.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  var vendorForCashCollection by remember { mutableStateOf<VendorStore?>(null) }
  var vendorForSuspension by remember { mutableStateOf<VendorStore?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }

  val notifPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  LaunchedEffect(notificationsState) {
    val list = (notificationsState as? UiState.Success)?.data.orEmpty()
    if (list.isNotEmpty()) {
      LocalNotificationHelper.notifyIfNew(context, list)
    }
  }

  LaunchedEffect(feedback) {
    feedback?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearFeedback()
    }
  }

  BackHandler(enabled = selectedTab != 0) { selectedTab = 0 }

  val unreadAdminNotifs =
    (notificationsState as? UiState.Success)?.data?.count { !it.isRead } ?: 0
  val pendingVendorsCount =
    (pendingVendorsState as? UiState.Success)?.data?.size ?: 0
  val suspiciousStoresCount =
    (financialState as? UiState.Success)?.data?.count { it.isExceedingDailyCancelLimit } ?: 0

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isExpanded = maxWidth >= 680.dp

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.AdminPanelSettings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text("لوحة تحكم الإدارة والعمولة", style = MaterialTheme.typography.titleLarge)
                Text(
                  text = "مسؤول المنصة: yahiakhamisasd@gmail.com",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          },
          actions = {
            IconButton(
              onClick = onSwitchView,
              modifier = Modifier.testTag("admin_switch_view_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = "معاينة واجهة العميل أو المحل",
              )
            }
            IconButton(
              onClick = onSignOut,
              modifier = Modifier.testTag("admin_sign_out_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = stringResource(R.string.sign_out),
              )
            }
          },
          colors =
            TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
      },
      bottomBar = {
        if (!isExpanded) {
          NavigationBar {
            NavigationBarItem(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              icon = {
                BadgedBox(
                  badge = {
                    if (unreadAdminNotifs > 0) {
                      Badge { Text("$unreadAdminNotifs") }
                    }
                  }
                ) {
                  Icon(Icons.Filled.ReceiptLong, contentDescription = "الطلبات الحية")
                }
              },
              label = { Text("الطلبات") },
              modifier = Modifier.testTag("admin_tab_live_orders"),
            )
            NavigationBarItem(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              icon = {
                BadgedBox(
                  badge = {
                    if (pendingVendorsCount > 0) {
                      Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                        Text("$pendingVendorsCount")
                      }
                    }
                  }
                ) {
                  Icon(Icons.Filled.HourglassTop, contentDescription = "طلبات التفعيل المعلقة")
                }
              },
              label = { Text("التفعيل") },
              modifier = Modifier.testTag("admin_tab_pending_vendors"),
            )
            NavigationBarItem(
              selected = selectedTab == 2,
              onClick = { selectedTab = 2 },
              icon = {
                BadgedBox(
                  badge = {
                    if (suspiciousStoresCount > 0) {
                      Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("$suspiciousStoresCount")
                      }
                    }
                  }
                ) {
                  Icon(Icons.Filled.GppMaybe, contentDescription = "رقابة الإلغاء")
                }
              },
              label = { Text("الإلغاءات") },
              modifier = Modifier.testTag("admin_tab_cancellations"),
            )
            NavigationBarItem(
              selected = selectedTab == 3,
              onClick = { selectedTab = 3 },
              icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "عمولة 10%") },
              label = { Text("عمولة 10%") },
              modifier = Modifier.testTag("admin_tab_commissions"),
            )
            NavigationBarItem(
              selected = selectedTab == 4,
              onClick = { selectedTab = 4 },
              icon = { Icon(Icons.Filled.Settings, contentDescription = "الإعدادات") },
              label = { Text("الإعدادات") },
              modifier = Modifier.testTag("admin_tab_settings"),
            )
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (isExpanded) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            NavigationRailItem(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              icon = { Icon(Icons.Filled.ReceiptLong, contentDescription = null) },
              label = { Text("الطلبات") },
            )
            NavigationRailItem(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              icon = {
                BadgedBox(
                  badge = {
                    if (pendingVendorsCount > 0) {
                      Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                        Text("$pendingVendorsCount")
                      }
                    }
                  }
                ) {
                  Icon(Icons.Filled.HourglassTop, contentDescription = null)
                }
              },
              label = { Text("التفعيل") },
            )
            NavigationRailItem(
              selected = selectedTab == 2,
              onClick = { selectedTab = 2 },
              icon = { Icon(Icons.Filled.GppMaybe, contentDescription = null) },
              label = { Text("الإلغاءات") },
            )
            NavigationRailItem(
              selected = selectedTab == 3,
              onClick = { selectedTab = 3 },
              icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null) },
              label = { Text("عمولة 10%") },
            )
            NavigationRailItem(
              selected = selectedTab == 4,
              onClick = { selectedTab = 4 },
              icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
              label = { Text("الإعدادات") },
            )
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
          when (selectedTab) {
            0 ->
              AdminLiveOrdersAndAlertsTab(
                ordersState = ordersState,
                notificationsState = notificationsState,
                pendingCount = pendingVendorsCount,
                onOpenPendingTab = { selectedTab = 1 },
                onMarkRead = viewModel::markNotificationRead,
              )
            1 ->
              AdminPendingVendorsTab(
                pendingVendorsState = pendingVendorsState,
                isBusy = isBusy,
                onApproveVendor = viewModel::approveVendor,
                onRejectVendor = viewModel::rejectVendor,
              )
            2 ->
              AdminCancellationMonitorTab(
                financialState = financialState,
                ordersState = ordersState,
                dailyThreshold = config.dailyCancelAlertLimit.toInt(),
                isBusy = isBusy,
                onSuspendClick = { vendor ->
                  if (vendor.isSuspended) {
                    viewModel.toggleSuspension(vendor, suspend = false, reason = "")
                  } else {
                    vendorForSuspension = vendor
                  }
                },
              )
            3 ->
              AdminCommissionLedgerTab(
                financialState = financialState,
                settlements = settlements,
                onRecordCashClick = { vendorForCashCollection = it },
              )
            4 ->
              AdminSettingsAndVendorsTab(
                config = config,
                financialState = financialState,
                isBusy = isBusy,
                onUpdateThreshold = viewModel::updateDailyCancelLimit,
                onToggleSuspend = { vendor ->
                  if (vendor.isSuspended) {
                    viewModel.toggleSuspension(vendor, suspend = false, reason = "")
                  } else {
                    vendorForSuspension = vendor
                  }
                },
              )
          }
        }
      }
    }
  }

  vendorForCashCollection?.let { store ->
    RecordCashCollectionDialog(
      vendor = store,
      isBusy = isBusy,
      onDismiss = { vendorForCashCollection = null },
      onConfirm = { amountText, note ->
        viewModel.recordCashCollection(store, amountText, note) {
          vendorForCashCollection = null
        }
      },
    )
  }

  vendorForSuspension?.let { store ->
    SuspendVendorDialog(
      vendor = store,
      onDismiss = { vendorForSuspension = null },
      onConfirmSuspend = { reason ->
        viewModel.toggleSuspension(store, suspend = true, reason = reason)
        vendorForSuspension = null
      },
    )
  }
}

@Composable
private fun AdminLiveOrdersAndAlertsTab(
  ordersState: UiState<List<Order>>,
  notificationsState: UiState<List<AppNotification>>,
  pendingCount: Int,
  onOpenPendingTab: () -> Unit,
  onMarkRead: (String) -> Unit,
) {
  var subView by rememberSaveable { mutableIntStateOf(0) }
  var statusFilter by rememberSaveable { mutableStateOf("ALL") }

  Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
    AdminPendingBannerCard(pendingCount = pendingCount, onOpenPendingTab = onOpenPendingTab)

    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
      FilterChip(
        selected = subView == 0,
        onClick = { subView = 0 },
        label = { Text("جدول الطلبات الحية لحظة بلحظة") },
        leadingIcon = { Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp)) },
      )
      FilterChip(
        selected = subView == 1,
        onClick = { subView = 1 },
        label = { Text("نسخة الإشعارات الفورية") },
        leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp)) },
      )
    }

    if (subView == 0) {
      val filters =
        listOf(
          "ALL" to "الكل",
          OrderStatus.PENDING.key to "قيد الانتظار",
          OrderStatus.ACCEPTED.key to "مقبول",
          OrderStatus.DELIVERED.key to "تم التوصيل",
          OrderStatus.CANCELLED.key to "ملغي",
        )
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(filters) { (key, label) ->
          FilterChip(
            selected = statusFilter == key,
            onClick = { statusFilter = key },
            label = { Text(label) },
          )
        }
      }

      when (ordersState) {
        is UiState.Loading -> {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator()
          }
        }
        is UiState.Error -> {
          EmptyStateBox(
            icon = Icons.Filled.ReceiptLong,
            title = "خطأ في تحميل الطلبات",
            subtitle = ordersState.message,
          )
        }
        is UiState.Success -> {
          val filtered =
            ordersState.data.filter { statusFilter == "ALL" || it.status == statusFilter }
          if (filtered.isEmpty()) {
            EmptyStateBox(
              icon = Icons.Filled.ReceiptLong,
              title = "لا توجد طلبات مطابقة",
              subtitle = "ستظهر هنا جميع طلبات محلات القرية في نفس لحظة إرسالها من الزبائن.",
            )
          } else {
            LazyColumn(
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxSize(),
            ) {
              items(filtered, key = { it.id }) { order ->
                Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text(
                        text = "${order.storeName} • ${order.offerTitle} (×${order.quantity})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                      )
                      OrderStatusPill(statusKey = order.status)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = "العميل: ${order.customerName} | هاتف: ${order.customerPhone}",
                      style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                      text = "العنوان: ${order.deliveryAddress}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (order.status == OrderStatus.CANCELLED.key) {
                      Text(
                        text = "سبب الإلغاء: ${order.cancellationReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                      horizontalArrangement = Arrangement.SpaceBetween,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text(
                        text = "المبلغ: ${ImageAndFormatUtils.formatEgp(order.totalPrice)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                      )
                      Text(
                        text = "عمولتك (10%): ${ImageAndFormatUtils.formatEgp(order.commissionAmount)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    } else {
      when (notificationsState) {
        is UiState.Loading -> {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator()
          }
        }
        is UiState.Error -> {
          EmptyStateBox(
            icon = Icons.Filled.NotificationsActive,
            title = "تعذر تحميل الإشعارات",
            subtitle = notificationsState.message,
          )
        }
        is UiState.Success -> {
          if (notificationsState.data.isEmpty()) {
            EmptyStateBox(
              icon = Icons.Filled.NotificationsActive,
              title = "لا توجد إشعارات حية بعد",
              subtitle = "كل طلب جديد أو تنبيه إلغاء يصل هنا بنسخة فورية مطابقة.",
            )
          } else {
            LazyColumn(
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxSize(),
            ) {
              items(notificationsState.data, key = { it.id }) { notif ->
                NotificationItemCard(notification = notif, onMarkRead = onMarkRead)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AdminCancellationMonitorTab(
  financialState: UiState<List<VendorFinancialSummary>>,
  ordersState: UiState<List<Order>>,
  dailyThreshold: Int,
  isBusy: Boolean,
  onSuspendClick: (VendorStore) -> Unit,
) {
  val summaries = (financialState as? UiState.Success)?.data.orEmpty()
  val cancelledOrders =
    (ordersState as? UiState.Success)
      ?.data
      .orEmpty()
      .filter { it.status == OrderStatus.CANCELLED.key }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
  ) {
    item {
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text =
            "رادار مكافحة الهروب من العمولة: ينبهك النظام تلقائياً إذا بلغ المحل ($dailyThreshold إلغاءات في اليوم الواحد) لاحتمال إلغاء الطلب بالتطبيق وتوصيله خارجياً.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.padding(12.dp),
        )
      }
    }

    val flaggedStores = summaries.filter { it.todayCancelledCount > 0 || it.totalCancelledCount > 0 }
    if (flaggedStores.isNotEmpty()) {
      item {
        Text(
          text = "مؤشر إلغاءات المحلات والتحكم بالحساب",
          style = MaterialTheme.typography.titleMedium,
        )
      }
      items(flaggedStores, key = { "cancel_summary_${it.vendor.vendorId}" }) { item ->
        val container =
          if (item.isExceedingDailyCancelLimit) MaterialTheme.colorScheme.errorContainer
          else MaterialTheme.colorScheme.surface
        Card(
          colors = CardDefaults.cardColors(containerColor = container),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  if (item.isExceedingDailyCancelLimit) {
                    Icon(
                      imageVector = Icons.Filled.WarningAmber,
                      contentDescription = "تحذير",
                      tint = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                  }
                  Text(
                    text = item.vendor.storeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                }
                Text(
                  text =
                    "إلغاءات اليوم: ${item.todayCancelledCount} | إجمالي الإلغاءات: ${item.totalCancelledCount}",
                  style = MaterialTheme.typography.bodyMedium,
                  color =
                    if (item.isExceedingDailyCancelLimit) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  fontWeight = FontWeight.Bold,
                )
              }

              Button(
                onClick = { onSuspendClick(item.vendor) },
                enabled = !isBusy,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor =
                      if (item.vendor.isSuspended) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.error
                  ),
                modifier = Modifier.testTag("suspend_vendor_btn_${item.vendor.vendorId}"),
              ) {
                Icon(
                  imageVector =
                    if (item.vendor.isSuspended) Icons.Filled.CheckCircle else Icons.Filled.Block,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  if (item.vendor.isSuspended) stringResource(R.string.unsuspend_vendor)
                  else stringResource(R.string.suspend_vendor)
                )
              }
            }
          }
        }
      }
    }

    item {
      Text(
        text = "سجل تتبع أسباب الإلغاء التفصيلي (${cancelledOrders.size})",
        style = MaterialTheme.typography.titleMedium,
      )
    }

    if (cancelledOrders.isEmpty()) {
      item {
        EmptyStateBox(
          icon = Icons.Filled.CheckCircle,
          title = "سجل الإلغاءات نظيف تماماً",
          subtitle = "لم يتم إلغاء أي طلب من قبل محلات القرية حتى الآن.",
        )
      }
    } else {
      items(cancelledOrders, key = { "cancelled_order_${it.id}" }) { order ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "المحل: ${order.storeName}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
              )
              Text(
                text = ImageAndFormatUtils.formatTimestamp(order.createdAt),
                style = MaterialTheme.typography.labelSmall,
              )
            }
            Text(
              text = "العرض: ${order.offerTitle} (${ImageAndFormatUtils.formatEgp(order.totalPrice)})",
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              text = "بيانات الزبون للمراجعة: ${order.customerName} - ${order.customerPhone}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "السبب المسجل: ${order.cancellationReason}",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AdminCommissionLedgerTab(
  financialState: UiState<List<VendorFinancialSummary>>,
  settlements: List<CommissionSettlement>,
  onRecordCashClick: (VendorStore) -> Unit,
) {
  when (financialState) {
    is UiState.Loading -> {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator()
      }
    }
    is UiState.Error -> {
      EmptyStateBox(
        icon = Icons.Filled.AccountBalanceWallet,
        title = "تعذر حساب الجدول المالي",
        subtitle = financialState.message,
      )
    }
    is UiState.Success -> {
      val list = financialState.data
      val grandSales = list.sumOf { it.totalCompletedSales }
      val grandCommission = list.sumOf { it.totalCommissionEarned }
      val grandRemaining = list.sumOf { it.netCommissionDue }

      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
      ) {
        item {
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            MetricStatCard(
              title = "إجمالي المبيعات المكتملة",
              value = ImageAndFormatUtils.formatEgp(grandSales),
              subtitle = "جميع المحلات",
              icon = Icons.Filled.Payments,
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.weight(1f),
            )
            MetricStatCard(
              title = "صافي عمولة 10% المتبقية",
              value = ImageAndFormatUtils.formatEgp(grandRemaining),
              subtitle = "من إجمالي ${ImageAndFormatUtils.formatEgp(grandCommission)}",
              icon = Icons.Filled.AccountBalanceWallet,
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.weight(1f),
            )
          }
        }

        item {
          Text(
            text = "جدول مبيعات المحلات وعمولة الـ (10%) المستحقة كاش",
            style = MaterialTheme.typography.titleMedium,
          )
        }

        if (list.isEmpty()) {
          item {
            EmptyStateBox(
              icon = Icons.Filled.AccountBalanceWallet,
              title = "لا توجد محلات مسجلة بعد",
              subtitle = "سيظهر هنا كشف الحساب التفصيلي وعمولة 10% لكل محل مسجل بالقرية.",
            )
          }
        } else {
          items(list, key = { "fin_${it.vendor.vendorId}" }) { summary ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
              modifier = Modifier.fillMaxWidth().testTag("commission_card_${summary.vendor.vendorId}"),
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = summary.vendor.storeName,
                      style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                      text = "هاتف المحل: ${summary.vendor.phone} • طلبات مكتملة: ${summary.completedOrdersCount}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  ActivityBadgeChip(activityKey = summary.vendor.activityType)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Row(
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column {
                    Text("إجمالي المبيعات", style = MaterialTheme.typography.labelSmall)
                    Text(
                      text = ImageAndFormatUtils.formatEgp(summary.totalCompletedSales),
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                  Column {
                    Text("إجمالي عمولة 10%", style = MaterialTheme.typography.labelSmall)
                    Text(
                      text = ImageAndFormatUtils.formatEgp(summary.totalCommissionEarned),
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                  Column {
                    Text("المستحق كاش الآن", style = MaterialTheme.typography.labelSmall)
                    Text(
                      text = ImageAndFormatUtils.formatEgp(summary.netCommissionDue),
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.secondary,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = "تم تحصيله سابقاً: ${ImageAndFormatUtils.formatEgp(summary.totalCashCollected)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  OutlinedButton(
                    onClick = { onRecordCashClick(summary.vendor) },
                    modifier = Modifier.testTag("record_cash_btn_${summary.vendor.vendorId}"),
                  ) {
                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.record_cash_collection))
                  }
                }
              }
            }
          }
        }

        if (settlements.isNotEmpty()) {
          item {
            Text(
              text = "أحدث عمليات التحصيل النقدي المسجلة",
              style = MaterialTheme.typography.titleMedium,
            )
          }
          items(settlements.take(10), key = { "settle_${it.id}" }) { s ->
            Surface(
              color = MaterialTheme.colorScheme.surface,
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(s.storeName, style = MaterialTheme.typography.titleSmall)
                  if (s.note.isNotBlank()) {
                    Text(
                      s.note,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                Text(
                  text = "+ ${ImageAndFormatUtils.formatEgp(s.amountCollected)}",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.primary,
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

@Composable
private fun AdminSettingsAndVendorsTab(
  config: PlatformConfig,
  financialState: UiState<List<VendorFinancialSummary>>,
  isBusy: Boolean,
  onUpdateThreshold: (Int) -> Unit,
  onToggleSuspend: (VendorStore) -> Unit,
) {
  var limitDraft by remember(config.dailyCancelAlertLimit) {
    mutableIntStateOf(config.dailyCancelAlertLimit.toInt())
  }
  val summaries = (financialState as? UiState.Success)?.data.orEmpty()

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
  ) {
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.padding(16.dp),
        ) {
          Text(
            text = "إعدادات التنبيه الفوري للإلغاءات اليومية",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text =
              "حدد عدد الإلغاءات اليومية للمحل الواحد الذي يُطلق عنده إشعار تحذيري فوري للإدارة:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = { if (limitDraft > 1) limitDraft -= 1 },
                enabled = !isBusy && limitDraft > 1,
              ) {
                Icon(Icons.Filled.Remove, contentDescription = "تقليل الحد")
              }
              Text(
                text = "$limitDraft إلغاءات / يوم",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp),
              )
              IconButton(
                onClick = { if (limitDraft < 25) limitDraft += 1 },
                enabled = !isBusy && limitDraft < 25,
              ) {
                Icon(Icons.Filled.Add, contentDescription = "زيادة الحد")
              }
            }

            Button(
              onClick = { onUpdateThreshold(limitDraft) },
              enabled = !isBusy && limitDraft != config.dailyCancelAlertLimit.toInt(),
              modifier = Modifier.testTag("save_cancel_threshold_btn"),
            ) {
              Text("حفظ الحد")
            }
          }
        }
      }
    }

    item {
      Text(
        text = "دليل جميع محلات القرية المسجلة (${summaries.size})",
        style = MaterialTheme.typography.titleMedium,
      )
    }

    items(summaries, key = { "dir_${it.vendor.vendorId}" }) { item ->
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(14.dp),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(item.vendor.storeName, style = MaterialTheme.typography.titleMedium)
              Spacer(modifier = Modifier.width(8.dp))
              if (item.vendor.isSuspended) {
                Surface(
                  color = MaterialTheme.colorScheme.errorContainer,
                  shape = RoundedCornerShape(50),
                ) {
                  Text(
                    text = "معلق",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  )
                }
              }
            }
            Text(
              text = "${item.vendor.phone} • ${item.vendor.email}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          OutlinedButton(
            onClick = { onToggleSuspend(item.vendor) },
            enabled = !isBusy,
            colors =
              ButtonDefaults.outlinedButtonColors(
                contentColor =
                  if (item.vendor.isSuspended) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.error
              ),
          ) {
            Text(if (item.vendor.isSuspended) "تفعيل" else "تعليق")
          }
        }
      }
    }
  }
}

@Composable
private fun RecordCashCollectionDialog(
  vendor: VendorStore,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (String, String) -> Unit,
) {
  var amount by rememberSaveable { mutableStateOf("") }
  var note by rememberSaveable { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("تسجيل تحصيل عمولة كاش") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "المحل: ${vendor.storeName}",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
          value = amount,
          onValueChange = { amount = it },
          label = { Text("المبلغ المحصل نقداً (ج.م)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("cash_amount_input"),
        )
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text("ملاحظة التحصيل (اختياري)") },
          placeholder = { Text("مثال: تحصيل أسبوعي باليد") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(amount, note) },
        enabled = !isBusy && (amount.toDoubleOrNull() ?: 0.0) > 0.0,
        modifier = Modifier.testTag("confirm_cash_collection_btn"),
      ) {
        Text("حفظ التحصيل")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
  )
}

@Composable
private fun SuspendVendorDialog(
  vendor: VendorStore,
  onDismiss: () -> Unit,
  onConfirmSuspend: (String) -> Unit,
) {
  var reason by rememberSaveable {
    mutableStateOf("تجاوز الحد المسموح من الإلغاءات اليومية واشتباه تهرب من العمولة")
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("تعليق حساب (${vendor.storeName})") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text =
            "سيؤدي تعليق الحساب إلى إخفاء كافة عروض هذا المحل فوراً عن شاشة الزبائن ومنعه من إضافة عروض جديدة.",
          style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          label = { Text("سبب التعليق الإداري") },
          modifier = Modifier.fillMaxWidth().testTag("suspend_reason_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmSuspend(reason) },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.testTag("confirm_suspend_vendor_btn"),
      ) {
        Text("تعليق الحساب فوراً")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("تراجع") } },
  )
}
