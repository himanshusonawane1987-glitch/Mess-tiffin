package com.example.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.R
import com.example.domain.AccountStatus
import com.example.domain.AdminCustomerMarkStatus
import com.example.domain.AppLanguage
import com.example.domain.BillStatus
import com.example.domain.DailyMarkChoice
import com.example.domain.DailyMenu
import com.example.domain.PaymentMethod
import com.example.domain.ProximityTier
import com.example.domain.SubscriptionStatus
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinStatus
import com.example.domain.VegType
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.Locale

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.DEFAULT }
val LocalLocalizedContext = staticCompositionLocalOf<Context?> { null }

fun localizedContextFor(base: Context, language: AppLanguage): Context {
    val config = Configuration(base.resources.configuration)
    config.setLocale(language.toLocale())
    return base.createConfigurationContext(config)
}

@Composable
fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, language) {
        localizedContextFor(baseContext, language)
    }
    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalLocalizedContext provides localizedContext,
        content = content
    )
}

@Composable
fun lStr(@StringRes resId: Int, vararg formatArgs: Any): String {
    val ctx = LocalLocalizedContext.current ?: LocalContext.current
    return if (formatArgs.isEmpty()) {
        ctx.resources.getString(resId)
    } else {
        ctx.resources.getString(resId, *formatArgs)
    }
}

class AppStrings(
    private val appContext: Context,
    private val languageProvider: () -> AppLanguage
) {
    fun currentLanguage(): AppLanguage = languageProvider()

    fun get(@StringRes resId: Int, vararg formatArgs: Any): String {
        val ctx = localizedContextFor(appContext, languageProvider())
        return if (formatArgs.isEmpty()) {
            ctx.resources.getString(resId)
        } else {
            ctx.resources.getString(resId, *formatArgs)
        }
    }

    fun getFor(language: AppLanguage, @StringRes resId: Int, vararg formatArgs: Any): String {
        val ctx = localizedContextFor(appContext, language)
        return if (formatArgs.isEmpty()) {
            ctx.resources.getString(resId)
        } else {
            ctx.resources.getString(resId, *formatArgs)
        }
    }
}

@Composable
fun localizedGreeting(timezone: String?): String {
    val hour = ZonedDateTime.now(TiffinBusinessLogic.safeZoneId(timezone)).hour
    val resId = when {
        hour < 12 -> R.string.greeting_morning
        hour < 17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }
    return lStr(resId)
}

@Composable
fun localizedDate(isoDate: String): String {
    val lang = LocalAppLanguage.current
    return TiffinBusinessLogic.formatDateDisplay(isoDate, lang.toLocale())
}

@Composable
fun localizedMonth(isoMonthDate: String): String {
    val lang = LocalAppLanguage.current
    return TiffinBusinessLogic.formatMonthDisplay(isoMonthDate, lang.toLocale())
}

@Composable
fun localizedMonthTitle(yearMonth: YearMonth): String {
    val lang = LocalAppLanguage.current
    return TiffinBusinessLogic.formatMonthDisplay(
        TiffinBusinessLogic.monthStartIso(yearMonth),
        lang.toLocale()
    )
}

@Composable
fun localizedTime(isoTimestamp: String?, timezone: String?): String? {
    val lang = LocalAppLanguage.current
    return TiffinBusinessLogic.formatTimeInMessZone(isoTimestamp, timezone, lang.toLocale())
}

@Composable
fun VegType.localizedLabel(): String = when (this) {
    VegType.VEG -> lStr(R.string.veg_type_veg)
    VegType.NON_VEG -> lStr(R.string.veg_type_non_veg)
    VegType.BOTH -> lStr(R.string.veg_type_both)
}

@Composable
fun PaymentMethod.localizedLabel(): String = when (this) {
    PaymentMethod.CASH -> lStr(R.string.payment_cash)
    PaymentMethod.UPI -> lStr(R.string.payment_upi)
    PaymentMethod.BANK_TRANSFER -> lStr(R.string.payment_bank)
    PaymentMethod.OTHER -> lStr(R.string.payment_other)
}

@Composable
fun TiffinStatus.localizedLabel(): String = when (this) {
    TiffinStatus.DELIVERED -> lStr(R.string.tiffin_delivered)
    TiffinStatus.NOT_DELIVERED -> lStr(R.string.tiffin_not_delivered)
    TiffinStatus.CANCELLED -> lStr(R.string.tiffin_cancelled)
}

@Composable
fun SubscriptionStatus.localizedLabel(): String = when (this) {
    SubscriptionStatus.ACTIVE -> lStr(R.string.sub_active)
    SubscriptionStatus.EXPIRING_SOON -> lStr(R.string.sub_expiring)
    SubscriptionStatus.EXPIRED -> lStr(R.string.sub_expired)
    SubscriptionStatus.PAUSED -> lStr(R.string.sub_paused)
    SubscriptionStatus.CANCELLED -> lStr(R.string.sub_cancelled)
}

@Composable
fun BillStatus.localizedLabel(): String = when (this) {
    BillStatus.PAID -> lStr(R.string.bill_paid)
    BillStatus.PARTIALLY_PAID -> lStr(R.string.bill_partially_paid)
    BillStatus.PENDING -> lStr(R.string.bill_pending)
    BillStatus.OVERDUE -> lStr(R.string.bill_overdue)
}

@Composable
fun ProximityTier.localizedLabel(): String = when (this) {
    ProximityTier.SAME_AREA_AND_CITY -> lStr(R.string.proximity_same_area)
    ProximityTier.SAME_CITY -> lStr(R.string.proximity_same_city)
    ProximityTier.OTHER -> lStr(R.string.proximity_other)
}

@Composable
fun DailyMarkChoice.localizedLabel(): String = when (this) {
    DailyMarkChoice.WANTED -> lStr(R.string.daily_wanted_label)
    DailyMarkChoice.NOT_WANTED -> lStr(R.string.daily_not_wanted_label)
}

@Composable
fun AdminCustomerMarkStatus.localizedLabel(): String = when (this) {
    AdminCustomerMarkStatus.WANTED -> lStr(R.string.daily_wanted_label)
    AdminCustomerMarkStatus.NOT_WANTED -> lStr(R.string.daily_not_wanted_label)
    AdminCustomerMarkStatus.ON_LEAVE -> lStr(R.string.daily_on_leave_label)
    AdminCustomerMarkStatus.NOT_MARKED -> lStr(R.string.mark_status_not_marked)
}

@Composable
fun AccountStatus.localizedLabel(): String = when (this) {
    AccountStatus.PENDING -> lStr(R.string.status_pending)
    AccountStatus.ACTIVE -> lStr(R.string.status_active)
    AccountStatus.SUSPENDED -> lStr(R.string.status_suspended)
    AccountStatus.REJECTED -> lStr(R.string.status_rejected)
}

@Composable
fun localizedMealSlot(slot: String): String = when (slot.lowercase(Locale.ENGLISH).trim()) {
    "breakfast" -> lStr(R.string.meal_breakfast)
    "lunch" -> lStr(R.string.meal_lunch)
    "dinner" -> lStr(R.string.meal_dinner)
    "daily" -> lStr(R.string.meal_daily)
    else -> slot.replaceFirstChar { it.uppercase() }
}

@Composable
fun localizedStatusText(status: String): String = when (status.lowercase(Locale.ENGLISH).trim()) {
    "approved" -> lStr(R.string.status_approved)
    "rejected" -> lStr(R.string.status_rejected)
    "pending" -> lStr(R.string.status_pending)
    "active" -> lStr(R.string.status_active)
    "suspended" -> lStr(R.string.status_suspended)
    "accepted" -> lStr(R.string.status_approved)
    "published" -> lStr(R.string.menu_published_status)
    "draft" -> lStr(R.string.menu_draft_status)
    else -> status.replaceFirstChar { it.uppercase() }
}

@Composable
fun localizedMenuPreviewItems(menu: DailyMenu?, maxItems: Int = 3): List<String> {
    if (menu == null) return emptyList()
    val items = mutableListOf<String>()
    if (!menu.lunch.isNullOrBlank()) items.add(lStr(R.string.lunch_colon, menu.lunch.trim()))
    if (!menu.dinner.isNullOrBlank()) items.add(lStr(R.string.dinner_colon, menu.dinner.trim()))
    if (!menu.breakfast.isNullOrBlank()) items.add(lStr(R.string.breakfast_colon, menu.breakfast.trim()))
    if (!menu.specialItems.isNullOrBlank()) items.add(lStr(R.string.special_colon, menu.specialItems.trim()))
    return items.take(maxItems)
}
