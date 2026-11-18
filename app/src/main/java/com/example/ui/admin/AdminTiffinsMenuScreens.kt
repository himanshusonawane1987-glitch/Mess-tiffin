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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.EditNote
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
import androidx.compose.ui.unit.dp
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
import java.time.LocalDate

@Composable
fun AdminTiffinsScreen(
    state: AdminUiState,
    onSelectDate: (LocalDate) -> Unit,
    onMarkTiffin: (customerId: String, dateIso: String, mealSlot: String, status: TiffinStatus, note: String?) -> Unit,
    onBulkMarkDelivered: (customerIds: List<String>, dateIso: String, mealSlot: String) -> Unit
) {
    val availableSlots = remember(state.mess.mealSlots) {
        state.mess.mealSlots.ifEmpty { listOf("daily") }
    }
    var selectedSlot by remember(availableSlots) { mutableStateOf(availableSlots.first()) }
    var statusFilter by remember { mutableStateOf("All") } // All, Pending, Not Delivered, Delivered
    val selectedIds = remember { mutableStateListOf<String>() }
    var confirmBulkMode by remember { mutableStateOf<String?>(null) } // "selected" or "all"
    var noteTarget by remember { mutableStateOf<Pair<CustomerProfile, TiffinStatus>?>(null) }

    val dateIso = state.selectedDate.toString()
    val recordsByCustomer = remember(state.dailyTiffins, selectedSlot, dateIso) {
        state.dailyTiffins
            .filter { it.serviceDate == dateIso && it.mealSlot == selectedSlot }
            .associateBy { it.customerId }
    }

    val filteredCustomers = remember(state.activeCustomers, recordsByCustomer, statusFilter) {
        state.activeCustomers.filter { cust ->
            val rec = recordsByCustomer[cust.id]
            when (statusFilter) {
                "Pending" -> rec == null
                "Delivered" -> rec?.status == TiffinStatus.DELIVERED
                "Not Delivered" -> rec?.status == TiffinStatus.NOT_DELIVERED
                else -> true
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Daily Tiffins", style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
            Text("Mark or correct delivery status per customer.", style = MaterialTheme.typography.bodyMedium)
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

        // Date switcher
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
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                    Text(
                        text = TiffinBusinessLogic.formatDateDisplay(dateIso),
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepBlueText
                    )
                    IconButton(
                        onClick = { onSelectDate(state.selectedDate.plusDays(1)) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next day")
                    }
                }
                if (availableSlots.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableSlots.forEach { slot ->
                            FilterChip(
                                selected = selectedSlot == slot,
                                onClick = { selectedSlot = slot },
                                label = { Text(slot.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Pending", "Delivered", "Not Delivered").forEach { tab ->
                        FilterChip(
                            selected = statusFilter == tab,
                            onClick = { statusFilter = tab },
                            label = { Text(tab) }
                        )
                    }
                }
            }
        }

        // Bulk actions
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkyPrimaryButton(
                    text = if (selectedIds.isEmpty()) "Mark All Active Delivered" else "Mark Selected (${selectedIds.size})",
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
                    ) { Text("Clear") }
                }
            }
        }

        if (filteredCustomers.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("No active customers in this view.", style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                }
            }
        } else {
            items(filteredCustomers, key = { it.id }) { cust ->
                val rec = recordsByCustomer[cust.id]
                val dayTotal = state.dailyTiffins.count {
                    it.customerId == cust.id && it.serviceDate == dateIso && it.status == TiffinStatus.DELIVERED
                }
                val isChecked = selectedIds.contains(cust.id)

                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
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
                            Text(cust.fullName, style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                            Text(
                                "${cust.addressOrRoom.ifBlank { cust.phone }} • Day total: $dayTotal",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (!rec?.note.isNullOrBlank()) {
                                Text("Note: ${rec?.note}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        val (pillLabel, pillTone) = when (rec?.status) {
                            TiffinStatus.DELIVERED -> "Delivered" to PillTone.SUCCESS
                            TiffinStatus.NOT_DELIVERED -> "Not Delivered" to PillTone.ERROR
                            TiffinStatus.CANCELLED -> "Cancelled" to PillTone.NEUTRAL
                            null -> "Pending" to PillTone.WARNING
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
                        ) { Text("Delivered", color = SuccessGreen) }
                        OutlinedButton(
                            onClick = { onMarkTiffin(cust.id, dateIso, selectedSlot, TiffinStatus.NOT_DELIVERED, rec?.note) },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) { Text("Missed", color = ErrorCoral) }
                        OutlinedButton(
                            onClick = { onMarkTiffin(cust.id, dateIso, selectedSlot, TiffinStatus.CANCELLED, rec?.note) },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) { Text("Cancel") }
                        IconButton(
                            onClick = { noteTarget = cust to (rec?.status ?: TiffinStatus.DELIVERED) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Outlined.EditNote, contentDescription = "Add note or correct status", tint = DeepBlueText)
                        }
                    }
                }
            }
        }
    }

    val bulkMode = confirmBulkMode
    if (bulkMode != null) {
        val targetIds = if (bulkMode == "selected") selectedIds.toList() else state.activeCustomers.map { it.id }
        AlertDialog(
            onDismissRequest = { confirmBulkMode = null },
            title = { Text("Confirm Bulk Delivery") },
            text = {
                Text("Mark ${targetIds.size} customer(s) as Delivered for $selectedSlot on ${TiffinBusinessLogic.formatDateDisplay(dateIso)}?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBulkMode = null
                        selectedIds.clear()
                        onBulkMarkDelivered(targetIds, dateIso, selectedSlot)
                    }
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkMode = null }) { Text("Cancel") }
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
            title = { Text("Update / Correct Record — ${target.first.fullName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TiffinStatus.entries.forEach { st ->
                            FilterChip(
                                selected = chosenStatus == st,
                                onClick = { chosenStatus = st },
                                label = { Text(st.name.lowercase().replace('_', ' ')) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Optional Note / Correction Reason") },
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
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { noteTarget = null }) { Text("Cancel") }
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
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = DeepBlueText)
                }
                Column {
                    Text("Daily Menu", style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
                    Text("Drafts stay hidden; published menus appear for customers.", style = MaterialTheme.typography.bodyMedium)
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
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous date")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(TiffinBusinessLogic.formatDateDisplay(menuDate.toString()), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                        if (existing != null) {
                            StatusPill(
                                label = existing.status.replaceFirstChar { it.uppercase() },
                                tone = if (existing.status == "published") PillTone.SUCCESS else PillTone.WARNING
                            )
                        }
                    }
                    IconButton(onClick = { menuDate = menuDate.plusDays(1) }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next date")
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (state.mess.mealSlots.contains("breakfast")) {
                    OutlinedTextField(value = breakfast, onValueChange = { breakfast = it }, label = { Text("Breakfast") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(value = lunch, onValueChange = { lunch = it }, label = { Text("Lunch Menu") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (state.mess.mealSlots.contains("dinner") || state.mess.mealSlots.isEmpty()) {
                    OutlinedTextField(value = dinner, onValueChange = { dinner = it }, label = { Text("Dinner Menu") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(value = special, onValueChange = { special = it }, label = { Text("Special Item (optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onSaveMenu(menuDate.toString(), breakfast, lunch, dinner, special, notes, false) },
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) { Text("Save Draft") }
                    SkyPrimaryButton(
                        text = "Publish Menu",
                        icon = Icons.Outlined.CheckCircle,
                        onClick = { onSaveMenu(menuDate.toString(), breakfast, lunch, dinner, special, notes, true) },
                        loading = state.isActionBusy,
                        modifier = Modifier.weight(1f).testTag("publish_menu_button")
                    )
                }
            }
        }

        if (state.menus.isNotEmpty()) {
            item { Text("Menu History", style = MaterialTheme.typography.titleLarge, color = DeepBlueText) }
            items(state.menus, key = { it.id }) { m ->
                GlassCard(modifier = Modifier.fillMaxWidth(), onClick = {
                    runCatching { menuDate = LocalDate.parse(m.menuDate) }
                }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(TiffinBusinessLogic.formatDateDisplay(m.menuDate), style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                        StatusPill(
                            label = m.status.replaceFirstChar { it.uppercase() },
                            tone = if (m.status == "published") PillTone.SUCCESS else PillTone.WARNING
                        )
                    }
                    if (!m.lunch.isNullOrBlank()) Text("Lunch: ${m.lunch}", style = MaterialTheme.typography.bodyMedium)
                    if (!m.dinner.isNullOrBlank()) Text("Dinner: ${m.dinner}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
