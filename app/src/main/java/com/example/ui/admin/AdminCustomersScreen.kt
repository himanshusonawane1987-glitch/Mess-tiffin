package com.example.ui.admin

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AccountStatus
import com.example.domain.CustomerProfile
import com.example.domain.SubscriptionStatus
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinStatus
import com.example.ui.auth.ErrorNoticeCard
import com.example.ui.theme.DeepBlueText
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GlassCard
import com.example.ui.theme.PillTone
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SkyPrimary
import com.example.ui.theme.SkyPrimaryButton
import com.example.ui.theme.StatusPill
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.lStr
import com.example.ui.theme.localizedLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCustomersScreen(
    state: AdminUiState,
    onCreateCustomer: (name: String, phone: String, email: String?, address: String) -> Unit,
    onReviewRequest: (requestId: String, approve: Boolean) -> Unit,
    onUpdateCustomerDetails: (customerId: String, name: String, phone: String, address: String) -> Unit,
    onUpdateCustomerStatus: (customerId: String, status: String) -> Unit,
    onOpenBillingForCustomer: () -> Unit,
    onOpenSubscriptionsForCustomer: () -> Unit,
    onCreateLeave: (customerId: String, startDateIso: String, endDateIso: String, reason: String) -> Unit = { _, _, _, _ -> },
    onCancelLeave: (leaveId: String) -> Unit = {}
) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var sortByDue by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCustomer by remember { mutableStateOf<CustomerProfile?>(null) }

    val dueByCustomer = remember(state.bills) {
        state.bills.groupBy { it.customerId }.mapValues { (_, list) -> list.sumOf { it.balanceDue } }
    }
    val subByCustomer = remember(state.subscriptions) {
        state.subscriptions.groupBy { it.customerId }.mapValues { (_, list) -> list.firstOrNull() }
    }
    val todayIso = TiffinBusinessLogic.messToday(state.mess.timezone).toString()

    val filteredCustomers = remember(state.customers, search, filter, sortByDue, dueByCustomer, subByCustomer) {
        state.customers
            .filter { c ->
                val matchesQuery = search.isBlank() ||
                    c.fullName.contains(search, ignoreCase = true) ||
                    c.phone.contains(search, ignoreCase = true)
                val sub = subByCustomer[c.id]
                val matchesFilter = when (filter) {
                    "ACTIVE" -> c.customerStatus == AccountStatus.ACTIVE
                    "EXPIRED" -> sub?.status == SubscriptionStatus.EXPIRED
                    "SUSPENDED" -> c.customerStatus == AccountStatus.SUSPENDED
                    else -> true
                }
                matchesQuery && matchesFilter
            }
            .let { list ->
                if (sortByDue) list.sortedByDescending { dueByCustomer[it.id] ?: 0.0 }
                else list.sortedBy { it.fullName.lowercase() }
            }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = SkyPrimary,
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = 84.dp).testTag("add_customer_fab")
            ) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = lStr(R.string.add_customer_cd))
            }
        }
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(lStr(R.string.qa_customers), style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
                Text(lStr(R.string.customers_sub, state.customers.size), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
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
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(lStr(R.string.search_customers_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_search_input")
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to lStr(R.string.filter_all),
                        "ACTIVE" to lStr(R.string.status_active),
                        "PENDING" to lStr(R.string.status_pending),
                        "EXPIRED" to lStr(R.string.sub_expired)
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = filter == key,
                            onClick = { filter = key },
                            label = { Text(label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !sortByDue,
                        onClick = { sortByDue = false },
                        label = { Text(lStr(R.string.sort_name)) }
                    )
                    FilterChip(
                        selected = sortByDue,
                        onClick = { sortByDue = true },
                        label = { Text(lStr(R.string.sort_balance_due)) }
                    )
                }
            }

            if (filter == "ALL" || filter == "PENDING") {
                if (state.pendingRequests.isNotEmpty()) {
                    item {
                        Text(
                            text = lStr(R.string.new_join_requests_title, state.pendingRequests.size),
                            style = MaterialTheme.typography.titleLarge,
                            color = DeepBlueText
                        )
                    }
                    items(state.pendingRequests, key = { it.id }) { req ->
                        GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(req.requestedName, style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                                    Text("${req.phone} • ${req.addressOrRoom.ifBlank { lStr(R.string.no_room_address) }}", style = MaterialTheme.typography.bodyMedium)
                                }
                                StatusPill(label = lStr(R.string.status_pending), tone = PillTone.WARNING)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SkyPrimaryButton(
                                    text = lStr(R.string.approve_btn),
                                    icon = Icons.Outlined.Check,
                                    onClick = { onReviewRequest(req.id, true) },
                                    loading = state.isActionBusy,
                                    modifier = Modifier.weight(1f).testTag("approve_req_${req.id}")
                                )
                                OutlinedButton(
                                    onClick = { onReviewRequest(req.id, false) },
                                    modifier = Modifier.weight(1f).height(50.dp)
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(lStr(R.string.reject_btn))
                                }
                            }
                        }
                    }
                }
            }

            if (filter != "PENDING") {
                if (filteredCustomers.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(lStr(R.string.no_customers_match), style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                        }
                    }
                } else {
                    items(filteredCustomers, key = { it.id }) { cust ->
                        val due = dueByCustomer[cust.id] ?: 0.0
                        val sub = subByCustomer[cust.id]
                        val todayRecs = state.dailyTiffins.filter { it.customerId == cust.id && it.serviceDate == todayIso }
                        val deliveredCountToday = todayRecs.count { it.status == TiffinStatus.DELIVERED }
                        val custSummary = state.dailyCustomerSummaries[cust.id]
                        val activeLeaveToday = custSummary?.activeLeave
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            strongSurface = true,
                            onClick = { selectedCustomer = cust }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cust.fullName, style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                                    Text(
                                        buildString {
                                            append(cust.phone)
                                            if (cust.addressOrRoom.isNotBlank()) append(" • ${cust.addressOrRoom}")
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                StatusPill(
                                    label = if (activeLeaveToday != null) lStr(R.string.daily_on_leave_label) else cust.customerStatus.localizedLabel(),
                                    tone = when {
                                        activeLeaveToday != null -> PillTone.WARNING
                                        cust.customerStatus == AccountStatus.ACTIVE -> PillTone.SUCCESS
                                        else -> PillTone.ERROR
                                    }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (sub != null) "${sub.planNameSnapshot} (${sub.status.localizedLabel()})" else lStr(R.string.no_plan_assigned),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SecondaryText
                                )
                                Text(
                                    text = lStr(R.string.today_delivered_count, deliveredCountToday),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (deliveredCountToday > 0) SuccessGreen else SecondaryText
                                )
                            }
                            if (due > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = lStr(R.string.due_colon, TiffinBusinessLogic.formatCurrency(due, state.mess.currency)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ErrorCoral
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(lStr(R.string.add_customer_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(lStr(R.string.field_full_name_req)) }, singleLine = true)
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(lStr(R.string.field_mobile_req)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(lStr(R.string.google_email_opt)) },
                        singleLine = true
                    )
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(lStr(R.string.field_room_opt)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        onCreateCustomer(name, phone, email, address)
                    }
                ) { Text(lStr(R.string.add_customer_title)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(lStr(R.string.action_cancel)) }
            }
        )
    }

    val cust = selectedCustomer
    if (cust != null) {
        var editName by remember(cust.id) { mutableStateOf(cust.fullName) }
        var editPhone by remember(cust.id) { mutableStateOf(cust.phone) }
        var editAddr by remember(cust.id) { mutableStateOf(cust.addressOrRoom) }
        var leaveStart by remember(cust.id) { mutableStateOf(todayIso) }
        var leaveEnd by remember(cust.id) { mutableStateOf(todayIso) }
        var leaveReason by remember(cust.id) { mutableStateOf("") }
        val custSummary = state.dailyCustomerSummaries[cust.id]
        val custTiffinsCount = custSummary?.monthDeliveredCount ?: 0
        val custDue = dueByCustomer[cust.id] ?: 0.0
        val custLeaves = state.leaves.filter { it.customerId == cust.id && it.endDate >= todayIso }

        ModalBottomSheet(onDismissRequest = { selectedCustomer = null }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(lStr(R.string.customer_detail_title, cust.fullName), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                Text(
                    text = lStr(
                        R.string.customer_detail_stats,
                        custTiffinsCount,
                        TiffinBusinessLogic.formatCurrency(custDue, state.mess.currency)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text(lStr(R.string.field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = editPhone, onValueChange = { editPhone = it }, label = { Text(lStr(R.string.field_mobile_short)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = editAddr, onValueChange = { editAddr = it }, label = { Text(lStr(R.string.field_room_addr)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkyPrimaryButton(
                        text = lStr(R.string.save_details_btn),
                        onClick = {
                            onUpdateCustomerDetails(cust.id, editName, editPhone, editAddr)
                            selectedCustomer = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val nextStatus = if (cust.customerStatus == AccountStatus.ACTIVE) "suspended" else "active"
                            onUpdateCustomerStatus(cust.id, nextStatus)
                            selectedCustomer = null
                        },
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Text(if (cust.customerStatus == AccountStatus.ACTIVE) lStr(R.string.suspend_btn) else lStr(R.string.activate_btn))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            selectedCustomer = null
                            onOpenSubscriptionsForCustomer()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(lStr(R.string.assign_plan_btn)) }
                    OutlinedButton(
                        onClick = {
                            selectedCustomer = null
                            onOpenBillingForCustomer()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(lStr(R.string.qa_bills_payments)) }
                }
                Text(lStr(R.string.leave_card_title), style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                custLeaves.forEach { lv ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lStr(R.string.date_range_to, lv.startDate, lv.endDate), style = MaterialTheme.typography.bodyMedium)
                        TextButton(
                            onClick = {
                                onCancelLeave(lv.id)
                                selectedCustomer = null
                            }
                        ) { Text(lStr(R.string.cancel_leave_btn), color = ErrorCoral) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = leaveStart,
                        onValueChange = { leaveStart = it },
                        label = { Text(lStr(R.string.start_short)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = leaveEnd,
                        onValueChange = { leaveEnd = it },
                        label = { Text(lStr(R.string.end_short)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = leaveReason,
                        onValueChange = { leaveReason = it },
                        label = { Text(lStr(R.string.reason_opt)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            onCreateLeave(cust.id, leaveStart, leaveEnd, leaveReason)
                            selectedCustomer = null
                        }
                    ) { Text(lStr(R.string.add_leave_btn)) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
