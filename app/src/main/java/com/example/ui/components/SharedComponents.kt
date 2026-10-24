package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityType
import com.example.data.model.AppNotification
import com.example.data.model.OrderStatus
import com.example.data.util.ImageAndFormatUtils

@Composable
fun OfferImageBanner(
  imageBase64: String,
  defaultImageKey: String,
  contentDescription: String,
  modifier: Modifier = Modifier,
  height: Dp = 176.dp,
) {
  val decodedBitmap =
    remember(imageBase64) {
      if (imageBase64.isNotBlank()) ImageAndFormatUtils.decodeBase64ToBitmap(imageBase64) else null
    }

  Box(modifier = modifier.fillMaxWidth().height(height)) {
    if (decodedBitmap != null) {
      Image(
        bitmap = decodedBitmap.asImageBitmap(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Image(
        painter = painterResource(id = ImageAndFormatUtils.defaultDrawableForActivity(defaultImageKey)),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

fun activityIcon(activityKey: String): ImageVector =
  when (ActivityType.fromKey(activityKey)) {
    ActivityType.RESTAURANT -> Icons.Filled.Restaurant
    ActivityType.SUPERMARKET -> Icons.Filled.ShoppingBag
    ActivityType.CAFE -> Icons.Filled.LocalCafe
  }

@Composable
fun ActivityBadgeChip(activityKey: String, modifier: Modifier = Modifier) {
  val type = ActivityType.fromKey(activityKey)
  Surface(
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    shape = RoundedCornerShape(50),
    modifier = modifier,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
      Icon(
        imageVector = activityIcon(activityKey),
        contentDescription = type.arabicLabel,
        modifier = Modifier.size(15.dp),
      )
      Spacer(modifier = Modifier.width(5.dp))
      Text(text = type.arabicLabel, style = MaterialTheme.typography.labelMedium)
    }
  }
}

@Composable
fun OrderStatusPill(statusKey: String, modifier: Modifier = Modifier) {
  val status = OrderStatus.fromKey(statusKey)
  val (bgColor, textColor) =
    when (status) {
      OrderStatus.PENDING ->
        MaterialTheme.colorScheme.secondaryContainer to
          MaterialTheme.colorScheme.onSecondaryContainer
      OrderStatus.ACCEPTED ->
        Color(0xFFDBEAFE) to Color(0xFF1E3A8A)
      OrderStatus.DELIVERED ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
      OrderStatus.CANCELLED ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

  Surface(color = bgColor, contentColor = textColor, shape = RoundedCornerShape(50), modifier = modifier) {
    Text(
      text = status.arabicLabel,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
    )
  }
}

@Composable
fun MetricStatCard(
  title: String,
  value: String,
  subtitle: String? = null,
  icon: ImageVector = Icons.Filled.Storefront,
  containerColor: Color = MaterialTheme.colorScheme.surface,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.size(34.dp)
              .clip(CircleShape)
              .background(contentColor.copy(alpha = 0.12f)),
        ) {
          Icon(
            imageVector = icon,
            contentDescription = title,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      if (!subtitle.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = contentColor.copy(alpha = 0.78f),
        )
      }
    }
  }
}

@Composable
fun NotificationItemCard(
  notification: AppNotification,
  onMarkRead: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val isUrgent = notification.type == "SUSPICIOUS_CANCELLATIONS"
  val containerColor =
    when {
      isUrgent -> MaterialTheme.colorScheme.errorContainer
      !notification.isRead -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
      else -> MaterialTheme.colorScheme.surface
    }

  Card(
    colors = CardDefaults.cardColors(containerColor = containerColor),
    elevation = CardDefaults.cardElevation(defaultElevation = if (notification.isRead) 1.dp else 3.dp),
    modifier =
      modifier
        .fillMaxWidth()
        .testTag("notification_card_${notification.id}")
        .clickable {
          if (!notification.isRead && notification.id.isNotBlank()) {
            onMarkRead(notification.id)
          }
        },
  ) {
    Row(
      verticalAlignment = Alignment.Top,
      modifier = Modifier.padding(14.dp),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.size(40.dp)
            .clip(CircleShape)
            .background(
              if (isUrgent) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.primary
            ),
      ) {
        Icon(
          imageVector =
            if (isUrgent) Icons.Filled.WarningAmber else Icons.Filled.NotificationsActive,
          contentDescription = notification.title,
          tint = Color.White,
          modifier = Modifier.size(20.dp),
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = notification.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
          )
          if (!notification.isRead) {
            Surface(
              color = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
              shape = RoundedCornerShape(50),
            ) {
              Text(
                text = "جديد",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          } else {
            Icon(
              imageVector = Icons.Filled.DoneAll,
              contentDescription = "مقروء",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = notification.body, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = ImageAndFormatUtils.formatTimestamp(notification.createdAt),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun EmptyStateBox(
  icon: ImageVector,
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = modifier.fillMaxWidth().padding(32.dp),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(72.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(36.dp),
      )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(text = title, style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
