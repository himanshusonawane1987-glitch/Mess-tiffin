package com.example.ui.discovery

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LocationHelper
import com.example.domain.DiscoverableMess
import com.example.domain.Mess
import com.example.domain.MessReview
import com.example.domain.ProximityTier
import com.example.domain.TiffinBusinessLogic
import com.example.domain.VegType
import com.example.ui.auth.ErrorNoticeCard
import com.example.ui.theme.DeepBlueText
import com.example.ui.theme.GlassBackdrop
import com.example.ui.theme.GlassCard
import com.example.ui.theme.PillTone
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SkyPrimary
import com.example.ui.theme.SkyPrimaryButton
import com.example.ui.theme.StatusPill
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.lStr
import com.example.ui.theme.localizedLabel
import com.example.ui.theme.localizedMenuPreviewItems
import java.util.Locale

@Composable
fun FindTiffinNearMeScreen(
    messes: List<DiscoverableMess>,
    areaFilter: String,
    cityFilter: String,
    isLoading: Boolean,
    isLocating: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onLocationFilterChange: (area: String, city: String) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onRefresh: () -> Unit,
    onSelectMess: (DiscoverableMess) -> Unit,
    onBack: (() -> Unit)? = null,
    bottomPaddingDp: Int = 24
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            onUseCurrentLocation()
        } else {
            permissionDenied = true
        }
    }

    val content: @Composable () -> Unit = {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (onBack != null && bottomPaddingDp <= 32) Modifier.safeDrawingPadding() else Modifier),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = bottomPaddingDp.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("discovery_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = lStr(R.string.action_back),
                                    tint = DeepBlueText
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        Column {
                            Text(
                                text = lStr(R.string.discovery_title),
                                style = MaterialTheme.typography.headlineMedium,
                                color = DeepBlueText
                            )
                            Text(
                                text = lStr(R.string.discovery_sub),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText
                            )
                        }
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("discovery_refresh_button")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = lStr(R.string.action_refresh), tint = DeepBlueText)
                    }
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = areaFilter,
                            onValueChange = { onLocationFilterChange(it, cityFilter) },
                            label = { Text(lStr(R.string.field_area_filter)) },
                            placeholder = { Text(lStr(R.string.placeholder_area)) },
                            leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("discovery_area_filter_input")
                        )
                        OutlinedTextField(
                            value = cityFilter,
                            onValueChange = { onLocationFilterChange(areaFilter, it) },
                            label = { Text(lStr(R.string.field_city_filter)) },
                            placeholder = { Text(lStr(R.string.placeholder_city)) },
                            leadingIcon = { Icon(Icons.Outlined.LocationCity, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("discovery_city_filter_input")
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            if (LocationHelper.hasCoarseLocationPermission(context)) {
                                permissionDenied = false
                                onUseCurrentLocation()
                            } else {
                                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        enabled = !isLocating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("use_current_location_button")
                    ) {
                        Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isLocating) lStr(R.string.detecting_location) else lStr(R.string.use_current_location))
                    }
                }
            }

            if (isLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp)),
                        color = SkyPrimary
                    )
                }
            }

            if (!infoMessage.isNullOrBlank() || permissionDenied) {
                item {
                    val notice = infoMessage ?: lStr(R.string.location_permission_off)
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        StatusPill(label = notice, tone = PillTone.INFO)
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                item { ErrorNoticeCard(message = errorMessage) }
            }

            if (!isLoading && messes.isEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("discovery_empty_state"),
                        strongSurface = true
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Explore,
                                contentDescription = null,
                                tint = SkyPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = lStr(R.string.discovery_empty_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepBlueText
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = lStr(R.string.discovery_empty_sub),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText
                            )
                        }
                    }
                }
            } else {
                items(messes, key = { it.mess.id }) { item ->
                    DiscoverableMessCard(
                        item = item,
                        hasLocationFilter = areaFilter.isNotBlank() || cityFilter.isNotBlank(),
                        onOpenDetail = { onSelectMess(item) },
                        onCallPhone = { phone ->
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(dialIntent)
                        }
                    )
                }
            }
        }
    }

    if (bottomPaddingDp <= 32) {
        GlassBackdrop { content() }
    } else {
        content()
    }
}

@Composable
private fun DiscoverableMessCard(
    item: DiscoverableMess,
    hasLocationFilter: Boolean,
    onOpenDetail: () -> Unit,
    onCallPhone: (String) -> Unit
) {
    val mess = item.mess
    val veg = VegType.fromDb(mess.vegType)
    val publicPhone = mess.phonePublic.ifBlank { mess.phone }
    val menuItems = localizedMenuPreviewItems(item.todayMenu, 3)

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discovery_mess_card_${mess.id}"),
        strongSurface = true,
        onClick = onOpenDetail
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mess.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = DeepBlueText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val locText = listOf(mess.area, mess.city).filter { it.isNotBlank() }.joinToString(", ")
                if (locText.isNotBlank()) {
                    Text(
                        text = locText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            RatingBadgePill(avgRating = mess.avgRating, ratingCount = mess.ratingCount)
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val vegTone = when (veg) {
                VegType.VEG -> PillTone.SUCCESS
                VegType.NON_VEG -> PillTone.ERROR
                VegType.BOTH -> PillTone.INFO
            }
            StatusPill(label = veg.localizedLabel(), tone = vegTone)

            if (hasLocationFilter && item.proximityTier != ProximityTier.OTHER) {
                StatusPill(
                    label = item.proximityTier.localizedLabel(),
                    tone = if (item.proximityTier == ProximityTier.SAME_AREA_AND_CITY) PillTone.SUCCESS else PillTone.INFO
                )
            }

            mess.monthlyPrice?.let { price ->
                StatusPill(
                    label = lStr(R.string.price_per_month, TiffinBusinessLogic.formatCurrency(price, mess.currency)),
                    tone = PillTone.NEUTRAL
                )
            }
        }

        val timings = buildList {
            if (mess.lunchTime.isNotBlank()) add(lStr(R.string.lunch_prefix, mess.lunchTime))
            if (mess.dinnerTime.isNotBlank()) add(lStr(R.string.dinner_prefix, mess.dinnerTime))
        }.joinToString(" • ")
        if (timings.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(text = timings, style = MaterialTheme.typography.labelMedium, color = DeepBlueText)
            }
        }

        if (menuItems.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SkyPrimary.copy(alpha = 0.08f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = lStr(R.string.todays_menu_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = SkyPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                menuItems.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeepBlueText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (publicPhone.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onCallPhone(publicPhone) },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("call_mess_button_${mess.id}")
                ) {
                    Icon(Icons.Outlined.Call, contentDescription = lStr(R.string.action_call_phone, mess.name), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(lStr(R.string.action_call_phone, publicPhone))
                }
                Text(
                    text = lStr(R.string.view_and_join),
                    style = MaterialTheme.typography.labelLarge,
                    color = SkyPrimary
                )
            }
        }
    }
}

@Composable
fun RatingBadgePill(avgRating: Double, ratingCount: Int) {
    if (ratingCount <= 0) {
        StatusPill(label = lStr(R.string.badge_new), tone = PillTone.INFO)
    } else {
        StatusPill(
            label = "★ ${String.format(Locale.ENGLISH, "%.1f", avgRating)} ($ratingCount)",
            tone = PillTone.WARNING
        )
    }
}

@Composable
fun SellerDetailScreen(
    item: DiscoverableMess,
    reviews: List<MessReview>,
    reviewsLoading: Boolean,
    canJoinFromDiscovery: Boolean,
    isCurrentActiveMember: Boolean,
    currentUserId: String?,
    isActionBusy: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onBack: () -> Unit,
    onJoinMess: ((Mess) -> Unit)? = null,
    onSubmitReview: ((stars: Int, comment: String) -> Unit)? = null,
    bottomPaddingDp: Int = 24
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val mess = item.mess
    val veg = VegType.fromDb(mess.vegType)
    val publicPhone = mess.phonePublic.ifBlank { mess.phone }

    val existingOwnReview = reviews.firstOrNull { it.customerId == currentUserId }
    var selectedStars by remember(existingOwnReview?.id, existingOwnReview?.stars) {
        mutableIntStateOf(existingOwnReview?.stars ?: 5)
    }
    var reviewComment by remember(existingOwnReview?.id, existingOwnReview?.comment) {
        mutableStateOf(existingOwnReview?.comment.orEmpty())
    }

    val content: @Composable () -> Unit = {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (bottomPaddingDp <= 32) Modifier.safeDrawingPadding() else Modifier),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = bottomPaddingDp.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("seller_detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = lStr(R.string.action_back), tint = DeepBlueText)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mess.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = DeepBlueText
                        )
                        val locText = listOf(mess.area, mess.city).filter { it.isNotBlank() }.joinToString(", ")
                        if (locText.isNotBlank()) {
                            Text(text = locText, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                        }
                    }
                }
            }

            if (!successMessage.isNullOrBlank()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        StatusPill(label = successMessage, tone = PillTone.SUCCESS)
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                item { ErrorNoticeCard(message = errorMessage) }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(
                                label = veg.localizedLabel(),
                                tone = when (veg) {
                                    VegType.VEG -> PillTone.SUCCESS
                                    VegType.NON_VEG -> PillTone.ERROR
                                    VegType.BOTH -> PillTone.INFO
                                }
                            )
                            if (isCurrentActiveMember) {
                                StatusPill(label = lStr(R.string.your_mess_pill), tone = PillTone.SUCCESS)
                            }
                        }
                        RatingBadgePill(avgRating = mess.avgRating, ratingCount = mess.ratingCount)
                    }

                    Spacer(Modifier.height(12.dp))

                    mess.monthlyPrice?.let { price ->
                        Text(
                            text = lStr(R.string.price_per_month_full, TiffinBusinessLogic.formatCurrency(price, mess.currency)),
                            style = MaterialTheme.typography.headlineSmall,
                            color = DeepBlueText
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    if (mess.description.isNotBlank()) {
                        Text(text = mess.description, style = MaterialTheme.typography.bodyLarge, color = DeepBlueText)
                        Spacer(Modifier.height(10.dp))
                    }

                    val timings = buildList {
                        if (mess.lunchTime.isNotBlank()) add(lStr(R.string.lunch_colon, mess.lunchTime))
                        if (mess.dinnerTime.isNotBlank()) add(lStr(R.string.dinner_colon, mess.dinnerTime))
                    }.joinToString("  •  ")
                    if (timings.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = SkyPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(text = timings, style = MaterialTheme.typography.bodyMedium, color = DeepBlueText)
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    if (mess.deliveryAreas.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.LocalShipping, contentDescription = null, tint = SkyPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = lStr(R.string.delivers_to, mess.deliveryAreas),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (publicPhone.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$publicPhone")))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("seller_detail_call_button")
                            ) {
                                Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(lStr(R.string.action_call))
                            }
                        }

                        if (canJoinFromDiscovery && onJoinMess != null && !isCurrentActiveMember) {
                            SkyPrimaryButton(
                                text = lStr(R.string.join_this_mess_btn),
                                onClick = { onJoinMess(mess) },
                                modifier = Modifier
                                    .weight(1.4f)
                                    .testTag("join_this_mess_button")
                            )
                        }
                    }
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.RestaurantMenu, contentDescription = null, tint = SkyPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(lStr(R.string.todays_menu_title), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
                    }
                    Spacer(Modifier.height(10.dp))
                    val menu = item.todayMenu
                    if (menu == null) {
                        Text(
                            text = lStr(R.string.menu_not_posted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    } else {
                        if (!menu.breakfast.isNullOrBlank()) {
                            Text(lStr(R.string.breakfast_colon, menu.breakfast), style = MaterialTheme.typography.bodyLarge, color = DeepBlueText)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (!menu.lunch.isNullOrBlank()) {
                            Text(lStr(R.string.lunch_colon, menu.lunch), style = MaterialTheme.typography.bodyLarge, color = DeepBlueText)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (!menu.dinner.isNullOrBlank()) {
                            Text(lStr(R.string.dinner_colon, menu.dinner), style = MaterialTheme.typography.bodyLarge, color = DeepBlueText)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (!menu.specialItems.isNullOrBlank()) {
                            Text(
                                text = lStr(R.string.special_colon, menu.specialItems),
                                style = MaterialTheme.typography.labelLarge,
                                color = SkyPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (!menu.notes.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(menu.notes, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                        }
                    }
                }
            }

            if (isCurrentActiveMember && onSubmitReview != null) {
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rate_mess_card"),
                        strongSurface = true
                    ) {
                        Text(
                            text = if (existingOwnReview != null) lStr(R.string.rate_update_title) else lStr(R.string.rate_mess_title, mess.name),
                            style = MaterialTheme.typography.titleLarge,
                            color = DeepBlueText
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            (1..5).forEach { star ->
                                IconButton(
                                    onClick = { selectedStars = star },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("rate_star_$star")
                                ) {
                                    Icon(
                                        imageVector = if (star <= selectedStars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                        contentDescription = lStr(R.string.star_cd, star),
                                        tint = if (star <= selectedStars) WarningAmber else SecondaryText,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = reviewComment,
                            onValueChange = { reviewComment = it },
                            label = { Text(lStr(R.string.review_hint)) },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("review_comment_input")
                        )
                        Spacer(Modifier.height(12.dp))
                        SkyPrimaryButton(
                            text = if (existingOwnReview != null) lStr(R.string.update_review_btn) else lStr(R.string.submit_review_btn),
                            onClick = { onSubmitReview(selectedStars, reviewComment) },
                            loading = isActionBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_review_button")
                        )
                    }
                }
            }

            item {
                Text(lStr(R.string.customer_reviews_title), style = MaterialTheme.typography.titleLarge, color = DeepBlueText)
            }

            if (reviewsLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp)),
                        color = SkyPrimary
                    )
                }
            } else if (reviews.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = lStr(R.string.no_reviews_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    }
                }
            } else {
                items(reviews, key = { it.id }) { review ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = review.reviewerName.ifBlank { lStr(R.string.customer_fallback) },
                                style = MaterialTheme.typography.titleMedium,
                                color = DeepBlueText
                            )
                            StatusPill(label = "★ ${review.stars}/5", tone = PillTone.WARNING)
                        }
                        if (review.comment.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color = SkyPrimary.copy(alpha = 0.12f))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = review.comment,
                                style = MaterialTheme.typography.bodyMedium,
                                color = DeepBlueText
                            )
                        }
                    }
                }
            }
        }
    }

    if (bottomPaddingDp <= 32) {
        GlassBackdrop { content() }
    } else {
        content()
    }
}
