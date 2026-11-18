package com.example.ui.admin

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.BillStatus
import com.example.domain.MonthlyBill
import com.example.domain.Payment
import com.example.domain.PaymentMethod
import com.example.domain.PaymentProof
import com.example.domain.PaymentStatus
import com.example.domain.SubscriptionPlan
import com.example.domain.SubscriptionStatus
import com.example.domain.TiffinBusinessLogic
import com.example.ui.auth.ErrorNoticeCard
import com.example.ui.theme.DeepBlueText
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GlassCard
import com.example.ui.theme.PillTone
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SkyPrimary
import com.example.ui.theme.SkyPrimaryButton
import com.example.ui.theme.SkySecondary
import com.example.ui.theme.StatusPill
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.lStr
import com.example.ui.theme.localizedDate
import com.example.ui.theme.localizedLabel
import com.example.ui.theme.localizedMonth
import com.example.ui.theme.localizedMonthTitle
import com.example.ui.theme.localizedTime
import java.time.YearMonth

@Composable
fun AdminSubscriptionsScreen(
    state: AdminUiState,
    onBack: () -> Unit,
    onCreatePlan: (name: String, days: Int, price: Double, tiffins: Int, slots: List<String>, extraPrice: Double) -> Unit,
    onAssignSubscription: (
        customerId: String,
        plan: SubscriptionPlan?,
        customName: String,
        startDateIso: String,
        endDateIso: String,
        includedTiffins: Int,
        extraTiffinPrice: Double,
        mealSlots: List<String>,
        agreedPrice: Double
    ) -> Unit,
    onUpdateSubscriptionStatus: (subscriptionId: String, status: SubscriptionStatus) -> Unit,
    onReviewRenewalRequest: (requestId: String, approve: Boolean) -> Unit
) {
    var showCreatePlan by remember { mutableStateOf(false) }
    var showAssignSub by remember { mutableStateOf(false) }
    val customerNames = remember(state.customers) { state.customers.associateBy({ it.id }, { it.fullName }) }
    val customerFallback = lStr(R.string.customer_fallback)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = lStr(R.string.action_back), tint = DeepBlueText)
                }
                Column {
                    Text(lStr(R.string.plans_subs_title), style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
                    Text(lStr(R.string.plans_subs_sub), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                }
            }
        }

        if (!state.errorMessage.isNullOrBlank()) item { ErrorNoticeCard(state.errorMessage) }
        if (!state.successMessage.isNullOrBlank()) {
            item { GlassCard(modifier = Modifier.fillMaxWidth()) { StatusPill(state.successMessage, PillTone.SUCCESS) } }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { showCreatePlan = true },
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(lStr(R.string.new_plan_btn))
                }
                SkyPrimaryButton(
                    text = lStr(R.string.assign_plan_btn),
                    onClick = { showAssignSub = true },
                    modifier = Modifier.weight(1f).testTag("assign_subscription_button")
                )
            }
        }

        if (state.renewalRequests.isNotEmpty()) {
            item { Text(lStr(R.string.pending_renewals_title), style = MaterialTheme.typography.titleLarge, color = DeepBlueText) }
            items(state.renewalRequests, key = { it.id }) { req ->
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Text(customerNames[req.customerId] ?: customerFallback, style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                    if (!req.note.isNullOrBlank()) Text(req.note, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkyPrimaryButton(lStr(R.string.approve_btn), onClick = { onReviewRenewalRequest(req.id, true) }, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { onReviewRenewalRequest(req.id, false) }, modifier = Modifier.weight(1f).height(50.dp)) {
                            Text(lStr(R.string.decline_btn))
                        }
                    }
                }
            }
        }

        if (state.plans.isNotEmpty()) {
            item { Text(lStr(R.string.mess_plans_count, state.plans.size), style = MaterialTheme.typography.titleLarge, color = DeepBlueText) }
            items(state.plans, key = { it.id }) { plan ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(plan.name, style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                            Text(
                                text = lStr(
                                    R.string.plan_line_details,
                                    plan.includedTiffins,
                                    plan.durationDays,
                                    TiffinBusinessLogic.formatCurrency(plan.extraTiffinPrice, state.mess.currency)
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(TiffinBusinessLogic.formatCurrency(plan.price, state.mess.currency), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    }
                }
            }
        }

        item { Text(lStr(R.string.customer_subs_count, state.subscriptions.size), style = MaterialTheme.typography.titleLarge, color = DeepBlueText) }
        items(state.subscriptions, key = { it.id }) { sub ->
            val tone = when (sub.status) {
                SubscriptionStatus.ACTIVE -> PillTone.SUCCESS
                SubscriptionStatus.EXPIRING_SOON, SubscriptionStatus.PAUSED -> PillTone.WARNING
                SubscriptionStatus.EXPIRED -> PillTone.ERROR
                SubscriptionStatus.CANCELLED -> PillTone.NEUTRAL
            }
            GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(customerNames[sub.customerId] ?: customerFallback, style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                        Text("${sub.planNameSnapshot} • ${TiffinBusinessLogic.formatCurrency(sub.agreedPrice, state.mess.currency)}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = lStr(
                                R.string.sub_dates_tiffins,
                                localizedDate(sub.startDate),
                                localizedDate(sub.endDate),
                                sub.includedTiffins
                            ),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    StatusPill(sub.status.localizedLabel(), tone)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (sub.status == SubscriptionStatus.PAUSED) {
                        OutlinedButton(onClick = { onUpdateSubscriptionStatus(sub.id, SubscriptionStatus.ACTIVE) }, modifier = Modifier.weight(1f)) {
                            Text(lStr(R.string.resume_btn))
                        }
                    } else if (sub.status == SubscriptionStatus.ACTIVE) {
                        OutlinedButton(onClick = { onUpdateSubscriptionStatus(sub.id, SubscriptionStatus.PAUSED) }, modifier = Modifier.weight(1f)) {
                            Text(lStr(R.string.pause_btn))
                        }
                    }
                    if (sub.status != SubscriptionStatus.EXPIRED) {
                        OutlinedButton(onClick = { onUpdateSubscriptionStatus(sub.id, SubscriptionStatus.EXPIRED) }, modifier = Modifier.weight(1f)) {
                            Text(lStr(R.string.expire_btn))
                        }
                    }
                    if (sub.status != SubscriptionStatus.CANCELLED) {
                        OutlinedButton(onClick = { onUpdateSubscriptionStatus(sub.id, SubscriptionStatus.CANCELLED) }, modifier = Modifier.weight(1f)) {
                            Text(lStr(R.string.action_cancel))
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlan) {
        var name by remember { mutableStateOf("") }
        var days by remember { mutableStateOf("30") }
        var price by remember { mutableStateOf("3000") }
        var tiffins by remember { mutableStateOf("60") }
        var extraPrice by remember { mutableStateOf("60") }
        val defaultPlanName = lStr(R.string.custom_monthly_plan_default)
        AlertDialog(
            onDismissRequest = { showCreatePlan = false },
            title = { Text(lStr(R.string.create_plan_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(lStr(R.string.plan_name_hint)) })
                    OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text(lStr(R.string.duration_days_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text(lStr(R.string.price_currency_label, state.mess.currency)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = tiffins, onValueChange = { tiffins = it }, label = { Text(lStr(R.string.included_tiffins_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = extraPrice, onValueChange = { extraPrice = it }, label = { Text(lStr(R.string.extra_tiffin_price_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCreatePlan = false
                    onCreatePlan(
                        name.ifBlank { defaultPlanName },
                        days.toIntOrNull() ?: 30,
                        price.toDoubleOrNull() ?: 0.0,
                        tiffins.toIntOrNull() ?: 30,
                        state.mess.mealSlots,
                        extraPrice.toDoubleOrNull() ?: 0.0
                    )
                }) { Text(lStr(R.string.create_btn)) }
            },
            dismissButton = { TextButton(onClick = { showCreatePlan = false }) { Text(lStr(R.string.action_cancel)) } }
        )
    }

    if (showAssignSub) {
        val defaultCustomName = lStr(R.string.custom_monthly_plan_default)
        var selectedCustId by remember { mutableStateOf(state.activeCustomers.firstOrNull()?.id) }
        var selectedPlan by remember { mutableStateOf(state.plans.firstOrNull()) }
        var customName by remember(defaultCustomName) { mutableStateOf(defaultCustomName) }
        var price by remember(selectedPlan) { mutableStateOf((selectedPlan?.price ?: 3000.0).toString()) }
        var tiffins by remember(selectedPlan) { mutableStateOf((selectedPlan?.includedTiffins ?: 30).toString()) }
        val today = TiffinBusinessLogic.messToday(state.mess.timezone)
        var startIso by remember { mutableStateOf(today.toString()) }
        var endIso by remember(selectedPlan) {
            mutableStateOf(today.plusDays((selectedPlan?.durationDays ?: 30).toLong() - 1).toString())
        }

        AlertDialog(
            onDismissRequest = { showAssignSub = false },
            title = { Text(lStr(R.string.assign_sub_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(customerFallback, style = MaterialTheme.typography.labelMedium)
                    state.activeCustomers.take(5).forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedCustId == c.id, onClick = { selectedCustId = c.id })
                            Text(c.fullName)
                        }
                    }
                    if (state.plans.isNotEmpty()) {
                        Text(lStr(R.string.plan_label), style = MaterialTheme.typography.labelMedium)
                        state.plans.take(4).forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selectedPlan?.id == p.id, onClick = { selectedPlan = p })
                                Text("${p.name} (${TiffinBusinessLogic.formatCurrency(p.price, state.mess.currency)})")
                            }
                        }
                    } else {
                        OutlinedTextField(value = customName, onValueChange = { customName = it }, label = { Text(lStr(R.string.custom_plan_name_label)) })
                    }
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text(lStr(R.string.agreed_price_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = tiffins, onValueChange = { tiffins = it }, label = { Text(lStr(R.string.total_allowance_tiffins)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = startIso, onValueChange = { startIso = it }, label = { Text(lStr(R.string.field_start_date)) })
                    OutlinedTextField(value = endIso, onValueChange = { endIso = it }, label = { Text(lStr(R.string.field_end_date)) })
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedCustId != null,
                    onClick = {
                        val cid = selectedCustId ?: return@TextButton
                        showAssignSub = false
                        onAssignSubscription(
                            cid,
                            selectedPlan,
                            customName,
                            startIso,
                            endIso,
                            tiffins.toIntOrNull() ?: 30,
                            selectedPlan?.extraTiffinPrice ?: 0.0,
                            selectedPlan?.mealSlots ?: state.mess.mealSlots,
                            price.toDoubleOrNull() ?: 0.0
                        )
                    }
                ) { Text(lStr(R.string.assign_btn)) }
            },
            dismissButton = { TextButton(onClick = { showAssignSub = false }) { Text(lStr(R.string.action_cancel)) } }
        )
    }
}

@Composable
fun AdminBillingScreen(
    state: AdminUiState,
    onSelectMonth: (YearMonth) -> Unit,
    onGenerateBill: (customerId: String, monthIso: String, discount: Double, previousBalance: Double) -> Unit,
    onRecordPayment: (billId: String, amount: Double, method: PaymentMethod, refNote: String?) -> Unit,
    onReversePayment: (paymentId: String, reason: String) -> Unit,
    onConfirmPaymentProof: (proofId: String) -> Unit = {},
    onRejectPaymentProof: (proofId: String, reason: String?) -> Unit = { _, _ -> }
) {
    var statusFilter by remember { mutableStateOf("ALL") }
    var showGenerateDialog by remember { mutableStateOf(false) }
    var paymentTargetBill by remember { mutableStateOf<MonthlyBill?>(null) }
    var reverseTargetPayment by remember { mutableStateOf<Payment?>(null) }
    var rejectTargetProof by remember { mutableStateOf<PaymentProof?>(null) }
    var fullscreenProofImageUrl by remember { mutableStateOf<String?>(null) }

    val customerNames = remember(state.customers) { state.customers.associateBy({ it.id }, { it.fullName }) }
    val billById = remember(state.bills) { state.bills.associateBy { it.id } }
    val customerFallback = lStr(R.string.customer_fallback)
    val monthTitle = localizedMonthTitle(state.selectedMonth)
    val monthIso = TiffinBusinessLogic.monthStartIso(state.selectedMonth)

    val filteredBills = remember(state.monthBills, statusFilter) {
        state.monthBills.filter { b ->
            when (statusFilter) {
                "PENDING" -> b.status == BillStatus.PENDING || b.status == BillStatus.PARTIALLY_PAID
                "OVERDUE" -> b.status == BillStatus.OVERDUE
                "PAID" -> b.status == BillStatus.PAID
                else -> true
            }
        }
    }

    val filterOptions = listOf(
        "ALL" to lStr(R.string.filter_all),
        "PENDING" to lStr(R.string.bill_pending),
        "OVERDUE" to lStr(R.string.bill_overdue),
        "PAID" to lStr(R.string.bill_paid)
    )
    val activeFilterLabel = filterOptions.firstOrNull { it.first == statusFilter }?.second ?: lStr(R.string.filter_all)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(lStr(R.string.bills_payments_title), style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
            Text(lStr(R.string.admin_billing_sub), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }

        if (!state.errorMessage.isNullOrBlank()) item { ErrorNoticeCard(state.errorMessage) }
        if (!state.successMessage.isNullOrBlank()) {
            item { GlassCard(modifier = Modifier.fillMaxWidth()) { StatusPill(state.successMessage, PillTone.SUCCESS) } }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_payment_proofs_section"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lStr(R.string.payment_proofs_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = DeepBlueText
                )
                StatusPill(
                    label = lStr(R.string.payment_proofs_pending_count, state.pendingPaymentProofs.size),
                    tone = if (state.pendingPaymentProofs.isNotEmpty()) PillTone.WARNING else PillTone.NEUTRAL
                )
            }
        }

        if (state.pendingPaymentProofs.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = lStr(R.string.no_pending_proofs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }
        } else {
            items(state.pendingPaymentProofs, key = { "proof_${it.id}" }) { proof ->
                val linkedBill = proof.billId?.let { billById[it] }
                val billMonthLabel = linkedBill?.billingMonth?.let {
                    lStr(R.string.bill_month_label, localizedMonth(it))
                }
                val dateLabel = proof.createdAt?.let { raw ->
                    val d = localizedDate(raw.take(10))
                    val t = localizedTime(raw, state.mess.timezone)
                    if (t != null) "$d • $t" else d
                } ?: ""
                val signedImageUrl = state.proofSignedUrls[proof.screenshotPath]

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_proof_card_${proof.id}"),
                    strongSurface = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = customerNames[proof.customerId] ?: customerFallback,
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepBlueText
                            )
                            if (!billMonthLabel.isNullOrBlank()) {
                                Text(
                                    text = billMonthLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SecondaryText
                                )
                            }
                            if (dateLabel.isNotBlank()) {
                                Text(
                                    text = lStr(R.string.submitted_at_label, dateLabel),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SecondaryText
                                )
                            }
                        }
                        Text(
                            text = TiffinBusinessLogic.formatCurrency(proof.amount, state.mess.currency),
                            style = MaterialTheme.typography.headlineSmall,
                            color = DeepBlueText,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!signedImageUrl.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, SkySecondary.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clickable { fullscreenProofImageUrl = signedImageUrl }
                                .testTag("proof_image_${proof.id}")
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = signedImageUrl,
                                    contentDescription = lStr(R.string.payment_screenshot_cd),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.58f),
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ZoomIn,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = lStr(R.string.tap_to_view_full),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SkyPrimaryButton(
                            text = lStr(R.string.confirm_payment_btn),
                            icon = Icons.Outlined.CheckCircle,
                            onClick = { onConfirmPaymentProof(proof.id) },
                            loading = state.isActionBusy,
                            modifier = Modifier
                                .weight(1.25f)
                                .testTag("confirm_proof_${proof.id}")
                        )
                        OutlinedButton(
                            onClick = { rejectTargetProof = proof },
                            enabled = !state.isActionBusy,
                            modifier = Modifier
                                .weight(0.85f)
                                .height(50.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("reject_proof_${proof.id}")
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(lStr(R.string.reject_proof_btn))
                        }
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSelectMonth(state.selectedMonth.minusMonths(1)) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = lStr(R.string.action_prev_month))
                    }
                    Text(monthTitle, style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    IconButton(onClick = { onSelectMonth(state.selectedMonth.plusMonths(1)) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = lStr(R.string.action_next_month))
                    }
                }
                Spacer(Modifier.height(8.dp))
                SkyPrimaryButton(
                    text = lStr(R.string.make_monthly_bill_btn),
                    icon = Icons.Outlined.ReceiptLong,
                    onClick = { showGenerateDialog = true },
                    loading = state.isActionBusy,
                    modifier = Modifier.fillMaxWidth().testTag("generate_bill_button")
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filterOptions.forEach { (key, label) ->
                    FilterChip(selected = statusFilter == key, onClick = { statusFilter = key }, label = { Text(label) })
                }
            }
        }

        if (filteredBills.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(lStr(R.string.no_bills_under_filter, monthTitle, activeFilterLabel), style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                }
            }
        } else {
            items(filteredBills, key = { it.id }) { bill ->
                val tone = when (bill.status) {
                    BillStatus.PAID -> PillTone.SUCCESS
                    BillStatus.PARTIALLY_PAID -> PillTone.WARNING
                    BillStatus.PENDING -> PillTone.INFO
                    BillStatus.OVERDUE -> PillTone.ERROR
                }
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(customerNames[bill.customerId] ?: customerFallback, style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                            Text(lStr(R.string.due_colon, localizedDate(bill.dueDate)), style = MaterialTheme.typography.labelMedium)
                        }
                        StatusPill(bill.status.localizedLabel(), tone)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(lStr(R.string.total_colon, TiffinBusinessLogic.formatCurrency(bill.totalAmount, state.mess.currency)))
                        Text(lStr(R.string.paid_colon, TiffinBusinessLogic.formatCurrency(bill.amountPaid, state.mess.currency)), color = SuccessGreen)
                        Text(
                            text = lStr(R.string.due_colon, TiffinBusinessLogic.formatCurrency(bill.balanceDue, state.mess.currency)),
                            color = if (bill.balanceDue > 0) ErrorCoral else SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (bill.balanceDue > 0) {
                        Spacer(Modifier.height(10.dp))
                        SkyPrimaryButton(
                            text = lStr(R.string.save_payment_btn),
                            icon = Icons.Outlined.Payments,
                            onClick = { paymentTargetBill = bill },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (state.payments.isNotEmpty()) {
            item { Text(lStr(R.string.payment_history_title), style = MaterialTheme.typography.titleLarge, color = DeepBlueText) }
            items(state.payments, key = { it.id }) { pay ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${customerNames[pay.customerId] ?: customerFallback} • ${TiffinBusinessLogic.formatCurrency(pay.amount, state.mess.currency)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (pay.status == PaymentStatus.CONFIRMED) DeepBlueText else SecondaryText
                            )
                            Text("${pay.paymentMethod.localizedLabel()} • ${pay.referenceNote ?: lStr(R.string.no_note)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (pay.status == PaymentStatus.CONFIRMED) {
                            OutlinedButton(onClick = { reverseTargetPayment = pay }) { Text(lStr(R.string.undo_btn)) }
                        } else {
                            StatusPill(lStr(R.string.tiffin_cancelled), PillTone.NEUTRAL)
                        }
                    }
                }
            }
        }
    }

    if (showGenerateDialog) {
        var selectedCustId by remember { mutableStateOf(state.activeCustomers.firstOrNull()?.id) }
        var discount by remember { mutableStateOf("0") }
        var prevBal by remember { mutableStateOf("0") }
        AlertDialog(
            onDismissRequest = { showGenerateDialog = false },
            title = { Text(lStr(R.string.generate_bill_title, monthTitle)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.activeCustomers.take(6).forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedCustId == c.id, onClick = { selectedCustId = c.id })
                            Text(c.fullName)
                        }
                    }
                    OutlinedTextField(value = discount, onValueChange = { discount = it }, label = { Text(lStr(R.string.discount_label, state.mess.currency)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = prevBal, onValueChange = { prevBal = it }, label = { Text(lStr(R.string.prev_unpaid_balance)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedCustId != null,
                    onClick = {
                        val cid = selectedCustId ?: return@TextButton
                        showGenerateDialog = false
                        onGenerateBill(cid, monthIso, discount.toDoubleOrNull() ?: 0.0, prevBal.toDoubleOrNull() ?: 0.0)
                    }
                ) { Text(lStr(R.string.generate_btn)) }
            },
            dismissButton = { TextButton(onClick = { showGenerateDialog = false }) { Text(lStr(R.string.action_cancel)) } }
        )
    }

    val bill = paymentTargetBill
    if (bill != null) {
        var amount by remember(bill.id) { mutableStateOf(bill.balanceDue.toString()) }
        var method by remember { mutableStateOf(PaymentMethod.CASH) }
        var refNote by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { paymentTargetBill = null },
            title = { Text(lStr(R.string.save_payment_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(lStr(R.string.save_payment_hint), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(lStr(R.string.amount_label, state.mess.currency)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaymentMethod.entries.forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m.localizedLabel()) })
                        }
                    }
                    OutlinedTextField(value = refNote, onValueChange = { refNote = it }, label = { Text(lStr(R.string.notes_opt_label)) })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    paymentTargetBill = null
                    onRecordPayment(bill.id, amt, method, refNote)
                }) { Text(lStr(R.string.save_payment_btn)) }
            },
            dismissButton = { TextButton(onClick = { paymentTargetBill = null }) { Text(lStr(R.string.action_cancel)) } }
        )
    }

    val revPay = reverseTargetPayment
    if (revPay != null) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reverseTargetPayment = null },
            title = { Text(lStr(R.string.undo_payment_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(lStr(R.string.undo_payment_desc), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text(lStr(R.string.reason_opt)) })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    reverseTargetPayment = null
                    onReversePayment(revPay.id, reason)
                }) { Text(lStr(R.string.confirm_undo_btn)) }
            },
            dismissButton = { TextButton(onClick = { reverseTargetPayment = null }) { Text(lStr(R.string.action_cancel)) } }
        )
    }

    val proofToReject = rejectTargetProof
    if (proofToReject != null) {
        var rejectReason by remember(proofToReject.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectTargetProof = null },
            title = { Text(lStr(R.string.reject_proof_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${customerNames[proofToReject.customerId] ?: customerFallback} • ${
                            TiffinBusinessLogic.formatCurrency(proofToReject.amount, state.mess.currency)
                        }",
                        style = MaterialTheme.typography.titleMedium,
                        color = DeepBlueText
                    )
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text(lStr(R.string.reject_proof_reason_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reject_proof_reason_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rejectTargetProof = null
                        onRejectPaymentProof(proofToReject.id, rejectReason)
                    },
                    modifier = Modifier.testTag("confirm_reject_proof_button")
                ) {
                    Text(lStr(R.string.confirm_reject_btn), color = ErrorCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectTargetProof = null }) {
                    Text(lStr(R.string.action_cancel))
                }
            }
        )
    }

    val fullUrl = fullscreenProofImageUrl
    if (!fullUrl.isNullOrBlank()) {
        Dialog(onDismissRequest = { fullscreenProofImageUrl = null }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = lStr(R.string.payment_screenshot_cd),
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepBlueText
                    )
                    Spacer(Modifier.height(10.dp))
                    AsyncImage(
                        model = fullUrl,
                        contentDescription = lStr(R.string.payment_screenshot_cd),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.04f))
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { fullscreenProofImageUrl = null },
                        modifier = Modifier.testTag("close_proof_preview_button")
                    ) {
                        Text(lStr(R.string.close_preview_btn))
                    }
                }
            }
        }
    }
}
