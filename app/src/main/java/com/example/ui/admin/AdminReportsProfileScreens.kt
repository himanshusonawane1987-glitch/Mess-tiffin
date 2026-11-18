package com.example.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.AppLanguage
import com.example.domain.SubscriptionStatus
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinStatus
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
import com.example.ui.theme.SkySecondary
import com.example.ui.theme.StatusPill
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.lStr
import com.example.ui.theme.localizedDate
import com.example.ui.theme.localizedMealSlot
import com.example.ui.theme.localizedMonthTitle
import com.example.ui.theme.localizedStatusText
import java.time.YearMonth

@Composable
fun AdminReportsScreen(
    state: AdminUiState,
    onBack: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit
) {
    val monthTitle = localizedMonthTitle(state.selectedMonth)
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
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = lStr(R.string.action_back), tint = DeepBlueText)
                }
                Column {
                    Text(lStr(R.string.reports_title), style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
                    Text(lStr(R.string.reports_sub), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
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
            }
        }

        item { Text(lStr(R.string.daily_report_title, localizedDate(state.selectedDate.toString())), style = MaterialTheme.typography.titleLarge, color = DeepBlueText) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricValueCard(lStr(R.string.metric_expected), state.expectedTiffinsToday.toString(), modifier = Modifier.weight(1f))
                MetricValueCard(lStr(R.string.metric_delivered), state.deliveredTodayCount.toString(), accentColor = SuccessGreen, modifier = Modifier.weight(1f))
                MetricValueCard(lStr(R.string.metric_completion), "${state.completionRatePercent}%", modifier = Modifier.weight(1f))
            }
        }

        item { Text(lStr(R.string.monthly_report_title, monthTitle), style = MaterialTheme.typography.titleLarge, color = DeepBlueText) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricValueCard(lStr(R.string.metric_delivered_tiffins), totalMonthDelivered.toString(), accentColor = SuccessGreen, modifier = Modifier.weight(1f))
                MetricValueCard(lStr(R.string.metric_active_plans), activeSubsCount.toString(), modifier = Modifier.weight(1f))
                MetricValueCard(lStr(R.string.metric_expired_plans), expiredSubsCount.toString(), accentColor = ErrorCoral, modifier = Modifier.weight(1f))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricValueCard(lStr(R.string.metric_total_billed), TiffinBusinessLogic.formatCurrency(state.monthlyExpectedRevenue, state.mess.currency), modifier = Modifier.weight(1f))
                MetricValueCard(lStr(R.string.metric_paid_received), TiffinBusinessLogic.formatCurrency(state.monthlyCollected, state.mess.currency), accentColor = SuccessGreen, modifier = Modifier.weight(1f))
                MetricValueCard(lStr(R.string.metric_yet_to_collect), TiffinBusinessLogic.formatCurrency(state.monthlyPendingBalance + state.monthlyOverdueBalance, state.mess.currency), accentColor = ErrorCoral, modifier = Modifier.weight(1f))
            }
        }

        item { Text(lStr(R.string.customer_wise_count), style = MaterialTheme.typography.titleLarge, color = DeepBlueText) }
        items(state.customers, key = { it.id }) { c ->
            val count = deliveredByCustomer[c.id] ?: 0
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(c.fullName, style = MaterialTheme.typography.titleMedium, color = DeepBlueText)
                        Text(c.phone, style = MaterialTheme.typography.bodyMedium)
                    }
                    StatusPill(label = lStr(R.string.count_delivered_pill, count), tone = if (count > 0) PillTone.SUCCESS else PillTone.NEUTRAL)
                }
            }
        }
    }
}

@Composable
fun AdminProfileSettingsScreen(
    state: AdminUiState,
    currentLanguage: AppLanguage = AppLanguage.DEFAULT,
    onSelectLanguage: (AppLanguage) -> Unit = {},
    onUpdateMessSettings: (
        name: String,
        phone: String,
        address: String,
        currency: String,
        defaultDueDay: Int,
        mealSlots: List<String>,
        dailyMarkCutoffTime: String
    ) -> Unit,
    onRotateCode: (newCode: String) -> Unit,
    onInviteAdmin: (email: String) -> Unit,
    onSaveUpiSettings: (upiId: String, newQrUri: Uri?) -> Unit = { _, _ -> },
    onSignOut: () -> Unit
) {
    var messName by remember(state.mess.name) { mutableStateOf(state.mess.name) }
    var messPhone by remember(state.mess.phone) { mutableStateOf(state.mess.phone) }
    var messAddress by remember(state.mess.address) { mutableStateOf(state.mess.address) }
    var currency by remember(state.mess.currency) { mutableStateOf(state.mess.currency) }
    var dueDay by remember(state.mess.defaultDueDay) { mutableStateOf(state.mess.defaultDueDay.toString()) }
    var cutoffTime by remember(state.mess.dailyMarkCutoffTime) { mutableStateOf(state.mess.dailyMarkCutoffTime) }
    val selectedSlots = remember(state.mess.mealSlots) {
        mutableStateListOf<String>().apply { addAll(state.mess.mealSlots) }
    }
    var newRegCode by remember { mutableStateOf("") }
    var inviteEmail by remember { mutableStateOf("") }
    var upiIdInput by remember(state.mess.upiId) { mutableStateOf(state.mess.upiId.orEmpty()) }
    var pickedQrUri by remember { mutableStateOf<Uri?>(null) }
    var showQrPreviewDialog by remember { mutableStateOf(false) }

    val qrPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedQrUri = uri
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(lStr(R.string.mess_settings_title), style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
            Text(lStr(R.string.mess_settings_sub), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
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
                    label = if (state.isOwner) lStr(R.string.mess_owner_pill) else lStr(R.string.mess_admin_pill),
                    tone = PillTone.INFO
                )
            }
        }

        if (state.isOwner) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Text(lStr(R.string.mess_details_title), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = messName, onValueChange = { messName = it }, label = { Text(lStr(R.string.mess_name_label)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = messPhone, onValueChange = { messPhone = it }, label = { Text(lStr(R.string.mess_phone_label)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = messAddress, onValueChange = { messAddress = it }, label = { Text(lStr(R.string.mess_address_label)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text(lStr(R.string.currency_label)) }, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = dueDay,
                            onValueChange = { dueDay = it },
                            label = { Text(lStr(R.string.bill_due_day_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cutoffTime,
                        onValueChange = { cutoffTime = it },
                        label = { Text(lStr(R.string.cutoff_time_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_cutoff_time_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(lStr(R.string.meals_you_serve), style = MaterialTheme.typography.labelMedium, color = SecondaryText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("breakfast", "lunch", "dinner", "daily").forEach { slot ->
                            val active = selectedSlots.contains(slot)
                            FilterChip(
                                selected = active,
                                onClick = {
                                    if (active && selectedSlots.size > 1) selectedSlots.remove(slot)
                                    else if (!active) selectedSlots.add(slot)
                                },
                                label = { Text(localizedMealSlot(slot)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SkyPrimaryButton(
                        text = lStr(R.string.save_mess_settings_btn),
                        onClick = {
                            onUpdateMessSettings(
                                messName,
                                messPhone,
                                messAddress,
                                currency,
                                dueDay.toIntOrNull() ?: 5,
                                selectedSlots.toList(),
                                cutoffTime
                            )
                        },
                        loading = state.isActionBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Text(lStr(R.string.mess_join_code_title), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    Text(lStr(R.string.mess_join_code_sub), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newRegCode,
                        onValueChange = { newRegCode = it.uppercase() },
                        label = { Text(lStr(R.string.new_join_code_label)) },
                        leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    SkyPrimaryButton(
                        text = lStr(R.string.update_join_code_btn),
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
                    Text(lStr(R.string.add_helper_admin_title), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    Text(lStr(R.string.add_helper_admin_sub), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inviteEmail,
                        onValueChange = { inviteEmail = it },
                        label = { Text(lStr(R.string.admin_google_email_label)) },
                        leadingIcon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    SkyPrimaryButton(
                        text = lStr(R.string.send_admin_invite_btn),
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
                                StatusPill(localizedStatusText(inv.status), if (inv.status == "accepted") PillTone.SUCCESS else PillTone.INFO)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_payment_settings_card"),
                strongSurface = true
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCode2,
                        contentDescription = null,
                        tint = SkyPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = lStr(R.string.payment_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepBlueText
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = lStr(R.string.payment_settings_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = upiIdInput,
                    onValueChange = { upiIdInput = it },
                    label = { Text(lStr(R.string.upi_id_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_upi_id_input")
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = lStr(R.string.upi_qr_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = DeepBlueText
                )
                Spacer(Modifier.height(6.dp))
                val previewModel: Any? = pickedQrUri ?: state.upiQrSignedUrl
                if (previewModel != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, SkySecondary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .size(150.dp)
                            .clickable { showQrPreviewDialog = true }
                            .testTag("admin_qr_preview_image")
                    ) {
                        AsyncImage(
                            model = previewModel,
                            contentDescription = lStr(R.string.seller_qr_cd),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                StatusPill(
                    label = when {
                        pickedQrUri != null -> lStr(R.string.upi_qr_selected_pending)
                        !state.mess.upiQrPath.isNullOrBlank() -> lStr(R.string.upi_qr_ready)
                        else -> lStr(R.string.upi_qr_not_set)
                    },
                    tone = when {
                        pickedQrUri != null -> PillTone.WARNING
                        !state.mess.upiQrPath.isNullOrBlank() -> PillTone.SUCCESS
                        else -> PillTone.NEUTRAL
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = lStr(R.string.upi_visibility_note),
                    style = MaterialTheme.typography.labelMedium,
                    color = SecondaryText
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        qrPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_upload_qr_button")
                ) {
                    Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.mess.upiQrPath.isNullOrBlank() && pickedQrUri == null) {
                            lStr(R.string.upload_qr_btn)
                        } else {
                            lStr(R.string.change_qr_btn)
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                SkyPrimaryButton(
                    text = lStr(R.string.save_payment_settings_btn),
                    onClick = {
                        onSaveUpiSettings(upiIdInput, pickedQrUri)
                        pickedQrUri = null
                    },
                    loading = state.isActionBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_save_payment_settings_button")
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
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("admin_logout_button")
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(lStr(R.string.action_sign_out))
            }
        }
    }

    val fullQrModel: Any? = pickedQrUri ?: state.upiQrSignedUrl
    if (showQrPreviewDialog && fullQrModel != null) {
        Dialog(onDismissRequest = { showQrPreviewDialog = false }) {
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
                        text = lStr(R.string.upi_qr_label),
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepBlueText
                    )
                    Spacer(Modifier.height(12.dp))
                    AsyncImage(
                        model = fullQrModel,
                        contentDescription = lStr(R.string.seller_qr_cd),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { showQrPreviewDialog = false }) {
                        Text(lStr(R.string.close_preview_btn))
                    }
                }
            }
        }
    }
}
