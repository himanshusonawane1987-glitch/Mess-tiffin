package com.example.ui.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AdminCustomerMarkStatus
import com.example.domain.CustomerProfile
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinStatus
import com.example.ui.auth.ErrorNoticeCard
import com.example.ui.theme.DeepBlueText
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GlassCard
import com.example.ui.theme.PillTone
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SkyPrimaryButton
import com.example.ui.theme.StatusPill
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.lStr
import com.example.ui.theme.localizedDate
import com.example.ui.theme.localizedLabel
import com.example.ui.theme.localizedMealSlot
import com.example.ui.theme.localizedStatusText
import java.time.LocalDate

@Composable
fun AdminTiffinsScreen(
    state: AdminUiState,
    onSelectDate: (LocalDate) -> Unit,
    onMarkTiffin: (customerId: String, dateIso: String, mealSlot: String, status: TiffinStatus, note: String?) -> Unit,
    onBulkMarkDelivered: (customerIds: List<String>, dateIso: String, mealSlot: String) -> Unit,
    onCreateLeave: (customerId: String, startDateIso: String, endDateIso: String, reason: String) -> Unit = { _, _, _, _ -> },
    onCancelLeave: (leaveId: String) -> Unit = {}
) {
    val availableSlots = remember(state.mess.mealSlots) {
        state.mess.mealSlots.ifEmpty { listOf("daily") }
    }
    var selectedSlot by remember(availableSlots) { mutableStateOf(availableSlots.first()) }
    var statusFilter by remember { mutableStateOf("ALL") }
    val selectedIds = remember { mutableStateListOf<String>() }
    var confirmBulkMode by remember { mutableStateOf<String?>(null) }
    var noteTarget by remember { mutableStateOf<Pair<CustomerProfile, TiffinStatus>?>(null) }
    var leaveTarget by remember { mutableStateOf<CustomerProfile?>(null) }

    val dateIso = state.selectedDate.toString()
    val summaries = state.dailyCustomerSummaries
    val dayTotals = state.dayTotals
    val recordsByCustomer = remember(state.dailyTiffins, selectedSlot, dateIso) {
        state.dailyTiffins
            .filter { it.serviceDate == dateIso && it.mealSlot == selectedSlot }
            .associateBy { it.customerId }
    }

    val filteredCustomers = remember(state.activeCustomers, recordsByCustomer, summaries, statusFilter) {
        state.activeCustomers.filter { cust ->
            val rec = recordsByCustomer[cust.id]
            val markStatus = summaries[cust.id]?.markStatus ?: AdminCustomerMarkStatus.NOT_MARKED
            when (statusFilter) {
                "WANTED" -> markStatus == AdminCustomerMarkStatus.WANTED
                "NOT_WANTED" -> markStatus == AdminCustomerMarkStatus.NOT_WANTED
                "ON_LEAVE" -> markStatus == AdminCustomerMarkStatus.ON_LEAVE
                "PENDING" -> rec == null && markStatus != AdminCustomerMarkStatus.NOT_WANTED && markStatus != AdminCustomerMarkStatus.ON_LEAVE
                "DELIVERED" -> rec?.status == TiffinStatus.DELIVERED
                "NOT_DELIVERED" -> rec?.status == TiffinStatus.NOT_DELIVERED
                else -> true
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("admin_tiffins_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(lStr(R.string.qa_todays_tiffins), style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
            Text(lStr(R.string.admin_tiffins_sub), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
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
            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("admin_day_totals_card"),
                strongSurface = true
            ) {
                Text(
                    text = lStr(
                        R.string.admin_today_total,
                        dayTotals.deliveredTodayCount,
                        TiffinBusinessLogic.formatCurrency(dayTotals.deliveredTodayAmount, state.mess.currency)
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = DeepBlueText,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = lStr(
                        R.string.admin_cook_summary,
                        dayTotals.wantedCount,
                        TiffinBusinessLogic.formatCurrency(dayTotals.wantedTodayAmount, state.mess.currency),
                        dayTotals.notWantedCount,
                        dayTotals.onLeaveCount,
                        dayTotals.notMarkedCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText
                )
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSelectDate(state.selectedDate.minusDays(1)) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = lStr(R.string.action_prev_day))
                    }
                    Text(
                        text = localizedDate(dateIso),
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepBlueText
                    )
                    IconButton(
                        onClick = { onSelectDate(state.selectedDate.plusDays(1)) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = lStr(R.string.action_next_day))
                    }
                }
                if (availableSlots.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableSlots.forEach { slot ->
                            FilterChip(
                                selected = selectedSlot == slot,
                                onClick = { selectedSlot = slot },
                                label = { Text(localizedMealSlot(slot)) }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to lStr(R.string.filter_all),
                        "WANTED" to lStr(R.string.daily_wanted_label),
                        "NOT_WANTED" to lStr(R.string.daily_not_wanted_label),
                        "ON_LEAVE" to lStr(R.string.daily_on_leave_label),
                        "PENDING" to lStr(R.string.tiffin_pending),
                        "DELIVERED" to lStr(R.string.tiffin_delivered),
                        "NOT_DELIVERED" to lStr(R.string.tiffin_not_delivered)
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = statusFilter == key,
                            onClick = { statusFilter = key },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkyPrimaryButton(
                    text = if (selectedIds.isEmpty()) lStr(R.string.bulk_mark_all_btn) else lStr(R.string.bulk_mark_selected_btn, selectedIds.size),
                    icon = Icons.Outlined.DoneAll,
                    onClick = {
                        confirmBulkMode = if (selectedIds.isEmpty()) "all" else "selected"
                    },
                    loading = state.isActionBusy,
                    modifier = Modifier.weight(1f).testTag("bulk_mark_delivered_button")
                )
                if (selectedIds.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { selectedIds.clear() },
                        modifier = Modifier.height(50.dp)
                    ) { Text(lStr(R.string.action_clear)) }
                }
            }
        }

        if (filteredCustomers.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(lStr(R.string.no_active_customers_view), style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                }
            }
        } else {
            items(filteredCustomers, key = { it.id }) { cust ->
                val rec = recordsByCustomer[cust.id]
                val custSum = summaries[cust.id]
                val markStatus = custSum?.markStatus ?: AdminCustomerMarkStatus.NOT_MARKED
                val monthCount = custSum?.monthDeliveredCount ?: 0
                val monthAmount = custSum?.monthAmount ?: 0.0
                val isChecked = selectedIds.contains(cust.id)

                val markTone = when (markStatus) {
                    AdminCustomerMarkStatus.WANTED -> PillTone.SUCCESS
                    AdminCustomerMarkStatus.NOT_WANTED -> PillTone.ERROR
                    AdminCustomerMarkStatus.ON_LEAVE -> PillTone.WARNING
                    AdminCustomerMarkStatus.NOT_MARKED -> PillTone.NEUTRAL
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth().testTag("admin_tiffin_customer_${cust.id}"),
                    strongSurface = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                if (checked) selectedIds.add(cust.id) else selectedIds.remove(cust.id)
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(cust.fullName, style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                                StatusPill(
                                    label = markStatus.localizedLabel(),
                                    tone = markTone,
                                    modifier = Modifier.testTag("admin_mark_badge_${cust.id}")
                                )
                            }
                            Text(
                                text = lStr(
                                    R.string.month_cust_tiffins,
                                    monthCount,
                                    TiffinBusinessLogic.formatCurrency(monthAmount, state.mess.currency)
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = DeepBlueText,
                                fontWeight = FontWeight.SemiBold
                            )
                            val leave = custSum?.activeLeave
                            if (leave != null) {
                                val reasonSuffix = if (leave.reason.isNotBlank()) " (${leave.reason})" else ""
                                Text(
                                    text = lStr(R.string.leave_line, leave.startDate, leave.endDate, reasonSuffix),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarningAmber
                                )
                            } else {
                                Text(
                                    text = cust.addressOrRoom.ifBlank { cust.phone },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SecondaryText
                                )
                            }
                            val recNote = rec?.note
                            if (!recNote.isNullOrBlank()) {
                                Text(lStr(R.string.note_prefix, recNote), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        val pillLabel = rec?.status?.localizedLabel() ?: lStr(R.string.tiffin_pending)
                        val pillTone = when (rec?.status) {
                            TiffinStatus.DELIVERED -> PillTone.SUCCESS
                            TiffinStatus.NOT_DELIVERED -> PillTone.ERROR
                            TiffinStatus.CANCELLED -> PillTone.NEUTRAL
                            null -> PillTone.WARNING
                        }
                        StatusPill(label = pillLabel, tone = pillTone)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onMarkTiffin(cust.id, dateIso, selectedSlot, TiffinStatus.DELIVERED, rec?.note) },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) { Text(lStr(R.string.metric_delivered), color = SuccessGreen) }
                        OutlinedButton(
                            onClick = { onMarkTiffin(cust.id, dateIso, selectedSlot, TiffinStatus.NOT_DELIVERED, rec?.note) },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) { Text(lStr(R.string.tiffin_missed), color = ErrorCoral) }
                        IconButton(
                            onClick = { leaveTarget = cust },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Outlined.EventBusy, contentDescription = lStr(R.string.set_cancel_leave_cd), tint = WarningAmber)
                        }
                        IconButton(
                            onClick = { noteTarget = cust to (rec?.status ?: TiffinStatus.DELIVERED) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Outlined.EditNote, contentDescription = lStr(R.string.add_note_cd), tint = DeepBlueText)
                        }
                    }
                }
            }
        }
    }

    val bulkMode = confirmBulkMode
    if (bulkMode != null) {
        val targetIds = if (bulkMode == "selected") {
            selectedIds.toList()
        } else {
            state.activeCustomers
                .filter {
                    val ms = summaries[it.id]?.markStatus
                    ms != AdminCustomerMarkStatus.NOT_WANTED && ms != AdminCustomerMarkStatus.ON_LEAVE
                }
                .map { it.id }
        }
        val slotLabel = localizedMealSlot(selectedSlot)
        AlertDialog(
            onDismissRequest = { confirmBulkMode = null },
            title = { Text(lStr(R.string.bulk_dialog_title)) },
            text = {
                Text(lStr(R.string.bulk_dialog_body, targetIds.size, slotLabel))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBulkMode = null
                        selectedIds.clear()
                        onBulkMarkDelivered(targetIds, dateIso, selectedSlot)
                    }
                ) { Text(lStr(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkMode = null }) { Text(lStr(R.string.action_cancel)) }
            }
        )
    }

    val target = noteTarget
    if (target != null) {
        var noteText by remember(target.first.id) {
            mutableStateOf(recordsByCustomer[target.first.id]?.note.orEmpty())
        }
        var chosenStatus by remember(target.first.id) { mutableStateOf(target.second) }
        AlertDialog(
            onDismissRequest = { noteTarget = null },
            title = { Text(lStr(R.string.correct_record_title, target.first.fullName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TiffinStatus.entries.forEach { st ->
                            FilterChip(
                                selected = chosenStatus == st,
                                onClick = { chosenStatus = st },
                                label = { Text(st.localizedLabel()) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(lStr(R.string.correction_note_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        noteTarget = null
                        onMarkTiffin(target.first.id, dateIso, selectedSlot, chosenStatus, noteText)
                    }
                ) { Text(lStr(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { noteTarget = null }) { Text(lStr(R.string.action_cancel)) }
            }
        )
    }

    val leaveCust = leaveTarget
    if (leaveCust != null) {
        val existingLeaves = state.leaves.filter { it.customerId == leaveCust.id && it.endDate >= dateIso }
        var startIn by remember(leaveCust.id) { mutableStateOf(dateIso) }
        var endIn by remember(leaveCust.id) { mutableStateOf(dateIso) }
        var reasonIn by remember(leaveCust.id) { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { leaveTarget = null },
            title = { Text(lStr(R.string.admin_leave_dialog_title, leaveCust.fullName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (existingLeaves.isNotEmpty()) {
                        Text(lStr(R.string.active_leaves_label), style = MaterialTheme.typography.labelMedium, color = DeepBlueText)
                        existingLeaves.forEach { lv ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(lStr(R.string.date_range_to, lv.startDate, lv.endDate), style = MaterialTheme.typography.bodyMedium)
                                TextButton(
                                    onClick = {
                                        leaveTarget = null
                                        onCancelLeave(lv.id)
                                    }
                                ) { Text(lStr(R.string.cancel_leave_btn), color = ErrorCoral) }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = startIn,
                        onValueChange = { startIn = it },
                        label = { Text(lStr(R.string.field_start_date)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = endIn,
                        onValueChange = { endIn = it },
                        label = { Text(lStr(R.string.field_end_date)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reasonIn,
                        onValueChange = { reasonIn = it },
                        label = { Text(lStr(R.string.reason_opt)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        leaveTarget = null
                        onCreateLeave(leaveCust.id, startIn, endIn, reasonIn)
                    }
                ) { Text(lStr(R.string.add_leave_btn)) }
            },
            dismissButton = {
                TextButton(onClick = { leaveTarget = null }) { Text(lStr(R.string.action_close)) }
            }
        )
    }
}

@Composable
fun AdminMenuScreen(
    state: AdminUiState,
    onBack: () -> Unit,
    onSaveMenu: (
        dateIso: String,
        breakfast: String?,
        lunch: String?,
        dinner: String?,
        specialItems: String?,
        notes: String?,
        publish: Boolean
    ) -> Unit
) {
    var menuDate by remember { mutableStateOf(TiffinBusinessLogic.messToday(state.mess.timezone)) }
    val existing = remember(state.menus, menuDate) {
        state.menus.firstOrNull { it.menuDate == menuDate.toString() }
    }
    var breakfast by remember(menuDate, existing?.breakfast) { mutableStateOf(existing?.breakfast.orEmpty()) }
    var lunch by remember(menuDate, existing?.lunch) { mutableStateOf(existing?.lunch.orEmpty()) }
    var dinner by remember(menuDate, existing?.dinner) { mutableStateOf(existing?.dinner.orEmpty()) }
    var special by remember(menuDate, existing?.specialItems) { mutableStateOf(existing?.specialItems.orEmpty()) }
    var notes by remember(menuDate, existing?.notes) { mutableStateOf(existing?.notes.orEmpty()) }

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
                    Text(lStr(R.string.qa_daily_menu), style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
                    Text(lStr(R.string.admin_menu_sub), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { menuDate = menuDate.minusDays(1) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = lStr(R.string.action_prev_day))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(localizedDate(menuDate.toString()), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                        if (existing != null) {
                            StatusPill(
                                label = localizedStatusText(existing.status),
                                tone = if (existing.status == "published") PillTone.SUCCESS else PillTone.WARNING
                            )
                        }
                    }
                    IconButton(onClick = { menuDate = menuDate.plusDays(1) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = lStr(R.string.action_next_day))
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (state.mess.mealSlots.contains("breakfast")) {
                    OutlinedTextField(value = breakfast, onValueChange = { breakfast = it }, label = { Text(lStr(R.string.meal_breakfast)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(value = lunch, onValueChange = { lunch = it }, label = { Text(lStr(R.string.lunch_menu_label)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (state.mess.mealSlots.contains("dinner") || state.mess.mealSlots.isEmpty()) {
                    OutlinedTextField(value = dinner, onValueChange = { dinner = it }, label = { Text(lStr(R.string.dinner_menu_label)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(value = special, onValueChange = { special = it }, label = { Text(lStr(R.string.special_opt_label)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(lStr(R.string.notes_opt_label)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onSaveMenu(menuDate.toString(), breakfast, lunch, dinner, special, notes, false) },
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) { Text(lStr(R.string.save_draft_btn)) }
                    SkyPrimaryButton(
                        text = lStr(R.string.publish_menu_btn),
                        icon = Icons.Outlined.CheckCircle,
                        onClick = { onSaveMenu(menuDate.toString(), breakfast, lunch, dinner, special, notes, true) },
                        loading = state.isActionBusy,
                        modifier = Modifier.weight(1f).testTag("publish_menu_button")
                    )
                }
            }
        }

        if (state.menus.isNotEmpty()) {
            item { Text(lStr(R.string.menu_history_title), style = MaterialTheme.typography.titleLarge, color = DeepBlueText) }
            items(state.menus, key = { it.id }) { m ->
                GlassCard(modifier = Modifier.fillMaxWidth(), onClick = {
                    runCatching { menuDate = LocalDate.parse(m.menuDate) }
                }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(localizedDate(m.menuDate), style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                        StatusPill(
                            label = localizedStatusText(m.status),
                            tone = if (m.status == "published") PillTone.SUCCESS else PillTone.WARNING
                        )
                    }
                    if (!m.lunch.isNullOrBlank()) Text(lStr(R.string.lunch_colon, m.lunch), style = MaterialTheme.typography.bodyMedium)
                    if (!m.dinner.isNullOrBlank()) Text(lStr(R.string.dinner_colon, m.dinner), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
