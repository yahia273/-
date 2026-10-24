package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ActivityType
import com.example.data.model.AppNotification
import com.example.data.model.Offer
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.util.ImageAndFormatUtils
import com.example.data.util.LocalNotificationHelper
import com.example.ui.components.ActivityBadgeChip
import com.example.ui.components.EmptyStateBox
import com.example.ui.components.NotificationItemCard
import com.example.ui.components.OfferImageBanner
import com.example.ui.components.OrderStatusPill
import com.example.ui.viewmodel.CustomerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerMainScreen(
  userProfile: UserProfile,
  viewModel: CustomerViewModel,
  onSwitchRole: () -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val offersState by viewModel.offersState.collectAsStateWithLifecycle()
  val myOrdersState by viewModel.myOrdersState.collectAsStateWithLifecycle()
  val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
  val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val isSubmitting by viewModel.orderSubmitting.collectAsStateWithLifecycle()
  val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  var selectedOfferForOrder by remember { mutableStateOf<Offer?>(null) }
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

  val unreadCount =
    (notificationsState as? UiState.Success)?.data?.count { !it.isRead } ?: 0

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isExpanded = maxWidth >= 640.dp

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text("عروض القرية", style = MaterialTheme.typography.titleLarge)
              Text(
                text = "أهلاً ${userProfile.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
          actions = {
            IconButton(onClick = onSwitchRole, modifier = Modifier.testTag("customer_switch_role")) {
              Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.switch_role))
            }
            IconButton(onClick = onSignOut, modifier = Modifier.testTag("customer_sign_out")) {
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
      bottomBar = {
        if (!isExpanded) {
          NavigationBar {
            NavigationBarItem(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              icon = { Icon(Icons.Filled.Explore, contentDescription = "العروض") },
              label = { Text("العروض") },
              modifier = Modifier.testTag("customer_tab_offers"),
            )
            NavigationBarItem(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              icon = { Icon(Icons.Filled.ReceiptLong, contentDescription = "طلباتي") },
              label = { Text("طلباتي") },
              modifier = Modifier.testTag("customer_tab_orders"),
            )
            NavigationBarItem(
              selected = selectedTab == 2,
              onClick = { selectedTab = 2 },
              icon = {
                BadgedBox(
                  badge = {
                    if (unreadCount > 0) {
                      Badge { Text("$unreadCount") }
                    }
                  }
                ) {
                  Icon(Icons.Filled.Notifications, contentDescription = "الإشعارات")
                }
              },
              label = { Text("الإشعارات") },
              modifier = Modifier.testTag("customer_tab_notifications"),
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
              icon = { Icon(Icons.Filled.Explore, contentDescription = "العروض") },
              label = { Text("العروض") },
            )
            NavigationRailItem(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              icon = { Icon(Icons.Filled.ReceiptLong, contentDescription = "طلباتي") },
              label = { Text("طلباتي") },
            )
            NavigationRailItem(
              selected = selectedTab == 2,
              onClick = { selectedTab = 2 },
              icon = {
                BadgedBox(
                  badge = {
                    if (unreadCount > 0) {
                      Badge { Text("$unreadCount") }
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

        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
          when (selectedTab) {
            0 ->
              CustomerOffersFeedTab(
                offersState = offersState,
                selectedCategory = selectedCategory,
                searchQuery = searchQuery,
                onCategoryChange = viewModel::setCategory,
                onSearchChange = viewModel::setSearchQuery,
                onSelectOffer = { selectedOfferForOrder = it },
              )
            1 -> CustomerOrdersTab(ordersState = myOrdersState)
            2 ->
              CustomerNotificationsTab(
                notificationsState = notificationsState,
                onMarkRead = viewModel::markNotificationRead,
              )
          }
        }
      }
    }
  }

  selectedOfferForOrder?.let { offer ->
    OfferOrderBottomSheet(
      offer = offer,
      defaultCustomerName = userProfile.name,
      defaultCustomerPhone = userProfile.phone,
      isSubmitting = isSubmitting,
      onDismiss = { selectedOfferForOrder = null },
      onConfirmOrder = { name, phone, address, notes, qty ->
        viewModel.placeOrder(
          offer = offer,
          customerName = name,
          customerPhone = phone,
          deliveryAddress = address,
          notes = notes,
          quantity = qty,
          onOrderCreated = {
            selectedOfferForOrder = null
            selectedTab = 1
          },
        )
      },
    )
  }
}

@Composable
private fun CustomerOffersFeedTab(
  offersState: UiState<List<Offer>>,
  selectedCategory: String,
  searchQuery: String,
  onCategoryChange: (String) -> Unit,
  onSearchChange: (String) -> Unit,
  onSelectOffer: (Offer) -> Unit,
) {
  val categories =
    listOf(
      "ALL" to "الكل",
      ActivityType.RESTAURANT.key to "مطاعم",
      ActivityType.SUPERMARKET.key to "سوبر ماركت",
      ActivityType.CAFE.key to "مقاهي",
    )

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
  ) {
    item {
      // Marketplace Promo Banner
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(132.dp)
            .clip(MaterialTheme.shapes.large)
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_village_hero),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(
                Brush.horizontalGradient(
                  listOf(Color(0xFF042F21).copy(alpha = 0.90f), Color.Black.copy(alpha = 0.35f))
                )
              )
              .padding(18.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          Column {
            Text(
              text = "عروض حقيقية ومستمرة طوال توفر الكمية!",
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "اطلب الآن ويصل إشعار فوري للمحل لتجهيز وتوصيل طلبك",
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.88f),
            )
          }
        }
      }
    }

    item {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("ابحث عن عرض أو اسم مطعم أو سوبر ماركت...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "بحث") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("customer_search_input"),
      )
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories) { (key, label) ->
          FilterChip(
            selected = selectedCategory == key,
            onClick = { onCategoryChange(key) },
            label = { Text(label) },
            modifier = Modifier.testTag("category_chip_$key"),
          )
        }
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
            icon = Icons.Filled.LocalOffer,
            title = "تعذر تحميل العروض",
            subtitle = offersState.message,
          )
        }
      }
      is UiState.Success -> {
        if (offersState.data.isEmpty()) {
          item {
            EmptyStateBox(
              icon = Icons.Filled.LocalOffer,
              title = "لا توجد عروض متاحة حالياً",
              subtitle = "ستظهر عروض مطاعم وسوبر ماركت القرية هنا فور إضافتها من التجار.",
            )
          }
        } else {
          items(offersState.data, key = { it.id }) { offer ->
            CustomerOfferCard(offer = offer, onOrderClick = { onSelectOffer(offer) })
          }
        }
      }
    }
  }
}

@Composable
private fun CustomerOfferCard(offer: Offer, onOrderClick: () -> Unit) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    shape = MaterialTheme.shapes.large,
    modifier =
      Modifier.fillMaxWidth()
        .testTag("customer_offer_card_${offer.id}")
        .clickable(onClick = onOrderClick),
  ) {
    Column {
      Box {
        OfferImageBanner(
          imageBase64 = offer.imageBase64,
          defaultImageKey = offer.defaultImageKey,
          contentDescription = offer.title,
          height = 185.dp,
        )
        if (offer.discountPercent > 0) {
          Surface(
            color = MaterialTheme.colorScheme.tertiary,
            shape = RoundedCornerShape(bottomEnd = 16.dp),
            modifier = Modifier.align(Alignment.TopStart),
          ) {
            Text(
              text = "خصم ${offer.discountPercent}%",
              style = MaterialTheme.typography.labelLarge,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }
        Surface(
          color = Color.Black.copy(alpha = 0.68f),
          shape = RoundedCornerShape(50),
          modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
        ) {
          Text(
            text = "متبقي: ${offer.stockQuantity}",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              imageVector = Icons.Filled.Storefront,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = offer.storeName,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          ActivityBadgeChip(activityKey = offer.activityType)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = offer.title, style = MaterialTheme.typography.titleLarge)
        if (offer.description.isNotBlank()) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = offer.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column {
            if (offer.originalPrice > offer.offerPrice) {
              Text(
                text = ImageAndFormatUtils.formatEgp(offer.originalPrice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
              )
            }
            Text(
              text = ImageAndFormatUtils.formatEgp(offer.offerPrice),
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
            )
          }

          Button(
            onClick = onOrderClick,
            colors =
              ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            shape = MaterialTheme.shapes.medium,
            modifier =
              Modifier.height(48.dp)
                .testTag("order_offer_btn_${offer.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.ShoppingCartCheckout,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.order_offer_now))
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfferOrderBottomSheet(
  offer: Offer,
  defaultCustomerName: String,
  defaultCustomerPhone: String,
  isSubmitting: Boolean,
  onDismiss: () -> Unit,
  onConfirmOrder: (String, String, String, String, Long) -> Unit,
) {
  var recipientName by rememberSaveable { mutableStateOf(defaultCustomerName) }
  var phone by rememberSaveable { mutableStateOf(defaultCustomerPhone) }
  var deliveryAddress by rememberSaveable { mutableStateOf("") }
  var notes by rememberSaveable { mutableStateOf("") }
  var quantity by rememberSaveable { mutableLongStateOf(1L) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp)
          .padding(bottom = 28.dp),
    ) {
      OfferImageBanner(
        imageBase64 = offer.imageBase64,
        defaultImageKey = offer.defaultImageKey,
        contentDescription = offer.title,
        height = 150.dp,
        modifier = Modifier.clip(MaterialTheme.shapes.medium),
      )

      Text(text = offer.title, style = MaterialTheme.typography.headlineSmall)
      Text(
        text = "${offer.storeName} • متاح بالمخزون: ${offer.stockQuantity}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
      if (offer.description.isNotBlank()) {
        Text(
          text = offer.description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Quantity Selector
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
          Text("الكمية المطلوبة:", style = MaterialTheme.typography.titleMedium)
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = { if (quantity > 1L) quantity -= 1L },
              enabled = quantity > 1L,
              modifier = Modifier.testTag("order_qty_minus"),
            ) {
              Icon(Icons.Filled.Remove, contentDescription = "تقليل الكمية")
            }
            Text(
              text = "$quantity",
              style = MaterialTheme.typography.titleLarge,
              modifier = Modifier.padding(horizontal = 12.dp),
            )
            IconButton(
              onClick = { if (quantity < offer.stockQuantity) quantity += 1L },
              enabled = quantity < offer.stockQuantity,
              modifier = Modifier.testTag("order_qty_plus"),
            ) {
              Icon(Icons.Filled.Add, contentDescription = "زيادة الكمية")
            }
          }
        }
      }

      Text("بيانات التوصيل للزبون", style = MaterialTheme.typography.titleMedium)

      OutlinedTextField(
        value = recipientName,
        onValueChange = { recipientName = it },
        label = { Text("اسم العميل المستلم") },
        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("order_recipient_name_input"),
      )

      OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("رقم الهاتف للتواصل") },
        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("order_phone_input"),
      )

      OutlinedTextField(
        value = deliveryAddress,
        onValueChange = { deliveryAddress = it },
        label = { Text("عنوان التوصيل داخل القرية / علامة مميزة") },
        placeholder = { Text("مثال: بجوار المسجد الكبير - منزل الحاج...") },
        leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().testTag("order_address_input"),
      )

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("ملاحظات إضافية للمحل (اختياري)") },
        modifier = Modifier.fillMaxWidth().testTag("order_notes_input"),
      )

      val totalPrice = offer.offerPrice * quantity
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(14.dp),
        ) {
          Text(
            text = "الإجمالي المطلوب عند الاستلام:",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
            text = ImageAndFormatUtils.formatEgp(totalPrice),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      Button(
        onClick = {
          onConfirmOrder(recipientName, phone, deliveryAddress, notes, quantity)
        },
        enabled = !isSubmitting,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
        modifier =
          Modifier.fillMaxWidth()
            .height(54.dp)
            .testTag("confirm_order_submit_button"),
      ) {
        if (isSubmitting) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = Color.White,
            strokeWidth = 2.dp,
          )
        } else {
          Text(
            text = stringResource(R.string.confirm_order),
            style = MaterialTheme.typography.titleMedium,
          )
        }
      }
    }
  }
}

@Composable
private fun CustomerOrdersTab(ordersState: UiState<List<Order>>) {
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
          title = "لم تقم بأي طلبات بعد",
          subtitle = "اختر عرضك المفضل من الصفحة الرئيسية واطلبه ليصلك لباب المنزل.",
        )
      } else {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
        ) {
          items(ordersState.data, key = { it.id }) { order ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
              modifier = Modifier.fillMaxWidth().testTag("customer_order_card_${order.id}"),
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = order.offerTitle,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                  )
                  OrderStatusPill(statusKey = order.status)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "المحل: ${order.storeName} • الكمية: ${order.quantity}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = "العنوان: ${order.deliveryAddress}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (
                  order.status == OrderStatus.CANCELLED.key &&
                    order.cancellationReason.isNotBlank()
                ) {
                  Spacer(modifier = Modifier.height(8.dp))
                  Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = "سبب الإلغاء من المحل: ${order.cancellationReason}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onErrorContainer,
                      modifier = Modifier.padding(8.dp),
                    )
                  }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                  horizontalArrangement = Arrangement.SpaceBetween,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = ImageAndFormatUtils.formatTimestamp(order.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = "الإجمالي: ${ImageAndFormatUtils.formatEgp(order.totalPrice)}",
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
}

@Composable
private fun CustomerNotificationsTab(
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
          title = "لا توجد إشعارات حالياً",
          subtitle = "ستصلك إشعارات فورية هنا بمجرد قبول طلبك أو توصيله.",
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
