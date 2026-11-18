package com.example.ui.customer

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.BillStatus
import com.example.domain.MonthlyBill
import com.example.domain.PaymentStatus
import com.example.domain.SubscriptionStatus
import com.example.domain.TiffinBusinessLogic
import com.example.ui.auth.ErrorNoticeCard
import com.example.ui.theme.DeepBlueText
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GlassCard
import com.example.ui.theme.MetricValueCard
import com.example.ui.theme.PillTone
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SkyPrimary
import com.example.ui.theme.SkyPrimaryButton
import com.example.ui.theme.StatusPill
import com.example.ui.theme.SuccessGreen

@Composable
fun CustomerSubscriptionScreen(
    state: CustomerUiState,
    onRequestRenewal: (planId: String?, note: String?) -> Unit
) {
    var showRenewalDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("My Subscription", style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
            Text("Plan details and monthly tiffin allowance.", style = MaterialTheme.typography.bodyMedium)
        }

        if (!state.successMessage.isNullOrBlank()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    StatusPill(label = state.successMessage, tone = PillTone.SUCCESS)
                }
            }
        }

        item {
            val sub = state.activeSubscription
            if (sub == null) {
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Text("No Active Subscription", style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ask your mess owner to assign a subscription plan, or submit a renewal/plan request below.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    SkyPrimaryButton(
                        text = "Request Plan / Renewal",
                        onClick = { showRenewalDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("request_renewal_button")
                    )
                }
            } else {
                val (statusLabel, tone) = when (sub.status) {
                    SubscriptionStatus.ACTIVE -> "Active" to PillTone.SUCCESS
                    SubscriptionStatus.EXPIRING_SOON -> "Expiring Soon" to PillTone.WARNING
                    SubscriptionStatus.EXPIRED -> "Expired" to PillTone.ERROR
                    SubscriptionStatus.PAUSED -> "Paused" to PillTone.WARNING
                    SubscriptionStatus.CANCELLED -> "Cancelled" to PillTone.NEUTRAL
                }
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.planNameSnapshot, style = MaterialTheme.typography.headlineSmall, color = DeepBlueText)
                            Text(
                                "${TiffinBusinessLogic.formatCurrency(sub.agreedPrice, state.mess.currency)} • ${sub.mealSlotsSnapshot.joinToString(" + ") { it.replaceFirstChar { c -> c.uppercase() } }}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        StatusPill(label = statusLabel, tone = tone)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricValueCard(
                            title = "Included",
                            value = sub.includedTiffins.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        MetricValueCard(
                            title = "Used",
                            value = state.monthSummary.deliveredCount.toString(),
                            accentColor = SkyPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricValueCard(
                            title = "Remaining",
                            value = (state.monthSummary.remainingTiffins ?: 0).toString(),
                            accentColor = SuccessGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Valid: ${TiffinBusinessLogic.formatDateDisplay(sub.startDate)} to ${TiffinBusinessLogic.formatDateDisplay(sub.endDate)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeepBlueText
                    )
                    Spacer(Modifier.height(14.dp))
                    SkyPrimaryButton(
                        text = "Request Renewal",
                        onClick = { showRenewalDialog = true },
                        loading = state.isActionBusy,
                        modifier = Modifier.fillMaxWidth().testTag("request_renewal_button")
                    )
                }
            }
        }

        if (state.renewalRequests.isNotEmpty()) {
            item {
                Text("Renewal Requests", style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
            }
            items(state.renewalRequests, key = { it.id }) { req ->
                val tone = when (req.status) {
                    "approved" -> PillTone.SUCCESS
                    "rejected" -> PillTone.ERROR
                    else -> PillTone.WARNING
                }
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Plan Renewal Request", style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                            if (!req.note.isNullOrBlank()) {
                                Text(req.note, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        StatusPill(label = req.status.replaceFirstChar { it.uppercase() }, tone = tone)
                    }
                }
            }
        }
    }

    if (showRenewalDialog) {
        var selectedPlanId by remember { mutableStateOf(state.availablePlans.firstOrNull()?.id) }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRenewalDialog = false },
            title = { Text("Request Subscription Renewal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Your request will be sent to the mess admin for approval.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    state.availablePlans.forEach { plan ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedPlanId == plan.id,
                                onClick = { selectedPlanId = plan.id }
                            )
                            Column {
                                Text(plan.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${TiffinBusinessLogic.formatCurrency(plan.price, state.mess.currency)} • ${plan.includedTiffins} tiffins / ${plan.durationDays}d",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note for admin (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenewalDialog = false
                        onRequestRenewal(selectedPlanId, note)
                    }
                ) {
                    Text("Submit Request")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenewalDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerPaymentsScreen(state: CustomerUiState) {
    var selectedBill by remember { mutableStateOf<MonthlyBill?>(null) }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Payments & Bills", style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
            Text(
                "Payment records are entered and verified by your mess administrator.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (state.bills.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Text("No Monthly Bills Yet", style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "When your mess owner generates a monthly bill, it will appear here with payment history.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(state.bills, key = { it.id }) { bill ->
                val (label, tone) = when (bill.status) {
                    BillStatus.PAID -> "Paid" to PillTone.SUCCESS
                    BillStatus.PARTIALLY_PAID -> "Partially Paid" to PillTone.WARNING
                    BillStatus.PENDING -> "Pending" to PillTone.INFO
                    BillStatus.OVERDUE -> "Overdue" to PillTone.ERROR
                }
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    strongSurface = true,
                    onClick = { selectedBill = bill }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = TiffinBusinessLogic.formatMonthDisplay(bill.billingMonth),
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepBlueText
                            )
                            Text(
                                text = "Due: ${TiffinBusinessLogic.formatDateDisplay(bill.dueDate)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        StatusPill(label = label, tone = tone)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total: ${TiffinBusinessLogic.formatCurrency(bill.totalAmount, state.mess.currency)}")
                        Text("Paid: ${TiffinBusinessLogic.formatCurrency(bill.amountPaid, state.mess.currency)}", color = SuccessGreen)
                        Text(
                            "Due: ${TiffinBusinessLogic.formatCurrency(bill.balanceDue, state.mess.currency)}",
                            color = if (bill.balanceDue > 0) ErrorCoral else SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (state.payments.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Payment Receipts", style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
            }
            items(state.payments, key = { it.id }) { payment ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = TiffinBusinessLogic.formatCurrency(payment.amount, state.mess.currency),
                                style = MaterialTheme.typography.titleLarge,
                                color = if (payment.status == PaymentStatus.CONFIRMED) SuccessGreen else SecondaryText
                            )
                            Text(
                                text = "${payment.paymentMethod.label} • ${TiffinBusinessLogic.formatTimeInMessZone(payment.paymentDate, state.mess.timezone) ?: payment.paymentDate.take(10)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (!payment.referenceNote.isNullOrBlank()) {
                                Text(payment.referenceNote, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        StatusPill(
                            label = if (payment.status == PaymentStatus.CONFIRMED) "Confirmed" else "Reversed",
                            tone = if (payment.status == PaymentStatus.CONFIRMED) PillTone.SUCCESS else PillTone.NEUTRAL
                        )
                    }
                }
            }
        }
    }

    val bill = selectedBill
    if (bill != null) {
        val billPayments = state.payments.filter { it.billId == bill.id }
        ModalBottomSheet(onDismissRequest = { selectedBill = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = SkyPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Bill Details — ${TiffinBusinessLogic.formatMonthDisplay(bill.billingMonth)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = DeepBlueText
                        )
                    }
                }
                Text("${state.customer.fullName} • ${state.mess.name}", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                BillLine("Subscription Charge", TiffinBusinessLogic.formatCurrency(bill.subscriptionCharge, state.mess.currency))
                BillLine(
                    "Extra Tiffins (${bill.extraTiffinCount})",
                    TiffinBusinessLogic.formatCurrency(bill.extraTiffinCharge, state.mess.currency)
                )
                BillLine("Previous Balance", TiffinBusinessLogic.formatCurrency(bill.previousBalance, state.mess.currency))
                BillLine("Discount", "-${TiffinBusinessLogic.formatCurrency(bill.discountAmount, state.mess.currency)}")
                HorizontalDivider()
                BillLine("Total Payable", TiffinBusinessLogic.formatCurrency(bill.totalAmount, state.mess.currency), bold = true)
                BillLine("Amount Paid", TiffinBusinessLogic.formatCurrency(bill.amountPaid, state.mess.currency), bold = true)
                BillLine("Remaining Balance", TiffinBusinessLogic.formatCurrency(bill.balanceDue, state.mess.currency), bold = true)

                if (billPayments.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Payments for this bill", style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                    billPayments.forEach { p ->
                        Text(
                            "• ${TiffinBusinessLogic.formatCurrency(p.amount, state.mess.currency)} (${p.paymentMethod.label}) — ${p.status.name.lowercase()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                SkyPrimaryButton(
                    text = "Share / Download Bill Summary",
                    icon = Icons.Outlined.Share,
                    onClick = {
                        val summary = buildString {
                            appendLine("Tiffin Mess — Monthly Bill")
                            appendLine("Mess: ${state.mess.name}")
                            appendLine("Customer: ${state.customer.fullName}")
                            appendLine("Month: ${TiffinBusinessLogic.formatMonthDisplay(bill.billingMonth)}")
                            appendLine("Subscription: ${TiffinBusinessLogic.formatCurrency(bill.subscriptionCharge, state.mess.currency)}")
                            appendLine("Extra Tiffins (${bill.extraTiffinCount}): ${TiffinBusinessLogic.formatCurrency(bill.extraTiffinCharge, state.mess.currency)}")
                            appendLine("Previous Balance: ${TiffinBusinessLogic.formatCurrency(bill.previousBalance, state.mess.currency)}")
                            appendLine("Discount: ${TiffinBusinessLogic.formatCurrency(bill.discountAmount, state.mess.currency)}")
                            appendLine("Total Payable: ${TiffinBusinessLogic.formatCurrency(bill.totalAmount, state.mess.currency)}")
                            appendLine("Paid: ${TiffinBusinessLogic.formatCurrency(bill.amountPaid, state.mess.currency)}")
                            appendLine("Balance Due: ${TiffinBusinessLogic.formatCurrency(bill.balanceDue, state.mess.currency)}")
                            appendLine("Status: ${bill.status.name}")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, summary)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Monthly Bill"))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BillLine(label: String, amount: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(amount, style = MaterialTheme.typography.bodyLarge, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, color = DeepBlueText)
    }
}

@Composable
fun CustomerProfileScreen(
    state: CustomerUiState,
    onUpdateProfile: (fullName: String, phone: String, addressOrRoom: String) -> Unit,
    onSignOut: () -> Unit
) {
    var fullName by remember(state.customer.fullName) { mutableStateOf(state.customer.fullName) }
    var phone by remember(state.customer.phone) { mutableStateOf(state.customer.phone) }
    var address by remember(state.customer.addressOrRoom) { mutableStateOf(state.customer.addressOrRoom) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("My Profile", style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(SkyPrimary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!state.profile.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = state.profile.avatarUrl,
                                contentDescription = "Google Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Outlined.AccountCircle, contentDescription = null, tint = SkyPrimary, modifier = Modifier.size(40.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(state.customer.fullName, style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                        Text(state.profile.email, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        StatusPill(label = "Active Customer • ${state.mess.name}", tone = PillTone.SUCCESS)
                    }
                }
            }
        }

        if (!state.errorMessage.isNullOrBlank()) {
            item { ErrorNoticeCard(state.errorMessage) }
        }
        if (!state.successMessage.isNullOrBlank()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    StatusPill(label = state.successMessage, tone = PillTone.SUCCESS)
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                Text("Personal Details", style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Room / Hostel / Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                SkyPrimaryButton(
                    text = "Save Profile",
                    onClick = { onUpdateProfile(fullName, phone, address) },
                    loading = state.isActionBusy,
                    modifier = Modifier.fillMaxWidth().testTag("save_customer_profile_button")
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("customer_logout_button")
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }
        }
    }
}
