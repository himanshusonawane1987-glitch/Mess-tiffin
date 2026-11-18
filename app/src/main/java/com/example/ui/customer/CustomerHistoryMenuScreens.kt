package com.example.ui.customer

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DinnerDining
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.DailyMenu
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinStatus
import com.example.ui.theme.DeepBlueText
import com.example.ui.theme.GlassCard
import com.example.ui.theme.MetricValueCard
import com.example.ui.theme.PillTone
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SkyPrimary
import com.example.ui.theme.StatusPill
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.lStr
import com.example.ui.theme.localizedDate
import com.example.ui.theme.localizedLabel
import com.example.ui.theme.localizedMealSlot
import com.example.ui.theme.localizedMonthTitle
import com.example.ui.theme.localizedTime
import java.time.YearMonth

@Composable
fun CustomerHistoryScreen(
    state: CustomerUiState,
    onSelectMonth: (YearMonth) -> Unit
) {
    val currentMessMonth = TiffinBusinessLogic.messYearMonth(state.mess.timezone)
    val monthTitle = localizedMonthTitle(state.selectedMonth)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(lStr(R.string.history_title), style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
            Text(lStr(R.string.history_sub), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSelectMonth(state.selectedMonth.minusMonths(1)) },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("history_prev_month")
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = lStr(R.string.action_prev_month), tint = DeepBlueText)
                    }
                    Text(
                        text = monthTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepBlueText
                    )
                    IconButton(
                        onClick = {
                            if (state.selectedMonth.isBefore(currentMessMonth)) {
                                onSelectMonth(state.selectedMonth.plusMonths(1))
                            }
                        },
                        enabled = state.selectedMonth.isBefore(currentMessMonth),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("history_next_month")
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = lStr(R.string.action_next_month))
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricValueCard(
                    title = lStr(R.string.metric_delivered),
                    value = state.monthSummary.deliveredCount.toString(),
                    accentColor = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricValueCard(
                    title = lStr(R.string.metric_not_delivered),
                    value = state.monthSummary.notDeliveredCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                MetricValueCard(
                    title = lStr(R.string.metric_cancelled),
                    value = state.monthSummary.cancelledCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (state.monthTiffins.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = lStr(R.string.history_empty_month, monthTitle),
                        style = MaterialTheme.typography.titleMedium,
                        color = DeepBlueText
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = lStr(R.string.history_empty_sub),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(state.monthTiffins, key = { it.id }) { rec ->
                val tone = when (rec.status) {
                    TiffinStatus.DELIVERED -> PillTone.SUCCESS
                    TiffinStatus.NOT_DELIVERED -> PillTone.ERROR
                    TiffinStatus.CANCELLED -> PillTone.NEUTRAL
                }
                val icon = when (rec.status) {
                    TiffinStatus.DELIVERED -> Icons.Outlined.CheckCircle
                    TiffinStatus.NOT_DELIVERED, TiffinStatus.CANCELLED -> Icons.Outlined.Close
                }
                val label = rec.status.localizedLabel()
                val timeStr = localizedTime(rec.deliveredAt, state.mess.timezone)
                val slotText = localizedMealSlot(rec.mealSlot)
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = localizedDate(rec.serviceDate),
                                style = MaterialTheme.typography.titleMedium,
                                color = DeepBlueText
                            )
                            Text(
                                text = if (!timeStr.isNullOrBlank()) "$slotText • $timeStr" else slotText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (!rec.note.isNullOrBlank()) {
                                Text(
                                    text = rec.note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SkyPrimary
                                )
                            }
                        }
                        StatusPill(label = label, tone = tone, icon = icon)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerMenuScreen(state: CustomerUiState) {
    val todayIso = TiffinBusinessLogic.messToday(state.mess.timezone).toString()
    val todayMenu = state.todayMenu
    val pastMenus = state.publishedMenus.filter { it.menuDate != todayIso }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(lStr(R.string.todays_menu_title), style = MaterialTheme.typography.headlineMedium, color = DeepBlueText)
            Text(lStr(R.string.menu_from_mess, state.mess.name), style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
        }

        item {
            if (todayMenu == null) {
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Image(
                        painter = painterResource(id = R.drawable.img_empty_menu),
                        contentDescription = lStr(R.string.menu_empty_cd),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = lStr(R.string.menu_not_posted),
                        style = MaterialTheme.typography.titleLarge,
                        color = DeepBlueText
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = lStr(R.string.menu_empty_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                MenuDetailCard(menu = todayMenu, timezone = state.mess.timezone, isToday = true)
            }
        }

        if (pastMenus.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(lStr(R.string.recent_menus), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
            }
            items(pastMenus, key = { it.id }) { menu ->
                MenuDetailCard(menu = menu, timezone = state.mess.timezone, isToday = false)
            }
        }
    }
}

@Composable
private fun MenuDetailCard(menu: DailyMenu, timezone: String, isToday: Boolean) {
    val updatedAt = localizedTime(menu.publishedAt ?: menu.updatedAt, timezone)
    GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = isToday) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = localizedDate(menu.menuDate),
                style = MaterialTheme.typography.titleLarge,
                color = DeepBlueText
            )
            if (isToday) {
                StatusPill(label = lStr(R.string.pill_today), tone = PillTone.INFO)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!menu.breakfast.isNullOrBlank()) {
            MenuMealRow(Icons.Outlined.BakeryDining, lStr(R.string.meal_breakfast), menu.breakfast)
            Spacer(Modifier.height(8.dp))
        }
        if (!menu.lunch.isNullOrBlank()) {
            MenuMealRow(Icons.Outlined.LunchDining, lStr(R.string.meal_lunch), menu.lunch)
            Spacer(Modifier.height(8.dp))
        }
        if (!menu.dinner.isNullOrBlank()) {
            MenuMealRow(Icons.Outlined.DinnerDining, lStr(R.string.meal_dinner), menu.dinner)
            Spacer(Modifier.height(8.dp))
        }
        if (!menu.specialItems.isNullOrBlank()) {
            MenuMealRow(Icons.Outlined.StarOutline, lStr(R.string.special_item_label), menu.specialItems)
            Spacer(Modifier.height(8.dp))
        }
        if (!menu.notes.isNullOrBlank()) {
            Text(
                text = lStr(R.string.note_prefix, menu.notes),
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText
            )
        }
        if (!updatedAt.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = lStr(R.string.updated_at_time, updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryText
            )
        }
    }
}

@Composable
private fun MenuMealRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    itemsText: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = SkyPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = SecondaryText, fontWeight = FontWeight.SemiBold)
            Text(itemsText, style = MaterialTheme.typography.bodyLarge, color = DeepBlueText)
        }
    }
}
