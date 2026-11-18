package com.example.ui.auth

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.Profile
import com.example.domain.SellerRegistrationResult
import com.example.domain.VegType
import com.example.ui.theme.DeepBlueText
import com.example.ui.theme.GlassBackdrop
import com.example.ui.theme.GlassCard
import com.example.ui.theme.PillTone
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SkyPrimary
import com.example.ui.theme.SkyPrimaryButton
import com.example.ui.theme.StatusPill
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.lStr
import com.example.ui.theme.localizedLabel

@Composable
fun SellerRegistrationScreen(
    profile: Profile,
    isBusy: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSubmit: (
        messName: String,
        area: String,
        city: String,
        phone: String,
        vegType: VegType,
        monthlyPriceInput: String,
        lunchTime: String,
        dinnerTime: String,
        deliveryAreas: String,
        description: String
    ) -> Unit
) {
    BackHandler(onBack = onBack)

    var messName by remember { mutableStateOf("") }
    var area by remember(profile.area) { mutableStateOf(profile.area) }
    var city by remember(profile.city) { mutableStateOf(profile.city) }
    var phone by remember(profile.phone) { mutableStateOf(profile.phone) }
    var vegType by remember { mutableStateOf(VegType.VEG) }
    var monthlyPrice by remember { mutableStateOf("2500") }
    var lunchTime by remember { mutableStateOf("12:30 PM") }
    var dinnerTime by remember { mutableStateOf("8:00 PM") }
    var deliveryAreas by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    GlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 540.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("seller_registration_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = lStr(R.string.action_back),
                            tint = DeepBlueText
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lStr(R.string.seller_reg_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = DeepBlueText
                        )
                        Text(
                            text = lStr(R.string.seller_reg_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    OutlinedTextField(
                        value = messName,
                        onValueChange = { messName = it },
                        label = { Text(lStr(R.string.field_mess_name_req)) },
                        placeholder = { Text(lStr(R.string.placeholder_mess_name)) },
                        leadingIcon = { Icon(Icons.Outlined.Storefront, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("seller_mess_name_input")
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = area,
                            onValueChange = { area = it },
                            label = { Text(lStr(R.string.field_area_req)) },
                            placeholder = { Text(lStr(R.string.placeholder_area)) },
                            leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("seller_area_input")
                        )
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text(lStr(R.string.field_city_req)) },
                            placeholder = { Text(lStr(R.string.placeholder_city)) },
                            leadingIcon = { Icon(Icons.Outlined.LocationCity, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("seller_city_input")
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(lStr(R.string.field_seller_mobile_req)) },
                        placeholder = { Text(lStr(R.string.placeholder_mobile)) },
                        leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("seller_phone_input")
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = lStr(R.string.label_food_type_req),
                        style = MaterialTheme.typography.labelLarge,
                        color = DeepBlueText
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VegType.entries.forEach { option ->
                            val selected = vegType == option
                            FilterChip(
                                selected = selected,
                                onClick = { vegType = option },
                                label = { Text(option.localizedLabel()) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SkyPrimary.copy(alpha = 0.18f),
                                    selectedLabelColor = DeepBlueText
                                ),
                                modifier = Modifier.testTag("seller_veg_chip_${option.dbValue}")
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = monthlyPrice,
                        onValueChange = { monthlyPrice = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text(lStr(R.string.field_monthly_price_req)) },
                        placeholder = { Text("2500") },
                        leadingIcon = { Icon(Icons.Outlined.CurrencyRupee, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("seller_price_input")
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = lunchTime,
                            onValueChange = { lunchTime = it },
                            label = { Text(lStr(R.string.field_lunch_time)) },
                            placeholder = { Text("12:30 PM") },
                            leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("seller_lunch_time_input")
                        )
                        OutlinedTextField(
                            value = dinnerTime,
                            onValueChange = { dinnerTime = it },
                            label = { Text(lStr(R.string.field_dinner_time)) },
                            placeholder = { Text("8:00 PM") },
                            leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("seller_dinner_time_input")
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = deliveryAreas,
                        onValueChange = { deliveryAreas = it },
                        label = { Text(lStr(R.string.field_delivery_areas)) },
                        placeholder = { Text(lStr(R.string.placeholder_delivery_areas)) },
                        leadingIcon = { Icon(Icons.Outlined.LocalShipping, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("seller_delivery_areas_input")
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(lStr(R.string.field_about_food)) },
                        placeholder = { Text(lStr(R.string.placeholder_about_food)) },
                        leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("seller_description_input")
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        ErrorNoticeCard(message = errorMessage)
                    }

                    Spacer(Modifier.height(18.dp))

                    SkyPrimaryButton(
                        text = lStr(R.string.register_my_mess_btn),
                        onClick = {
                            onSubmit(
                                messName,
                                area,
                                city,
                                phone,
                                vegType,
                                monthlyPrice,
                                lunchTime,
                                dinnerTime,
                                deliveryAreas,
                                description
                            )
                        },
                        loading = isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("seller_register_submit_button")
                    )
                }
            }
        }
    }
}

@Composable
fun SellerRegistrationSuccessScreen(
    result: SellerRegistrationResult,
    isBusy: Boolean,
    onGoToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val shareText = lStr(R.string.share_code_message, result.messName, result.registrationCode)
    val shareChooserTitle = lStr(R.string.share_code_chooser)

    GlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GlassCard(
                modifier = Modifier.widthIn(max = 480.dp),
                strongSurface = true
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    StatusPill(label = lStr(R.string.seller_success_pill), tone = PillTone.SUCCESS)
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = result.messName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = DeepBlueText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = lStr(R.string.seller_success_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(18.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(SkyPrimary.copy(alpha = 0.10f))
                            .border(1.dp, SkyPrimary.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                            .padding(vertical = 18.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = lStr(R.string.seller_code_caption),
                            style = MaterialTheme.typography.labelMedium,
                            color = SecondaryText
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = result.registrationCode,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp
                            ),
                            color = DeepBlueText,
                            modifier = Modifier.testTag("seller_generated_code_text")
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, shareChooserTitle))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("seller_share_code_button")
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(lStr(R.string.share_code_btn))
                    }

                    Spacer(Modifier.height(10.dp))

                    SkyPrimaryButton(
                        text = lStr(R.string.go_to_admin_dashboard_btn),
                        onClick = onGoToDashboard,
                        loading = isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("seller_go_to_dashboard_button")
                    )
                }
            }
        }
    }
}
