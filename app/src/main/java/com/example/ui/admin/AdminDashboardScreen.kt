package com.example.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.TiffinBusinessLogic
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
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.lStr
import com.example.ui.theme.localizedDate

@Composable
fun AdminDashboardScreen(
    state: AdminUiState,
    onRefresh: () -> Unit,
    onOpenTiffins: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenCustomers: () -> Unit,
    onOpenBilling: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val todayDisplay = localizedDate(TiffinBusinessLogic.messToday(state.mess.timezone).toString())
    val adminFirstName = state.profile.fullName.substringBefore(" ").ifBlank { lStr(R.string.owner_fallback) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                        text = lStr(R.string.admin_welcome, adminFirstName),
                        style = MaterialTheme.typography.headlineMedium,
                        color = DeepBlueText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${state.mess.name} • $todayDisplay",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = lStr(R.string.admin_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = SkyPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("admin_refresh_button")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = lStr(R.string.refresh_dashboard_cd), tint = DeepBlueText)
                    }
                    IconButton(
                        onClick = onOpenProfile,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = lStr(R.string.mess_settings_cd), tint = DeepBlueText)
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
            item { ErrorNoticeCard(state.errorMessage) }
        }

        if (state.pendingRequests.isNotEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    strongSurface = true,
                    onClick = onOpenCustomers
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = WarningAmber)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = lStr(R.string.join_requests_count, state.pendingRequests.size),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DeepBlueText
                                )
                                Text(lStr(R.string.tap_approve_reject), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        StatusPill(label = lStr(R.string.pill_review), tone = PillTone.WARNING)
                    }
                }
            }
        }

        item {
            Text(lStr(R.string.todays_overview), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(lStr(R.string.todays_progress), style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "${state.completionRatePercent}%",
                            style = MaterialTheme.typography.displaySmall,
                            color = DeepBlueText
                        )
                    }
                    StatusPill(
                        label = lStr(R.string.tiffins_progress_pill, state.deliveredTodayCount, state.expectedTiffinsToday),
                        tone = PillTone.INFO
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { state.completionRatePercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
                    color = SkyPrimary,
                    trackColor = SkyPrimary.copy(alpha = 0.16f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricValueCard(
                    title = lStr(R.string.metric_active_customers),
                    value = state.activeCustomers.size.toString(),
                    icon = Icons.Outlined.Groups,
                    modifier = Modifier.weight(1f)
                )
                MetricValueCard(
                    title = lStr(R.string.metric_delivered_today),
                    value = state.deliveredTodayCount.toString(),
                    accentColor = SuccessGreen,
                    icon = Icons.Outlined.CheckCircle,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricValueCard(
                    title = lStr(R.string.metric_pending_today),
                    value = state.pendingTodayCount.toString(),
                    accentColor = WarningAmber,
                    modifier = Modifier.weight(1f)
                )
                MetricValueCard(
                    title = lStr(R.string.metric_not_delivered),
                    value = state.notDeliveredTodayCount.toString(),
                    accentColor = ErrorCoral,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(lStr(R.string.this_months_money), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricValueCard(
                    title = lStr(R.string.metric_total_billed),
                    value = TiffinBusinessLogic.formatCurrency(state.monthlyExpectedRevenue, state.mess.currency),
                    modifier = Modifier.weight(1f)
                )
                MetricValueCard(
                    title = lStr(R.string.metric_paid_received),
                    value = TiffinBusinessLogic.formatCurrency(state.monthlyCollected, state.mess.currency),
                    accentColor = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricValueCard(
                    title = lStr(R.string.metric_yet_to_collect),
                    value = TiffinBusinessLogic.formatCurrency(state.monthlyPendingBalance, state.mess.currency),
                    accentColor = WarningAmber,
                    modifier = Modifier.weight(1f)
                )
                MetricValueCard(
                    title = lStr(R.string.bill_overdue),
                    value = TiffinBusinessLogic.formatCurrency(state.monthlyOverdueBalance, state.mess.currency),
                    accentColor = ErrorCoral,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(lStr(R.string.quick_actions), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminQuickTile(lStr(R.string.qa_todays_tiffins), Icons.Outlined.LunchDining, onOpenTiffins, Modifier.weight(1f), "quick_action_tiffins")
                    AdminQuickTile(lStr(R.string.qa_daily_menu), Icons.Outlined.RestaurantMenu, onOpenMenu, Modifier.weight(1f), "quick_action_menu")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminQuickTile(lStr(R.string.qa_customers), Icons.Outlined.PersonAdd, onOpenCustomers, Modifier.weight(1f), "quick_action_customers")
                    AdminQuickTile(lStr(R.string.qa_bills_payments), Icons.Outlined.Payments, onOpenBilling, Modifier.weight(1f), "quick_action_billing")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminQuickTile(lStr(R.string.qa_plans), Icons.Outlined.CardMembership, onOpenSubscriptions, Modifier.weight(1f), "quick_action_subscriptions")
                    AdminQuickTile(lStr(R.string.qa_reports), Icons.Outlined.Assessment, onOpenReports, Modifier.weight(1f), "quick_action_reports")
                }
            }
        }
    }
}

@Composable
private fun AdminQuickTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    GlassCard(
        modifier = modifier.minimumInteractiveComponentSize().testTag(testTag),
        strongSurface = true,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = SkyPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = DeepBlueText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
