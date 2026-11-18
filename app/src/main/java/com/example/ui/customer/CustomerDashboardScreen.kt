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
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.DailyMarkChoice
import com.example.domain.LeaveStatus
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinLeave
import com.example.domain.TiffinStatus
import com.example.ui.auth.ErrorNoticeCard
import com.example.ui.theme.DeepBlueText
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GlassCard
import com.example.ui.theme.PillTone
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SkyPrimary
import com.example.ui.theme.StatusPill
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.lStr
import com.example.ui.theme.localizedDate
import com.example.ui.theme.localizedGreeting
import com.example.ui.theme.localizedLabel
import com.example.ui.theme.localizedMealSlot
import com.example.ui.theme.localizedTime

@Composable
fun CustomerDashboardScreen(
    state: CustomerUiState,
    onRefresh: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToMenu: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToPayments: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDiscovery: () -> Unit = {},
    onNavigateToRateMess: () -> Unit = {},
    onDismissLocationBanner: () -> Unit = {},
    onMarkTodayTiffin: (DailyMarkChoice) -> Unit = {},
    onCreateLeave: (startDateIso: String, endDateIso: String, reason: String) -> Unit = { _, _, _ -> },
    onCancelLeave: (TiffinLeave) -> Unit = {}
) {
    val todayDate = TiffinBusinessLogic.messToday(state.mess.timezone)
    val todayIso = todayDate.toString()
    val greeting = localizedGreeting(state.mess.timezone)
    val firstName = state.customer.fullName.substringBefore(" ").ifBlank { lStr(R.string.friend_fallback) }
    val pastCutoff = TiffinBusinessLogic.isPastDailyCutoff(state.mess.dailyMarkCutoffTime, state.mess.timezone)
    val cutoffDisplay = TiffinBusinessLogic.formatCutoffDisplay(state.mess.dailyMarkCutoffTime)
    val todayLeave = TiffinBusinessLogic.findActiveLeaveForDate(todayIso, state.leaves, state.customer.id)
    val activeLeaves = state.leaves.filter { it.status == LeaveStatus.ACTIVE && it.endDate >= todayIso }
    var showLeaveDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("customer_dashboard_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                        text = "${localizedDate(todayDate.toString())} • ${state.mess.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (!pastCutoff && todayLeave == null) {
                            lStr(R.string.cust_hint_before_cutoff, cutoffDisplay)
                        } else {
                            lStr(R.string.cust_hint_default)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = SkyPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("customer_refresh_button")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = lStr(R.string.refresh_dashboard_cd), tint = DeepBlueText)
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
                                contentDescription = lStr(R.string.profile_photo_cd),
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
        if (!state.successMessage.isNullOrBlank()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    StatusPill(label = state.successMessage, tone = PillTone.SUCCESS)
                }
            }
        }

        if (!state.locationBannerDismissed && (state.profile.area.isBlank() || state.profile.city.isBlank())) {
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_location_banner"),
                    onClick = onNavigateToProfile
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = SkyPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = lStr(R.string.add_location_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DeepBlueText
                                )
                                Text(
                                    text = lStr(R.string.add_location_sub),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SecondaryText
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismissLocationBanner,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dismiss_location_banner_button")
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = lStr(R.string.dismiss_banner_cd), tint = SecondaryText)
                        }
                    }
                }
            }
        }

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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lStr(R.string.daily_card_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = DeepBlueText
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = SecondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = lStr(R.string.cutoff_label, cutoffDisplay),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (pastCutoff) WarningAmber else SecondaryText
                            )
                        }
                    }
                    when {
                        todayLeave != null -> StatusPill(label = lStr(R.string.daily_on_leave_label), tone = PillTone.WARNING, icon = Icons.Outlined.EventBusy)
                        state.todayMark?.choice == DailyMarkChoice.WANTED -> StatusPill(label = DailyMarkChoice.WANTED.localizedLabel(), tone = PillTone.SUCCESS, icon = Icons.Outlined.ThumbUp)
                        state.todayMark?.choice == DailyMarkChoice.NOT_WANTED -> StatusPill(label = DailyMarkChoice.NOT_WANTED.localizedLabel(), tone = PillTone.ERROR, icon = Icons.Outlined.ThumbDown)
                        pastCutoff -> StatusPill(label = lStr(R.string.daily_cutoff_passed), tone = PillTone.NEUTRAL)
                        else -> Icon(
                            imageVector = Icons.Outlined.LunchDining,
                            contentDescription = null,
                            tint = SkyPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (todayLeave != null) {
                    val reasonSuffix = if (todayLeave.reason.isNotBlank()) " • ${todayLeave.reason}" else ""
                    Text(
                        text = lStr(R.string.daily_leave_active_msg, todayLeave.startDate, todayLeave.endDate, reasonSuffix),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarningAmber
                    )
                    Spacer(Modifier.height(10.dp))
                }

                val canMark = !pastCutoff && todayLeave == null && !state.isActionBusy
                val isWanted = state.todayMark?.choice == DailyMarkChoice.WANTED
                val isNotWanted = state.todayMark?.choice == DailyMarkChoice.NOT_WANTED
                val wantedText = DailyMarkChoice.WANTED.localizedLabel()
                val notWantedText = DailyMarkChoice.NOT_WANTED.localizedLabel()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isWanted) {
                        Button(
                            onClick = { onMarkTodayTiffin(DailyMarkChoice.WANTED) },
                            enabled = canMark,
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("mark_tiffin_wanted_button")
                        ) {
                            Icon(Icons.Outlined.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(wantedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onMarkTodayTiffin(DailyMarkChoice.WANTED) },
                            enabled = canMark,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("mark_tiffin_wanted_button")
                        ) {
                            Icon(Icons.Outlined.ThumbUp, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(wantedText, color = DeepBlueText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    if (isNotWanted) {
                        Button(
                            onClick = { onMarkTodayTiffin(DailyMarkChoice.NOT_WANTED) },
                            enabled = canMark,
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorCoral, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("mark_tiffin_not_wanted_button")
                        ) {
                            Icon(Icons.Outlined.ThumbDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(notWantedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onMarkTodayTiffin(DailyMarkChoice.NOT_WANTED) },
                            enabled = canMark,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("mark_tiffin_not_wanted_button")
                        ) {
                            Icon(Icons.Outlined.ThumbDown, contentDescription = null, tint = ErrorCoral, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(notWantedText, color = DeepBlueText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (pastCutoff && todayLeave == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = lStr(R.string.daily_cutoff_passed_note, cutoffDisplay),
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = SkyPrimary.copy(alpha = 0.14f))
                Spacer(Modifier.height(10.dp))

                if (state.todayTiffins.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            label = if (todayLeave != null || isNotWanted) lStr(R.string.no_tiffin_today) else lStr(R.string.pending_delivery),
                            tone = if (todayLeave != null || isNotWanted) PillTone.NEUTRAL else PillTone.WARNING,
                            icon = Icons.Outlined.HourglassEmpty
                        )
                        Text(
                            text = if (todayLeave != null || isNotWanted) lStr(R.string.not_counted_or_billed) else lStr(R.string.awaiting_delivery),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.todayTiffins.forEach { rec ->
                            val (label, tone, icon) = when (rec.status) {
                                TiffinStatus.DELIVERED -> Triple(lStr(R.string.tiffin_delivered), PillTone.SUCCESS, Icons.Outlined.CheckCircle)
                                TiffinStatus.NOT_DELIVERED -> Triple(lStr(R.string.tiffin_not_received), PillTone.ERROR, Icons.Outlined.Close)
                                TiffinStatus.CANCELLED -> Triple(lStr(R.string.tiffin_cancelled), PillTone.NEUTRAL, Icons.Outlined.Close)
                            }
                            val timeStr = localizedTime(rec.deliveredAt, state.mess.timezone)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = localizedMealSlot(rec.mealSlot),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = DeepBlueText
                                    )
                                    if (!timeStr.isNullOrBlank()) {
                                        Text(
                                            text = lStr(R.string.delivered_at_time, timeStr),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SuccessGreen
                                        )
                                    }
                                    if (!rec.note.isNullOrBlank()) {
                                        Text(
                                            text = lStr(R.string.note_prefix, rec.note),
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

        item {
            val bill = state.currentMonthBill
            val perRate = TiffinBusinessLogic.computePerTiffinRate(state.activeSubscription, state.mess)
            val dueSoFar = bill?.balanceDue ?: (state.monthSummary.deliveredCount * perRate)
            val alreadyPaid = bill?.amountPaid ?: 0.0
            val sub = state.activeSubscription
            val subTone = when (sub?.status) {
                com.example.domain.SubscriptionStatus.ACTIVE -> PillTone.SUCCESS
                com.example.domain.SubscriptionStatus.EXPIRING_SOON,
                com.example.domain.SubscriptionStatus.PAUSED -> PillTone.WARNING
                com.example.domain.SubscriptionStatus.EXPIRED -> PillTone.ERROR
                com.example.domain.SubscriptionStatus.CANCELLED, null -> PillTone.NEUTRAL
            }
            val subLabel = if (sub != null) {
                lStr(R.string.plan_prefix, sub.status.localizedLabel())
            } else {
                lStr(R.string.no_plan)
            }

            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("is_mahine_ka_hisab_card"),
                strongSurface = true,
                onClick = onNavigateToPayments
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lStr(R.string.hisab_card_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepBlueText
                    )
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = SkyPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lStr(R.string.tiffins_taken), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                        Text(
                            text = state.monthSummary.deliveredCount.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("hisab_taken_value")
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lStr(R.string.tiffins_remaining), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                        Text(
                            text = state.monthSummary.remainingTiffins?.toString() ?: lStr(R.string.unlimited_label),
                            style = MaterialTheme.typography.headlineSmall,
                            color = DeepBlueText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("hisab_remaining_value")
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (bill != null) lStr(R.string.amount_due) else lStr(R.string.amount_due_est),
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText
                        )
                        Text(
                            text = TiffinBusinessLogic.formatCurrency(dueSoFar, state.mess.currency),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (dueSoFar > 0) ErrorCoral else SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("hisab_due_value")
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lStr(R.string.already_paid), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                        Text(
                            text = TiffinBusinessLogic.formatCurrency(alreadyPaid, state.mess.currency),
                            style = MaterialTheme.typography.titleLarge,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("hisab_paid_value")
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = SkyPrimary.copy(alpha = 0.14f))
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        label = subLabel,
                        tone = subTone,
                        modifier = Modifier.clickable(onClick = onNavigateToSubscription)
                    )
                    Text(
                        text = lStr(R.string.view_bills_arrow),
                        style = MaterialTheme.typography.labelLarge,
                        color = SkyPrimary
                    )
                }
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("customer_leave_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lStr(R.string.leave_card_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = DeepBlueText
                        )
                        Text(
                            text = lStr(R.string.leave_card_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    }
                    OutlinedButton(
                        onClick = { showLeaveDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("set_leave_button")
                    ) {
                        Icon(Icons.Outlined.EventBusy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(lStr(R.string.set_leave_btn))
                    }
                }

                if (activeLeaves.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeLeaves.forEach { leave ->
                            val canCancel = TiffinBusinessLogic.canCustomerCancelLeave(leave, todayIso)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = lStr(R.string.date_range_to, leave.startDate, leave.endDate),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = DeepBlueText
                                    )
                                    if (leave.reason.isNotBlank()) {
                                        Text(
                                            text = leave.reason,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SecondaryText
                                        )
                                    }
                                }
                                if (canCancel) {
                                    OutlinedButton(
                                        onClick = { onCancelLeave(leave) },
                                        enabled = !state.isActionBusy,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("cancel_leave_${leave.id}")
                                    ) {
                                        Text(lStr(R.string.action_cancel), color = ErrorCoral)
                                    }
                                } else {
                                    StatusPill(label = lStr(R.string.leave_ongoing), tone = PillTone.WARNING)
                                }
                            }
                        }
                    }
                }
            }
        }

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
                        text = lStr(R.string.todays_menu_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepBlueText
                    )
                    StatusPill(
                        label = if (state.todayMenu != null) lStr(R.string.pill_posted) else lStr(R.string.pill_not_posted),
                        tone = if (state.todayMenu != null) PillTone.INFO else PillTone.NEUTRAL
                    )
                }
                Spacer(Modifier.height(10.dp))
                val menu = state.todayMenu
                if (menu == null) {
                    Text(
                        text = lStr(R.string.menu_not_posted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                } else {
                    if (!menu.lunch.isNullOrBlank()) {
                        Text(lStr(R.string.lunch_colon, menu.lunch), style = MaterialTheme.typography.bodyLarge, color = DeepBlueText)
                    }
                    if (!menu.dinner.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(lStr(R.string.dinner_colon, menu.dinner), style = MaterialTheme.typography.bodyLarge, color = DeepBlueText)
                    }
                    if (!menu.specialItems.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = lStr(R.string.special_colon, menu.specialItems),
                            style = MaterialTheme.typography.labelMedium,
                            color = SkyPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_find_tiffin_card"),
                    onClick = onNavigateToDiscovery
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Explore, contentDescription = null, tint = SkyPrimary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(lStr(R.string.find_tiffin_short), style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(lStr(R.string.browse_nearby_short), style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                }

                GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_rate_mess_card"),
                    onClick = onNavigateToRateMess
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.StarOutline, contentDescription = null, tint = SkyPrimary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(lStr(R.string.rate_mess_short), style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(lStr(R.string.review_mess_short, state.mess.name), style = MaterialTheme.typography.labelSmall, color = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickNavChip(lStr(R.string.quick_history), Icons.Outlined.History, onNavigateToHistory, Modifier.weight(1f))
                QuickNavChip(lStr(R.string.quick_menu), Icons.Outlined.RestaurantMenu, onNavigateToMenu, Modifier.weight(1f))
                QuickNavChip(lStr(R.string.quick_plan), Icons.Outlined.CardMembership, onNavigateToSubscription, Modifier.weight(1f))
                QuickNavChip(lStr(R.string.quick_bills), Icons.Outlined.ReceiptLong, onNavigateToPayments, Modifier.weight(1f))
            }
        }
    }

    if (showLeaveDialog) {
        var startInput by remember { mutableStateOf(todayDate.plusDays(1).toString()) }
        var endInput by remember { mutableStateOf(todayDate.plusDays(3).toString()) }
        var reasonInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(lStr(R.string.leave_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = lStr(R.string.leave_dialog_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = startInput == todayIso,
                            onClick = {
                                startInput = todayIso
                                if (endInput < startInput) endInput = startInput
                            },
                            label = { Text(lStr(R.string.chip_from_today)) }
                        )
                        FilterChip(
                            selected = startInput == todayDate.plusDays(1).toString(),
                            onClick = {
                                startInput = todayDate.plusDays(1).toString()
                                if (endInput < startInput) endInput = startInput
                            },
                            label = { Text(lStr(R.string.chip_tomorrow)) }
                        )
                    }
                    OutlinedTextField(
                        value = startInput,
                        onValueChange = { startInput = it },
                        label = { Text(lStr(R.string.field_start_date)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("leave_start_input")
                    )
                    OutlinedTextField(
                        value = endInput,
                        onValueChange = { endInput = it },
                        label = { Text(lStr(R.string.field_end_date)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("leave_end_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2L to "+2d", 4L to "+5d", 6L to "+7d").forEach { (offset, label) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val base = runCatching { java.time.LocalDate.parse(startInput.trim()) }
                                        .getOrDefault(todayDate.plusDays(1))
                                    endInput = base.plusDays(offset).toString()
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = { Text(lStr(R.string.field_leave_reason)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("leave_reason_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        onCreateLeave(startInput, endInput, reasonInput)
                    },
                    modifier = Modifier.testTag("confirm_save_leave_button")
                ) { Text(lStr(R.string.save_leave_btn)) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text(lStr(R.string.action_cancel)) }
            }
        )
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
