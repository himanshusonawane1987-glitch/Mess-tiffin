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
import com.example.R
import com.example.domain.AppLanguage
import com.example.domain.BillStatus
import com.example.domain.MonthlyBill
import com.example.domain.PaymentStatus
import com.example.domain.SubscriptionStatus
import com.example.domain.TiffinBusinessLogic
import com.example.ui.auth.ErrorNoticeCard
import com.example.ui.theme.DeepBlueText
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GlassCard
import com.example.ui.theme.LanguageSettingsCard
import com.example.ui.theme.MetricValueCard
import com.example.ui.theme.PillTone
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SkyPrimary
import com.example.ui.theme.SkyPrimaryButton
import com.example.ui.theme.StatusPill
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.lStr
import com.example.ui.theme.localizedDate
import com.example.ui.theme.localizedLabel
import com.example.ui.theme.localizedMealSlot
import com.example.ui.theme.localizedMonth
import com.example.ui.theme.localizedStatusText
import com.example.ui.theme.localizedTime

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
            Text(lStr(R.string.my_plan_title), style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
            Text(lStr(R.string.my_plan_sub), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
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
                    Text(lStr(R.string.no_active_sub_title), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = lStr(R.string.no_active_sub_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    SkyPrimaryButton(
                        text = lStr(R.string.request_plan_renewal_btn),
                        onClick = { showRenewalDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("request_renewal_button")
                    )
                }
            } else {
                val tone = when (sub.status) {
                    SubscriptionStatus.ACTIVE -> PillTone.SUCCESS
                    SubscriptionStatus.EXPIRING_SOON, SubscriptionStatus.PAUSED -> PillTone.WARNING
                    SubscriptionStatus.EXPIRED -> PillTone.ERROR
                    SubscriptionStatus.CANCELLED -> PillTone.NEUTRAL
                }
                val localizedSlots = sub.mealSlotsSnapshot.map { localizedMealSlot(it) }.joinToString(" + ")
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.planNameSnapshot, style = MaterialTheme.typography.headlineSmall, color = DeepBlueText)
                            Text(
                                text = "${TiffinBusinessLogic.formatCurrency(sub.agreedPrice, state.mess.currency)} • $localizedSlots",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        StatusPill(label = sub.status.localizedLabel(), tone = tone)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricValueCard(
                            title = lStr(R.string.metric_included),
                            value = sub.includedTiffins.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        MetricValueCard(
                            title = lStr(R.string.metric_used),
                            value = state.monthSummary.deliveredCount.toString(),
                            accentColor = SkyPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricValueCard(
                            title = lStr(R.string.metric_remaining),
                            value = (state.monthSummary.remainingTiffins ?: 0).toString(),
                            accentColor = SuccessGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = lStr(R.string.valid_dates, localizedDate(sub.startDate), localizedDate(sub.endDate)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeepBlueText
                    )
                    Spacer(Modifier.height(14.dp))
                    SkyPrimaryButton(
                        text = lStr(R.string.request_renewal_btn),
                        onClick = { showRenewalDialog = true },
                        loading = state.isActionBusy,
                        modifier = Modifier.fillMaxWidth().testTag("request_renewal_button")
                    )
                }
            }
        }

        if (state.renewalRequests.isNotEmpty()) {
            item {
                Text(lStr(R.string.renewal_requests_title), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
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
                            Text(lStr(R.string.plan_renewal_request_item), style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                            if (!req.note.isNullOrBlank()) {
                                Text(req.note, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        StatusPill(label = localizedStatusText(req.status), tone = tone)
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
            title = { Text(lStr(R.string.renewal_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = lStr(R.string.renewal_dialog_sub),
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
                                    text = lStr(
                                        R.string.plan_summary_line,
                                        TiffinBusinessLogic.formatCurrency(plan.price, state.mess.currency),
                                        plan.includedTiffins,
                                        plan.durationDays
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(lStr(R.string.note_for_admin_opt)) },
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
                    Text(lStr(R.string.submit_request_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenewalDialog = false }) { Text(lStr(R.string.action_cancel)) }
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
            Text(lStr(R.string.bills_payments_title), style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
            Text(
                text = lStr(R.string.bills_payments_cust_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText
            )
        }

        if (state.bills.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Text(lStr(R.string.no_bills_yet_title), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = lStr(R.string.no_bills_yet_sub),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(state.bills, key = { it.id }) { bill ->
                val tone = when (bill.status) {
                    BillStatus.PAID -> PillTone.SUCCESS
                    BillStatus.PARTIALLY_PAID -> PillTone.WARNING
                    BillStatus.PENDING -> PillTone.INFO
                    BillStatus.OVERDUE -> PillTone.ERROR
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
                                text = localizedMonth(bill.billingMonth),
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepBlueText
                            )
                            Text(
                                text = lStr(R.string.due_colon, localizedDate(bill.dueDate)),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        StatusPill(label = bill.status.localizedLabel(), tone = tone)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(lStr(R.string.total_colon, TiffinBusinessLogic.formatCurrency(bill.totalAmount, state.mess.currency)))
                        Text(lStr(R.string.paid_colon, TiffinBusinessLogic.formatCurrency(bill.amountPaid, state.mess.currency)), color = SuccessGreen)
                        Text(
                            text = lStr(R.string.due_colon, TiffinBusinessLogic.formatCurrency(bill.balanceDue, state.mess.currency)),
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
                Text(lStr(R.string.payment_receipts_title), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
            }
            items(state.payments, key = { it.id }) { payment ->
                val dateText = localizedTime(payment.paymentDate, state.mess.timezone) ?: payment.paymentDate.take(10)
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
                                text = "${payment.paymentMethod.localizedLabel()} • $dateText",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (!payment.referenceNote.isNullOrBlank()) {
                                Text(payment.referenceNote, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        StatusPill(
                            label = if (payment.status == PaymentStatus.CONFIRMED) lStr(R.string.payment_confirmed) else lStr(R.string.payment_reversed),
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
        val monthDisplay = localizedMonth(bill.billingMonth)
        val shareChooser = lStr(R.string.share_bill_chooser)
        val shareSummary = lStr(
            R.string.share_bill_text,
            state.mess.name,
            state.customer.fullName,
            monthDisplay,
            TiffinBusinessLogic.formatCurrency(bill.subscriptionCharge, state.mess.currency),
            bill.extraTiffinCount,
            TiffinBusinessLogic.formatCurrency(bill.extraTiffinCharge, state.mess.currency),
            TiffinBusinessLogic.formatCurrency(bill.previousBalance, state.mess.currency),
            TiffinBusinessLogic.formatCurrency(bill.discountAmount, state.mess.currency),
            TiffinBusinessLogic.formatCurrency(bill.totalAmount, state.mess.currency),
            TiffinBusinessLogic.formatCurrency(bill.amountPaid, state.mess.currency),
            TiffinBusinessLogic.formatCurrency(bill.balanceDue, state.mess.currency),
            bill.status.localizedLabel()
        )
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
                            text = lStr(R.string.bill_details_title, monthDisplay),
                            style = MaterialTheme.typography.titleLarge,
                            color = DeepBlueText
                        )
                    }
                }
                Text("${state.customer.fullName} • ${state.mess.name}", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                BillLine(lStr(R.string.bill_sub_charge), TiffinBusinessLogic.formatCurrency(bill.subscriptionCharge, state.mess.currency))
                BillLine(
                    lStr(R.string.bill_extra_tiffins, bill.extraTiffinCount),
                    TiffinBusinessLogic.formatCurrency(bill.extraTiffinCharge, state.mess.currency)
                )
                BillLine(lStr(R.string.bill_prev_balance), TiffinBusinessLogic.formatCurrency(bill.previousBalance, state.mess.currency))
                BillLine(lStr(R.string.bill_discount), "-${TiffinBusinessLogic.formatCurrency(bill.discountAmount, state.mess.currency)}")
                HorizontalDivider()
                BillLine(lStr(R.string.bill_total_payable), TiffinBusinessLogic.formatCurrency(bill.totalAmount, state.mess.currency), bold = true)
                BillLine(lStr(R.string.bill_amount_paid), TiffinBusinessLogic.formatCurrency(bill.amountPaid, state.mess.currency), bold = true)
                BillLine(lStr(R.string.bill_remaining_balance), TiffinBusinessLogic.formatCurrency(bill.balanceDue, state.mess.currency), bold = true)

                if (billPayments.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(lStr(R.string.bill_payments_list_title), style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                    billPayments.forEach { p ->
                        val pStatusLabel = if (p.status == PaymentStatus.CONFIRMED) lStr(R.string.payment_confirmed) else lStr(R.string.payment_reversed)
                        Text(
                            text = "• ${TiffinBusinessLogic.formatCurrency(p.amount, state.mess.currency)} (${p.paymentMethod.localizedLabel()}) — $pStatusLabel",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                SkyPrimaryButton(
                    text = lStr(R.string.share_bill_btn),
                    icon = Icons.Outlined.Share,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareSummary)
                        }
                        context.startActivity(Intent.createChooser(intent, shareChooser))
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
    currentLanguage: AppLanguage = AppLanguage.DEFAULT,
    onSelectLanguage: (AppLanguage) -> Unit = {},
    onUpdateProfile: (fullName: String, phone: String, addressOrRoom: String, area: String, city: String) -> Unit,
    onSignOut: () -> Unit
) {
    var fullName by remember(state.customer.fullName) { mutableStateOf(state.customer.fullName) }
    var phone by remember(state.customer.phone) { mutableStateOf(state.customer.phone) }
    var area by remember(state.profile.area) { mutableStateOf(state.profile.area) }
    var city by remember(state.profile.city) { mutableStateOf(state.profile.city) }
    var address by remember(state.customer.addressOrRoom) { mutableStateOf(state.customer.addressOrRoom) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(lStr(R.string.my_profile_title), style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
            Text(lStr(R.string.my_profile_sub), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
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
                                contentDescription = lStr(R.string.profile_photo_cd),
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
                        StatusPill(label = lStr(R.string.active_customer_pill, state.mess.name), tone = PillTone.SUCCESS)
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
                Text(lStr(R.string.personal_details_title), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text(lStr(R.string.field_full_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(lStr(R.string.field_mobile)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = area,
                        onValueChange = { area = it },
                        label = { Text(lStr(R.string.field_area_req)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("customer_profile_area_input")
                    )
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text(lStr(R.string.field_city_req)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("customer_profile_city_input")
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(lStr(R.string.field_room_hostel)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                SkyPrimaryButton(
                    text = lStr(R.string.save_profile_btn),
                    onClick = { onUpdateProfile(fullName, phone, address, area, city) },
                    loading = state.isActionBusy,
                    modifier = Modifier.fillMaxWidth().testTag("save_customer_profile_button")
                )
            }
        }

        item {
            LanguageSettingsCard(
                selectedLanguage = currentLanguage,
                onSelectLanguage = onSelectLanguage
            )
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("customer_logout_button")
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(lStr(R.string.action_sign_out))
            }
        }
    }
}
