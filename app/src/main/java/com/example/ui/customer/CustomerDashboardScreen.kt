package com.example.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.BillStatus
import com.example.domain.SubscriptionStatus
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinStatus
import com.example.ui.auth.ErrorNoticeCard
import com.example.ui.theme.DeepBlueText
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GlassCard
import com.example.ui.theme.MetricValueCard
import com.example.ui.theme.PillTone
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SkyPrimary
import com.example.ui.theme.StatusPill
import com.example.ui.theme.SuccessGreen

@Composable
fun CustomerDashboardScreen(
    state: CustomerUiState,
    onRefresh: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToMenu: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToPayments: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val todayDate = TiffinBusinessLogic.messToday(state.mess.timezone)
    val greeting = TiffinBusinessLogic.greetingForMessTime(state.mess.timezone)
    val firstName = state.customer.fullName.substringBefore(" ").ifBlank { "Friend" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$greeting, $firstName",
                        style = MaterialTheme.typography.headlineMedium,
                        color = DeepBlueText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${TiffinBusinessLogic.formatDateDisplay(todayDate.toString())} • ${state.mess.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("customer_refresh_button")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh dashboard", tint = DeepBlueText)
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SkyPrimary.copy(alpha = 0.16f))
                            .clickable(onClick = onNavigateToProfile),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!state.profile.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = state.profile.avatarUrl,
                                contentDescription = "Profile photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = firstName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepBlueText
                            )
                        }
                    }
                }
            }
        }

        if (state.isLoading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)),
                    color = SkyPrimary
                )
            }
        }

        if (!state.errorMessage.isNullOrBlank()) {
            item { ErrorNoticeCard(message = state.errorMessage) }
        }

        // Card 1: Aaj ka Tiffin (Today's Tiffin Status)
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("today_tiffin_status_card"),
                strongSurface = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Aaj ka Tiffin",
                            style = MaterialTheme.typography.titleLarge,
                            color = DeepBlueText
                        )
                        Text(
                            text = TiffinBusinessLogic.formatDateDisplay(todayDate.toString()),
                            style = MaterialTheme.typography.labelMedium,
                            color = SecondaryText
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.LunchDining,
                        contentDescription = null,
                        tint = SkyPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (state.todayTiffins.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            label = "Pending Admin Update",
                            tone = PillTone.WARNING,
                            icon = Icons.Outlined.HourglassEmpty
                        )
                        Text(
                            text = "Not marked yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.todayTiffins.forEach { rec ->
                            val (label, tone, icon) = when (rec.status) {
                                TiffinStatus.DELIVERED -> Triple("Delivered", PillTone.SUCCESS, Icons.Outlined.CheckCircle)
                                TiffinStatus.NOT_DELIVERED -> Triple("Not Received", PillTone.ERROR, Icons.Outlined.Close)
                                TiffinStatus.CANCELLED -> Triple("Cancelled", PillTone.NEUTRAL, Icons.Outlined.Close)
                            }
                            val timeStr = TiffinBusinessLogic.formatTimeInMessZone(rec.deliveredAt, state.mess.timezone)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rec.mealSlot.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = DeepBlueText
                                    )
                                    if (!timeStr.isNullOrBlank()) {
                                        Text(
                                            text = "Delivered at $timeStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuccessGreen
                                        )
                                    }
                                    if (!rec.note.isNullOrBlank()) {
                                        Text(
                                            text = "Note: ${rec.note}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SecondaryText
                                        )
                                    }
                                }
                                StatusPill(label = label, tone = tone, icon = icon)
                            }
                        }
                    }
                }
            }
        }

        // Card 2: Monthly Summary (Is Month)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricValueCard(
                    title = "Kitne Tiffin Mile",
                    value = state.monthSummary.deliveredCount.toString(),
                    subtitle = "Delivered this month",
                    accentColor = SuccessGreen,
                    icon = Icons.Outlined.CheckCircle,
                    modifier = Modifier.weight(1f)
                )
                MetricValueCard(
                    title = "Remaining Tiffins",
                    value = state.monthSummary.remainingTiffins?.toString() ?: "Unlimited",
                    subtitle = state.activeSubscription?.let {
                        "Plan: ${it.includedTiffins} tiffins"
                    } ?: "No active plan",
                    accentColor = DeepBlueText,
                    icon = Icons.Outlined.CalendarMonth,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricValueCard(
                    title = "Missed / Cancelled",
                    value = (state.monthSummary.notDeliveredCount + state.monthSummary.cancelledCount).toString(),
                    subtitle = "${state.monthSummary.scheduledOrTotalRecords} records logged",
                    modifier = Modifier.weight(1f)
                )
                GlassCard(
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToSubscription
                ) {
                    Text(
                        text = "Subscription",
                        style = MaterialTheme.typography.labelMedium,
                        color = SecondaryText
                    )
                    Spacer(Modifier.height(6.dp))
                    val sub = state.activeSubscription
                    val (subLabel, subTone) = when (sub?.status) {
                        SubscriptionStatus.ACTIVE -> "Active" to PillTone.SUCCESS
                        SubscriptionStatus.EXPIRING_SOON -> "Expiring Soon" to PillTone.WARNING
                        SubscriptionStatus.PAUSED -> "Paused" to PillTone.WARNING
                        SubscriptionStatus.EXPIRED -> "Expired" to PillTone.ERROR
                        SubscriptionStatus.CANCELLED -> "Cancelled" to PillTone.NEUTRAL
                        null -> "No Plan" to PillTone.NEUTRAL
                    }
                    StatusPill(label = subLabel, tone = subTone)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = sub?.planNameSnapshot ?: "Tap to view details",
                        style = MaterialTheme.typography.labelSmall,
                        color = DeepBlueText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Card 3: Today's Menu Preview
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("dashboard_menu_card"),
                onClick = onNavigateToMenu
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Menu",
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepBlueText
                    )
                    StatusPill(
                        label = if (state.todayMenu != null) "Published" else "Pending",
                        tone = if (state.todayMenu != null) PillTone.INFO else PillTone.NEUTRAL
                    )
                }
                Spacer(Modifier.height(10.dp))
                val menu = state.todayMenu
                if (menu == null) {
                    Text(
                        text = "Menu for today hasn't been published by ${state.mess.name} yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                } else {
                    if (!menu.lunch.isNullOrBlank()) {
                        Text("Lunch: ${menu.lunch}", style = MaterialTheme.typography.bodyLarge, color = DeepBlueText)
                    }
                    if (!menu.dinner.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Dinner: ${menu.dinner}", style = MaterialTheme.typography.bodyLarge, color = DeepBlueText)
                    }
                    if (!menu.specialItems.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Special: ${menu.specialItems}",
                            style = MaterialTheme.typography.labelMedium,
                            color = SkyPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Card 4: Payment Summary
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("dashboard_payment_card"),
                onClick = onNavigateToPayments
            ) {
                val bill = state.currentMonthBill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current Month Bill",
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepBlueText
                    )
                    if (bill != null) {
                        val (billLabel, billTone) = when (bill.status) {
                            BillStatus.PAID -> "Paid" to PillTone.SUCCESS
                            BillStatus.PARTIALLY_PAID -> "Partially Paid" to PillTone.WARNING
                            BillStatus.PENDING -> "Pending" to PillTone.INFO
                            BillStatus.OVERDUE -> "Overdue" to PillTone.ERROR
                        }
                        StatusPill(label = billLabel, tone = billTone)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (bill == null) {
                    Text(
                        text = "No bill generated for this month yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Bill", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                            Text(
                                TiffinBusinessLogic.formatCurrency(bill.totalAmount, state.mess.currency),
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepBlueText
                            )
                        }
                        Column {
                            Text("Paid", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                            Text(
                                TiffinBusinessLogic.formatCurrency(bill.amountPaid, state.mess.currency),
                                style = MaterialTheme.typography.titleLarge,
                                color = SuccessGreen
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Balance Due", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                            Text(
                                TiffinBusinessLogic.formatCurrency(bill.balanceDue, state.mess.currency),
                                style = MaterialTheme.typography.titleLarge,
                                color = if (bill.balanceDue > 0) ErrorCoral else SuccessGreen
                            )
                        }
                    }
                }
            }
        }

        // Quick Navigation Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickNavChip("History", Icons.Outlined.History, onNavigateToHistory, Modifier.weight(1f))
                QuickNavChip("Menu", Icons.Outlined.RestaurantMenu, onNavigateToMenu, Modifier.weight(1f))
                QuickNavChip("Plan", Icons.Outlined.CardMembership, onNavigateToSubscription, Modifier.weight(1f))
                QuickNavChip("Bills", Icons.Outlined.ReceiptLong, onNavigateToPayments, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickNavChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.minimumInteractiveComponentSize(),
        cornerRadius = 16.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = SkyPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = DeepBlueText,
                maxLines = 1
            )
        }
    }
}
