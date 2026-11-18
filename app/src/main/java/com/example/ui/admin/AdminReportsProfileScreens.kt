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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
import com.example.ui.theme.SkyPrimaryButton
import com.example.ui.theme.StatusPill
import com.example.ui.theme.SuccessGreen
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AdminReportsScreen(
    state: AdminUiState,
    onBack: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit
) {
    val monthTitle = state.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    val totalMonthDelivered = state.monthTiffins.count { it.status == TiffinStatus.DELIVERED }
    val activeSubsCount = state.subscriptions.count { it.status == SubscriptionStatus.ACTIVE || it.status == SubscriptionStatus.EXPIRING_SOON }
    val expiredSubsCount = state.subscriptions.count { it.status == SubscriptionStatus.EXPIRED }
    val deliveredByCustomer = remember(state.monthTiffins) {
        state.monthTiffins.filter { it.status == TiffinStatus.DELIVERED }.groupingBy { it.customerId }.eachCount()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = DeepBlueText)
                }
                Column {
                    Text("Reports", style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
                    Text("Live daily & monthly operational metrics.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSelectMonth(state.selectedMonth.minusMonths(1)) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                    Text(monthTitle, style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    IconButton(onClick = { onSelectMonth(state.selectedMonth.plusMonths(1)) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month")
                    }
                }
            }
        }

        item { Text("Daily Report (${TiffinBusinessLogic.formatDateDisplay(state.selectedDate.toString())})", style = MaterialTheme.typography.titleLarge, color = DeepBlueText) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricValueCard("Expected", state.expectedTiffinsToday.toString(), modifier = Modifier.weight(1f))
                MetricValueCard("Delivered", state.deliveredTodayCount.toString(), accentColor = SuccessGreen, modifier = Modifier.weight(1f))
                MetricValueCard("Completion", "${state.completionRatePercent}%", modifier = Modifier.weight(1f))
            }
        }

        item { Text("Monthly Report ($monthTitle)", style = MaterialTheme.typography.titleLarge, color = DeepBlueText) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricValueCard("Delivered Tiffins", totalMonthDelivered.toString(), accentColor = SuccessGreen, modifier = Modifier.weight(1f))
                MetricValueCard("Active Plans", activeSubsCount.toString(), modifier = Modifier.weight(1f))
                MetricValueCard("Expired Plans", expiredSubsCount.toString(), accentColor = ErrorCoral, modifier = Modifier.weight(1f))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricValueCard("Expected Billing", TiffinBusinessLogic.formatCurrency(state.monthlyExpectedRevenue, state.mess.currency), modifier = Modifier.weight(1f))
                MetricValueCard("Collected", TiffinBusinessLogic.formatCurrency(state.monthlyCollected, state.mess.currency), accentColor = SuccessGreen, modifier = Modifier.weight(1f))
                MetricValueCard("Outstanding", TiffinBusinessLogic.formatCurrency(state.monthlyPendingBalance + state.monthlyOverdueBalance, state.mess.currency), accentColor = ErrorCoral, modifier = Modifier.weight(1f))
            }
        }

        item { Text("Customer-wise Tiffin Count", style = MaterialTheme.typography.titleLarge, color = DeepBlueText) }
        items(state.customers, key = { it.id }) { c ->
            val count = deliveredByCustomer[c.id] ?: 0
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(c.fullName, style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                        Text(c.phone, style = MaterialTheme.typography.bodyMedium)
                    }
                    StatusPill(label = "$count delivered", tone = if (count > 0) PillTone.SUCCESS else PillTone.NEUTRAL)
                }
            }
        }
    }
}

@Composable
fun AdminProfileSettingsScreen(
    state: AdminUiState,
    onUpdateMessSettings: (
        name: String,
        phone: String,
        address: String,
        currency: String,
        defaultDueDay: Int,
        mealSlots: List<String>
    ) -> Unit,
    onRotateCode: (newCode: String) -> Unit,
    onInviteAdmin: (email: String) -> Unit,
    onSignOut: () -> Unit
) {
    var messName by remember(state.mess.name) { mutableStateOf(state.mess.name) }
    var messPhone by remember(state.mess.phone) { mutableStateOf(state.mess.phone) }
    var messAddress by remember(state.mess.address) { mutableStateOf(state.mess.address) }
    var currency by remember(state.mess.currency) { mutableStateOf(state.mess.currency) }
    var dueDay by remember(state.mess.defaultDueDay) { mutableStateOf(state.mess.defaultDueDay.toString()) }
    val selectedSlots = remember(state.mess.mealSlots) {
        mutableStateListOf<String>().apply { addAll(state.mess.mealSlots) }
    }
    var newRegCode by remember { mutableStateOf("") }
    var inviteEmail by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Admin Profile & Settings", style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
        }

        if (!state.errorMessage.isNullOrBlank()) item { ErrorNoticeCard(state.errorMessage) }
        if (!state.successMessage.isNullOrBlank()) {
            item { GlassCard(modifier = Modifier.fillMaxWidth()) { StatusPill(state.successMessage, PillTone.SUCCESS) } }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                Text(state.profile.fullName.ifBlank { state.profile.email }, style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                Text(state.profile.email, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                StatusPill(
                    label = if (state.isOwner) "Mess Owner" else "Mess Admin",
                    tone = PillTone.INFO
                )
            }
        }

        if (state.isOwner) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Text("Mess Configuration (Owner)", style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = messName, onValueChange = { messName = it }, label = { Text("Mess Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = messPhone, onValueChange = { messPhone = it }, label = { Text("Mess Phone") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = messAddress, onValueChange = { messAddress = it }, label = { Text("Mess Address") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("Currency") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = dueDay,
                            onValueChange = { dueDay = it },
                            label = { Text("Due Day (1-28)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Enabled Meal Slots", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("breakfast", "lunch", "dinner", "daily").forEach { slot ->
                            val active = selectedSlots.contains(slot)
                            FilterChip(
                                selected = active,
                                onClick = {
                                    if (active && selectedSlots.size > 1) selectedSlots.remove(slot)
                                    else if (!active) selectedSlots.add(slot)
                                },
                                label = { Text(slot.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SkyPrimaryButton(
                        text = "Save Mess Settings",
                        onClick = {
                            onUpdateMessSettings(
                                messName,
                                messPhone,
                                messAddress,
                                currency,
                                dueDay.toIntOrNull() ?: 5,
                                selectedSlots.toList()
                            )
                        },
                        loading = state.isActionBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Text("Customer Registration Code", style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    Text("Rotate the secret mess code customers use to request membership.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newRegCode,
                        onValueChange = { newRegCode = it.uppercase() },
                        label = { Text("New Registration Code (min 4 chars)") },
                        leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    SkyPrimaryButton(
                        text = "Update Registration Code",
                        onClick = {
                            onRotateCode(newRegCode)
                            newRegCode = ""
                        },
                        enabled = newRegCode.trim().length >= 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Text("Admin Invitations (Owner Only)", style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    Text("Invited emails automatically receive Admin access when signing in with Google.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inviteEmail,
                        onValueChange = { inviteEmail = it },
                        label = { Text("Admin Google Email") },
                        leadingIcon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    SkyPrimaryButton(
                        text = "Send Admin Invitation",
                        onClick = {
                            onInviteAdmin(inviteEmail)
                            inviteEmail = ""
                        },
                        enabled = inviteEmail.contains("@"),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.adminInvitations.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        state.adminInvitations.forEach { inv ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(inv.invitedEmail, style = MaterialTheme.typography.bodyMedium)
                                StatusPill(inv.status, if (inv.status == "accepted") PillTone.SUCCESS else PillTone.INFO)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("admin_logout_button")
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }
}
