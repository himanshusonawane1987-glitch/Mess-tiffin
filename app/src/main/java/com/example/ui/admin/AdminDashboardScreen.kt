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
    val todayDisplay = TiffinBusinessLogic.formatDateDisplay(
        TiffinBusinessLogic.messToday(state.mess.timezone).toString()
    )
    val adminFirstName = state.profile.fullName.substringBefore(" ").ifBlank { "Owner" }

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
                        text = "Welcome, $adminFirstName",
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
                }
                Row {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("admin_refresh_button")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh admin dashboard", tint = DeepBlueText)
                    }
                    IconButton(
                        onClick = onOpenProfile,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Mess Settings", tint = DeepBlueText)
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
                                    "${state.pendingRequests.size} Pending Customer Request(s)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DeepBlueText
                                )
                                Text("Tap to review and approve", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        StatusPill(label = "Review", tone = PillTone.WARNING)
                    }
                }
            }
        }

        // Today's Overview
        item {
            Text("Today's Overview", style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Delivery Completion", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${state.completionRatePercent}%",
                            style = MaterialTheme.typography.displaySmall,
                            color = DeepBlueText
                        )
                    }
                    StatusPill(
                        label = "${state.deliveredTodayCount} / ${state.expectedTiffinsToday} tiffins",
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
                    title = "Active Customers",
                    value = state.activeCustomers.size.toString(),
                    icon = Icons.Outlined.Groups,
                    modifier = Modifier.weight(1f)
                )
                MetricValueCard(
                    title = "Delivered Today",
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
                    title = "Pending Today",
                    value = state.pendingTodayCount.toString(),
                    accentColor = WarningAmber,
                    modifier = Modifier.weight(1f)
                )
                MetricValueCard(
                    title = "Not Delivered",
                    value = state.notDeliveredTodayCount.toString(),
                    accentColor = ErrorCoral,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Monthly Financial Overview
        item {
            Text("Monthly Financial Overview", style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricValueCard(
                    title = "Expected Billing",
                    value = TiffinBusinessLogic.formatCurrency(state.monthlyExpectedRevenue, state.mess.currency),
                    modifier = Modifier.weight(1f)
                )
                MetricValueCard(
                    title = "Collected",
                    value = TiffinBusinessLogic.formatCurrency(state.monthlyCollected, state.mess.currency),
                    accentColor = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricValueCard(
                    title = "Pending Balance",
                    value = TiffinBusinessLogic.formatCurrency(state.monthlyPendingBalance, state.mess.currency),
                    accentColor = WarningAmber,
                    modifier = Modifier.weight(1f)
                )
                MetricValueCard(
                    title = "Overdue",
                    value = TiffinBusinessLogic.formatCurrency(state.monthlyOverdueBalance, state.mess.currency),
                    accentColor = ErrorCoral,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Quick Actions
        item {
            Text("Quick Actions", style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminQuickTile("Today's Tiffins", Icons.Outlined.LunchDining, onOpenTiffins, Modifier.weight(1f), "quick_action_tiffins")
                    AdminQuickTile("Daily Menu", Icons.Outlined.RestaurantMenu, onOpenMenu, Modifier.weight(1f), "quick_action_menu")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminQuickTile("Customers", Icons.Outlined.PersonAdd, onOpenCustomers, Modifier.weight(1f), "quick_action_customers")
                    AdminQuickTile("Record Payment", Icons.Outlined.Payments, onOpenBilling, Modifier.weight(1f), "quick_action_billing")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminQuickTile("Subscriptions", Icons.Outlined.CardMembership, onOpenSubscriptions, Modifier.weight(1f), "quick_action_subscriptions")
                    AdminQuickTile("Reports", Icons.Outlined.Assessment, onOpenReports, Modifier.weight(1f), "quick_action_reports")
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
