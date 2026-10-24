package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ActivityType
import com.example.data.model.AppNotification
import com.example.data.model.Offer
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.UiState
import com.example.data.model.VendorStore
import com.example.data.util.ImageAndFormatUtils
import com.example.data.util.LocalNotificationHelper
import com.example.ui.components.ActivityBadgeChip
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.NotificationItemCard
import com.example.ui.components.OfferImageBanner
import com.example.ui.components.OrderStatusPill
import com.example.ui.viewmodel.VendorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorDashboardScreen(
  initialStore: VendorStore,
  viewModel: VendorViewModel,
  onSwitchRole: () -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val storeState by viewModel.storeState.collectAsStateWithLifecycle()
  val offersState by viewModel.offersState.collectAsStateWithLifecycle()
  val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
  val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

  val liveStore = (storeState as? UiState.Success)?.data ?: initialStore
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  var showAddOfferSheet by rememberSaveable { mutableStateOf(false) }
  var orderToCancel by remember { mutableStateOf<Order?>(null) }
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

  val pendingOrdersCount =
    (ordersState as? UiState.Success)?.data?.count { it.status == OrderStatus.PENDING.key } ?: 0
  val unreadNotifsCount =
    (notificationsState as? UiState.Success)?.data?.count { !it.isRead } ?: 0

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isExpanded = maxWidth >= 640.dp

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
          title = {
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(liveStore.storeName, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(8.dp))
                ActivityBadgeChip(activityKey = liveStore.activityType)
              }
              Text(
                text = "لوحة تحكم المحل التجاري",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
          actions = {
            IconButton(onClick = onSwitchRole, modifier = Modifier.testTag("vendor_switch_role")) {
              Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.switch_role))
            }
            IconButton(onClick = onSignOut, modifier = Modifier.testTag("vendor_sign_out")) {
              Icon(
                Icons.AutoMirrored.Filled.Logout,
                contentDescription = stringResource(R.string.sign_out),
              )
            }
          },
          colors =
            TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
      },
      floatingActionButton = {
        if (selectedTab == 1 && !liveStore.isSuspended) {
          ExtendedFloatingActionButton(
            onClick = { showAddOfferSheet = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.add_new_offer)) },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = Color.White,
            modifier = Modifier.testTag("add_offer_fab"),
          )
        }
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
                    if (pendingOrdersCount > 0) {
                      Badge { Text("$pendingOrdersCount") }
                    }
                  }
                ) {
                  Icon(Icons.Filled.ReceiptLong, contentDescription = "الطلبات الحية")
                }
              },
              label = { Text("الطلبات الحية") },
              modifier = Modifier.testTag("vendor_tab_orders"),
            )
            NavigationBarItem(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              icon = { Icon(Icons.Filled.Inventory2, contentDescription = "عروضي ومخزوني") },
              label = { Text("عروضي") },
              modifier = Modifier.testTag("vendor_tab_offers"),
            )
            NavigationBarItem(
              selected = selectedTab == 2,
              onClick = { selectedTab = 2 },
              icon = {
                BadgedBox(
                  badge = {
                    if (unreadNotifsCount > 0) {
                      Badge { Text("$unreadNotifsCount") }
                    }
                  }
                ) {
                  Icon(Icons.Filled.Notifications, contentDescription = "الإشعارات")
                }
              },
              label = { Text("الإشعارات") },
              modifier = Modifier.testTag("vendor_tab_notifications"),
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
              icon = {
                BadgedBox(
                  badge = {
                    if (pendingOrdersCount > 0) {
                      Badge { Text("$pendingOrdersCount") }
                    }
                  }
                ) {
                  Icon(Icons.Filled.ReceiptLong, contentDescription = "الطلبات")
                }
              },
              label = { Text("الطلبات") },
            )
            NavigationRailItem(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              icon = { Icon(Icons.Filled.Inventory2, contentDescription = "عروضي") },
              label = { Text("عروضي") },
            )
            NavigationRailItem(
              selected = selectedTab == 2,
              onClick = { selectedTab = 2 },
              icon = {
                BadgedBox(
                  badge = {
                    if (unreadNotifsCount > 0) {
                      Badge { Text("$unreadNotifsCount") }
                    }
                  }
                ) {
                  Icon(Icons.Filled.Notifications, contentDescription = "الإشعارات")
                }
              },
              label = { Text("الإشعارات") },
            )
          }
        }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
          if (liveStore.isSuspended) {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Block,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = "تم تعليق حساب المحل مؤقتاً من قبل الإدارة",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text =
                      liveStore.suspensionReason.ifBlank {
                        "يرجى التواصل مع إدارة التطبيق لمراجعة سجل الإلغاءات أو تسوية العمولة."
                      },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                  )
                }
              }
            }
          }

          when (selectedTab) {
            0 ->
              VendorLiveOrdersTab(
                ordersState = ordersState,
                isBusy = isBusy,
                onAcceptOrder = { viewModel.handleOrderAction(it, OrderStatus.ACCEPTED) },
                onMarkDelivered = { viewModel.handleOrderAction(it, OrderStatus.DELIVERED) },
                onRequestCancel = { orderToCancel = it },
              )
            1 ->
              VendorOffersInventoryTab(
                offersState = offersState,
                isBusy = isBusy,
                onAdjustStock = { offer, newStock ->
                  viewModel.updateOfferStockOrToggle(offer, newStock, offer.isActive)
                },
                onToggleActive = { offer ->
                  viewModel.updateOfferStockOrToggle(offer, offer.stockQuantity, !offer.isActive)
                },
                onDeleteOffer = { viewModel.deleteOffer(it.id) },
              )
            2 ->
              VendorNotificationsTab(
                notificationsState = notificationsState,
                onMarkRead = viewModel::markNotificationRead,
              )
          }
        }
      }
    }
  }

  if (showAddOfferSheet) {
    AddVendorOfferBottomSheet(
      vendorStore = liveStore,
      isBusy = isBusy,
      onDismiss = { showAddOfferSheet = false },
      onPublish = { title, desc, origPrice, offerPrice, stock, imgBase64, defaultKey ->
        viewModel.publishNewOffer(
          vendorStore = liveStore,
          title = title,
          description = desc,
          originalPriceText = origPrice,
          offerPriceText = offerPrice,
          stockText = stock,
          imageBase64 = imgBase64,
          defaultImageKey = defaultKey,
          onSuccess = { showAddOfferSheet = false },
        )
      },
    )
  }

  orderToCancel?.let { targetOrder ->
    CancelOrderReasonDialog(
      order = targetOrder,
      onDismiss = { orderToCancel = null },
      onConfirmCancel = { reason ->
        viewModel.handleOrderAction(targetOrder, OrderStatus.CANCELLED, reason)
        orderToCancel = null
      },
    )
  }
}

@Composable
private fun VendorLiveOrdersTab(
  ordersState: UiState<List<Order>>,
  isBusy: Boolean,
  onAcceptOrder: (Order) -> Unit,
  onMarkDelivered: (Order) -> Unit,
  onRequestCancel: (Order) -> Unit,
) {
  when (ordersState) {
    is UiState.Loading -> {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator()
      }
    }
    is UiState.Error -> {
      EmptyStateBox(
        icon = Icons.Filled.ReceiptLong,
        title = "تعذر تحميل الطلبات",
        subtitle = ordersState.message,
      )
    }
    is UiState.Success -> {
      if (ordersState.data.isEmpty()) {
        EmptyStateBox(
          icon = Icons.Filled.ReceiptLong,
          title = "لا توجد طلبات واردة بعد",
          subtitle = "بمجرد ضغط أي زبون على «اطلب العرض الآن» سيصلك إشعار فوري ويظهر الطلب هنا.",
        )
      } else {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
        ) {
          items(ordersState.data, key = { it.id }) { order ->
            VendorOrderActionCard(
              order = order,
              isBusy = isBusy,
              onAccept = { onAcceptOrder(order) },
              onDelivered = { onMarkDelivered(order) },
              onCancel = { onRequestCancel(order) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun VendorOrderActionCard(
  order: Order,
  isBusy: Boolean,
  onAccept: () -> Unit,
  onDelivered: () -> Unit,
  onCancel: () -> Unit,
) {
  val status = OrderStatus.fromKey(order.status)

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    modifier = Modifier.fillMaxWidth().testTag("vendor_order_card_${order.id}"),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "${order.offerTitle} (×${order.quantity})",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.weight(1f),
        )
        OrderStatusPill(statusKey = order.status)
      }

      Spacer(modifier = Modifier.height(10.dp))
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.padding(12.dp),
        ) {
          Text(
            text = "اسم الزبون: ${order.customerName}",
            style = MaterialTheme.typography.titleSmall,
          )
          Text(
            text = "رقم الهاتف: ${order.customerPhone}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "عنوان التوصيل: ${order.deliveryAddress}",
            style = MaterialTheme.typography.bodyMedium,
          )
          if (order.notes.isNotBlank()) {
            Text(
              text = "ملاحظات: ${order.notes}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column {
          Text(
            text = "إجمالي الطلب: ${ImageAndFormatUtils.formatEgp(order.totalPrice)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "عمولة المنصة (10%): ${ImageAndFormatUtils.formatEgp(order.commissionAmount)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Text(
          text = ImageAndFormatUtils.formatTimestamp(order.createdAt),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (status == OrderStatus.CANCELLED && order.cancellationReason.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "سبب الإلغاء المسجل: ${order.cancellationReason}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      if (status == OrderStatus.PENDING || status == OrderStatus.ACCEPTED) {
        Spacer(modifier = Modifier.height(14.dp))
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (status == OrderStatus.PENDING) {
            Button(
              onClick = onAccept,
              enabled = !isBusy,
              modifier =
                Modifier.weight(1f)
                  .height(48.dp)
                  .testTag("vendor_accept_order_${order.id}"),
            ) {
              Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.accept_order))
            }
          }

          Button(
            onClick = onDelivered,
            enabled = !isBusy,
            colors =
              ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            modifier =
              Modifier.weight(1f)
                .height(48.dp)
                .testTag("vendor_deliver_order_${order.id}"),
          ) {
            Icon(Icons.Filled.DeliveryDining, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.mark_delivered))
          }

          OutlinedButton(
            onClick = onCancel,
            enabled = !isBusy,
            colors =
              ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier =
              Modifier.height(48.dp)
                .testTag("vendor_cancel_order_${order.id}"),
          ) {
            Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("إلغاء")
          }
        }
      }
    }
  }
}

@Composable
private fun VendorOffersInventoryTab(
  offersState: UiState<List<Offer>>,
  isBusy: Boolean,
  onAdjustStock: (Offer, Long) -> Unit,
  onToggleActive: (Offer) -> Unit,
  onDeleteOffer: (Offer) -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
  ) {
    item {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text =
            "نظام العروض المستمرة: لا يتم حذف أي عرض تلقائياً بمرور الوقت؛ يظل العرض ظاهراً للزبائن طالما الكمية أكبر من صفر ولم تقم بإيقافه يدوياً.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.padding(12.dp),
        )
      }
    }

    when (offersState) {
      is UiState.Loading -> {
        item {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().padding(48.dp),
          ) {
            CircularProgressIndicator()
          }
        }
      }
      is UiState.Error -> {
        item {
          EmptyStateBox(
            icon = Icons.Filled.Inventory2,
            title = "تعذر تحميل عروض المحل",
            subtitle = offersState.message,
          )
        }
      }
      is UiState.Success -> {
        if (offersState.data.isEmpty()) {
          item {
            EmptyStateBox(
              icon = Icons.Filled.Inventory2,
              title = "لا توجد عروض مضافة لمحلك",
              subtitle = "اضغط على زر «إضافة عرض جديد» بالأسفل لعرض منتجاتك لأهل القرية.",
            )
          }
        } else {
          items(offersState.data, key = { it.id }) { offer ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
              modifier = Modifier.fillMaxWidth().testTag("vendor_offer_card_${offer.id}"),
            ) {
              Column {
                OfferImageBanner(
                  imageBase64 = offer.imageBase64,
                  defaultImageKey = offer.defaultImageKey,
                  contentDescription = offer.title,
                  height = 145.dp,
                )
                Column(modifier = Modifier.padding(14.dp)) {
                  Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = offer.title,
                      style = MaterialTheme.typography.titleMedium,
                      modifier = Modifier.weight(1f),
                    )
                    Surface(
                      color =
                        if (offer.isAvailableForCustomers) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                      shape = RoundedCornerShape(50),
                    ) {
                      Text(
                        text =
                          when {
                            offer.isVendorSuspended -> "حساب معلق"
                            offer.stockQuantity <= 0L -> "نفدت الكمية (مخفي)"
                            !offer.isActive -> "متوقف مؤقتاً (مخفي)"
                            else -> "ظاهر للزبائن"
                          },
                        style = MaterialTheme.typography.labelSmall,
                        color =
                          if (offer.isAvailableForCustomers)
                            MaterialTheme.colorScheme.onPrimaryContainer
                          else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text =
                      "سعر العرض: ${ImageAndFormatUtils.formatEgp(offer.offerPrice)} (بدلاً من ${ImageAndFormatUtils.formatEgp(offer.originalPrice)})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                  )

                  Spacer(modifier = Modifier.height(10.dp))
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text("المخزون المتاح:", style = MaterialTheme.typography.labelLarge)
                      IconButton(
                        onClick = {
                          if (offer.stockQuantity > 0L) onAdjustStock(offer, offer.stockQuantity - 1L)
                        },
                        enabled = !isBusy && offer.stockQuantity > 0L,
                      ) {
                        Icon(Icons.Filled.Remove, contentDescription = "نقص المخزون")
                      }
                      Text(
                        text = "${offer.stockQuantity}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                      )
                      IconButton(
                        onClick = { onAdjustStock(offer, offer.stockQuantity + 5L) },
                        enabled = !isBusy,
                      ) {
                        Icon(Icons.Filled.Add, contentDescription = "تزويد 5 قطع")
                      }
                    }

                    Row {
                      IconButton(
                        onClick = { onToggleActive(offer) },
                        enabled = !isBusy,
                        modifier = Modifier.testTag("vendor_toggle_offer_${offer.id}"),
                      ) {
                        Icon(
                          imageVector =
                            if (offer.isActive) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                          contentDescription = if (offer.isActive) "إيقاف العرض" else "تفعيل العرض",
                          tint = MaterialTheme.colorScheme.secondary,
                        )
                      }
                      IconButton(
                        onClick = { onDeleteOffer(offer) },
                        enabled = !isBusy,
                        modifier = Modifier.testTag("vendor_delete_offer_${offer.id}"),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.DeleteOutline,
                          contentDescription = "حذف العرض",
                          tint = MaterialTheme.colorScheme.error,
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
  }
}

@Composable
private fun VendorNotificationsTab(
  notificationsState: UiState<List<AppNotification>>,
  onMarkRead: (String) -> Unit,
) {
  when (notificationsState) {
    is UiState.Loading -> {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator()
      }
    }
    is UiState.Error -> {
      EmptyStateBox(
        icon = Icons.Filled.Notifications,
        title = "تعذر تحميل الإشعارات",
        subtitle = notificationsState.message,
      )
    }
    is UiState.Success -> {
      if (notificationsState.data.isEmpty()) {
        EmptyStateBox(
          icon = Icons.Filled.Notifications,
          title = "سجل الإشعارات فارغ",
          subtitle = "ستصل هنا إشعارات الطلبات الفورية من الزبائن لحظة تأكيدها.",
        )
      } else {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
        ) {
          items(notificationsState.data, key = { it.id }) { notif ->
            NotificationItemCard(notification = notif, onMarkRead = onMarkRead)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVendorOfferBottomSheet(
  vendorStore: VendorStore,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onPublish: (String, String, String, String, String, String, ActivityType) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var title by rememberSaveable { mutableStateOf("") }
  var description by rememberSaveable { mutableStateOf("") }
  var originalPrice by rememberSaveable { mutableStateOf("") }
  var offerPrice by rememberSaveable { mutableStateOf("") }
  var stockQuantity by rememberSaveable { mutableStateOf("10") }
  var imageBase64 by rememberSaveable { mutableStateOf("") }
  var selectedDefaultKey by rememberSaveable { mutableStateOf(vendorStore.activityType) }
  var compressingImage by remember { mutableStateOf(false) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        compressingImage = true
        scope.launch {
          val encoded = ImageAndFormatUtils.compressUriToBase64(context, uri)
          if (!encoded.isNullOrBlank()) {
            imageBase64 = encoded
          }
          compressingImage = false
        }
      }
    }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp)
          .padding(bottom = 28.dp),
    ) {
      Text("إضافة عرض جديد (${vendorStore.storeName})", style = MaterialTheme.typography.headlineSmall)

      OfferImageBanner(
        imageBase64 = imageBase64,
        defaultImageKey = selectedDefaultKey,
        contentDescription = "معاينة صورة العرض",
        height = 155.dp,
        modifier = Modifier.clip(MaterialTheme.shapes.medium),
      )

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        OutlinedButton(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          enabled = !compressingImage,
          modifier = Modifier.weight(1f).testTag("pick_offer_photo_button"),
        ) {
          Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (compressingImage) "جاري ضغط الصورة..." else "صورة من الهاتف")
        }
        if (imageBase64.isNotBlank()) {
          TextButton(onClick = { imageBase64 = "" }) { Text("استخدام الافتراضية") }
        }
      }

      if (imageBase64.isBlank()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          ActivityType.entries.forEach { act ->
            FilterChip(
              selected = selectedDefaultKey == act.key,
              onClick = { selectedDefaultKey = act.key },
              label = { Text("افتراضية: ${act.arabicLabel}") },
            )
          }
        }
      }

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("اسم العرض") },
        placeholder = { Text("مثال: عرض وجبة عائلية / كرتونة التوفير") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("offer_title_input"),
      )

      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("تفاصيل العرض ومكوناته") },
        modifier = Modifier.fillMaxWidth().testTag("offer_desc_input"),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
          value = originalPrice,
          onValueChange = { originalPrice = it },
          label = { Text("السعر الأصلي (ج.م)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("offer_orig_price_input"),
        )
        OutlinedTextField(
          value = offerPrice,
          onValueChange = { offerPrice = it },
          label = { Text("سعر العرض (ج.م)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("offer_sale_price_input"),
        )
      }

      OutlinedTextField(
        value = stockQuantity,
        onValueChange = { stockQuantity = it },
        label = { Text("الكمية المتاحة (يختفي العرض فقط عند وصولها لـ 0)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("offer_stock_input"),
      )

      Button(
        onClick = {
          onPublish(
            title,
            description,
            originalPrice,
            offerPrice,
            stockQuantity,
            imageBase64,
            ActivityType.fromKey(selectedDefaultKey),
          )
        },
        enabled = !isBusy && !compressingImage,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
        modifier =
          Modifier.fillMaxWidth()
            .height(52.dp)
            .testTag("publish_offer_submit_button"),
      ) {
        if (isBusy) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = Color.White,
            strokeWidth = 2.dp,
          )
        } else {
          Text("نشر العرض الآن للزبائن")
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CancelOrderReasonDialog(
  order: Order,
  onDismiss: () -> Unit,
  onConfirmCancel: (String) -> Unit,
) {
  var reason by rememberSaveable { mutableStateOf("") }
  val presetReasons =
    listOf(
      "العنوان خارج نطاق توصيل القرية",
      "تعذر الوصول لرقم هاتف العميل",
      "العميل طلب إلغاء الطلب هاتفياً",
      "إغلاق طارئ للمحل حالياً",
    )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("تأكيد إلغاء الطلب وذكر السبب") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text =
            "تنبيه: يتم تسجيل جميع حالات الإلغاء ومراجعتها في لوحة الإدارة. يرجى تحديد سبب الإلغاء بدقة:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          presetReasons.forEach { preset ->
            AssistChip(onClick = { reason = preset }, label = { Text(preset) })
          }
        }
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          label = { Text("سبب الإلغاء (إلزامي)") },
          modifier = Modifier.fillMaxWidth().testTag("cancel_reason_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmCancel(reason) },
        enabled = reason.trim().length >= 2,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.testTag("confirm_cancel_order_button"),
      ) {
        Text("تأكيد الإلغاء")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("تراجع") }
    },
  )
}
