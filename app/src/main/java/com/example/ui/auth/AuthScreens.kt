package com.example.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AppLanguage
import com.example.domain.DiscoveryJoinPrefill
import com.example.domain.OnboardingRoleChoice
import com.example.domain.Profile
import com.example.domain.RegistrationRequest
import com.example.ui.theme.DeepBlueText
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.ErrorCoralContainer
import com.example.ui.theme.GlassBackdrop
import com.example.ui.theme.GlassCard
import com.example.ui.theme.LanguagePickerButtons
import com.example.ui.theme.PillTone
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SkyPrimary
import com.example.ui.theme.SkyPrimaryButton
import com.example.ui.theme.StatusPill
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.lStr

@Composable
fun AuthLoadingScreen() {
    GlassBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.padding(24.dp),
                strongSurface = true
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    CircularProgressIndicator(color = SkyPrimary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = lStr(R.string.loading_app),
                        style = MaterialTheme.typography.titleMedium,
                        color = DeepBlueText
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(
    isSupabaseConfigured: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    selectedLanguage: AppLanguage = AppLanguage.DEFAULT,
    hasChosenLanguage: Boolean = true,
    onSelectLanguage: (AppLanguage) -> Unit = {},
    onChangeLanguageClick: () -> Unit = {},
    onContinueWithGoogle: () -> Unit,
    onRetryRestore: () -> Unit
) {
    GlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(SkyPrimary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restaurant,
                        contentDescription = lStr(R.string.welcome_logo_cd),
                        tint = SkyPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = lStr(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = DeepBlueText,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = lStr(R.string.welcome_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = SecondaryText,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(22.dp))

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    strongSurface = true
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_tiffin_welcome),
                        contentDescription = lStr(R.string.welcome_art_cd),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(16.dp))

                    if (!hasChosenLanguage) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("welcome_language_picker"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = lStr(R.string.lang_picker_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepBlueText,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = lStr(R.string.lang_picker_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            LanguagePickerButtons(
                                selectedLanguage = selectedLanguage,
                                onSelectLanguage = onSelectLanguage,
                                highlightSelected = false
                            )
                        }
                    } else {
                        Text(
                            text = lStr(R.string.welcome_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(18.dp))

                        SkyPrimaryButton(
                            text = lStr(R.string.welcome_google_btn),
                            onClick = onContinueWithGoogle,
                            enabled = isSupabaseConfigured,
                            loading = isBusy,
                            icon = Icons.Outlined.Login,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("continue_with_google_button")
                        )

                        Spacer(Modifier.height(10.dp))

                        TextButton(
                            onClick = onChangeLanguageClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("welcome_change_language_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null,
                                tint = SkyPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${selectedLanguage.buttonLabel} • ${lStr(R.string.lang_change_button)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = DeepBlueText
                            )
                        }
                    }
                }

                if (!isSupabaseConfigured) {
                    Spacer(Modifier.height(16.dp))
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = lStr(R.string.supabase_setup_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DeepBlueText
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = lStr(R.string.supabase_setup_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SecondaryText
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(onClick = onRetryRestore) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(lStr(R.string.recheck_config))
                                }
                            }
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    ErrorNoticeCard(message = errorMessage)
                }
            }
        }
    }
}

@Composable
fun RoleChoiceScreen(
    profile: Profile,
    onSelectRole: (OnboardingRoleChoice) -> Unit,
    onSignOut: () -> Unit
) {
    GlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 520.dp)) {
                StatusPill(
                    label = lStr(R.string.signed_in_as, profile.email.ifBlank { lStr(R.string.google_account_fallback) }),
                    tone = PillTone.INFO,
                    icon = Icons.Outlined.CheckCircle
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = lStr(R.string.role_choice_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = DeepBlueText
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = lStr(R.string.role_choice_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText
                )

                Spacer(Modifier.height(20.dp))

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("role_choice_customer"),
                    strongSurface = true,
                    onClick = { onSelectRole(OnboardingRoleChoice.CUSTOMER) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SkyPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Restaurant,
                                contentDescription = null,
                                tint = SkyPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = lStr(R.string.role_customer_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepBlueText
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = lStr(R.string.role_customer_sub),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("role_choice_seller"),
                    strongSurface = true,
                    onClick = { onSelectRole(OnboardingRoleChoice.SELLER) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SkyPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Storefront,
                                contentDescription = null,
                                tint = SkyPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = lStr(R.string.role_seller_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepBlueText
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = lStr(R.string.role_seller_sub),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("role_choice_browse_nearby"),
                    onClick = { onSelectRole(OnboardingRoleChoice.BROWSE_NEARBY) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Explore,
                            contentDescription = null,
                            tint = SkyPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = lStr(R.string.role_browse_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = DeepBlueText
                            )
                            Text(
                                text = lStr(R.string.role_browse_sub),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryText
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("role_choice_signout_button")
                ) {
                    Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(lStr(R.string.use_another_google))
                }
            }
        }
    }
}

@Composable
fun CustomerRegistrationScreen(
    profile: Profile,
    prefill: DiscoveryJoinPrefill? = null,
    isBusy: Boolean,
    errorMessage: String?,
    infoMessage: String? = null,
    onBrowseNearby: (() -> Unit)? = null,
    onClearPrefill: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onSubmit: (code: String, fullName: String, phone: String, address: String, area: String, city: String) -> Unit,
    onSignOut: () -> Unit
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    var registrationCode by remember(prefill?.messId) {
        mutableStateOf(prefill?.autoFilledCodeSlot.orEmpty())
    }
    var fullName by remember(profile.fullName) { mutableStateOf(profile.fullName) }
    var phone by remember(profile.phone) { mutableStateOf(profile.phone) }
    var area by remember(profile.area, prefill?.area) {
        mutableStateOf(profile.area.ifBlank { prefill?.area.orEmpty() })
    }
    var city by remember(profile.city, prefill?.city) {
        mutableStateOf(profile.city.ifBlank { prefill?.city.orEmpty() })
    }
    var address by remember { mutableStateOf("") }

    GlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 520.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("customer_registration_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = lStr(R.string.action_back),
                                tint = DeepBlueText
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lStr(R.string.join_mess_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = DeepBlueText
                        )
                        Text(
                            text = lStr(R.string.join_mess_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    StatusPill(
                        label = lStr(R.string.signed_in_as, profile.email.ifBlank { lStr(R.string.google_account_fallback) }),
                        tone = PillTone.INFO,
                        icon = Icons.Outlined.CheckCircle
                    )

                    if (prefill != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SkyPrimary.copy(alpha = 0.10f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lStr(R.string.joining_mess_label, prefill.messName),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DeepBlueText
                                )
                                Text(
                                    text = listOf(prefill.area, prefill.city).filter { it.isNotBlank() }.joinToString(", "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SecondaryText
                                )
                            }
                            if (onClearPrefill != null) {
                                TextButton(
                                    onClick = {
                                        registrationCode = ""
                                        onClearPrefill()
                                    },
                                    modifier = Modifier.testTag("clear_discovery_prefill_button")
                                ) {
                                    Text(lStr(R.string.action_change))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = if (prefill != null) lStr(R.string.selected_mess_prefix, prefill.messName) else registrationCode,
                        onValueChange = { if (prefill == null) registrationCode = it.uppercase() },
                        readOnly = prefill != null,
                        label = { Text(lStr(R.string.field_mess_code)) },
                        placeholder = { Text(lStr(R.string.placeholder_mess_code)) },
                        leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("registration_code_input")
                    )

                    if (onBrowseNearby != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onBrowseNearby,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("browse_nearby_messes_button")
                        ) {
                            Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(lStr(R.string.browse_nearby_btn))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text(lStr(R.string.field_full_name_req)) },
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("registration_name_input")
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(lStr(R.string.field_mobile_req)) },
                        placeholder = { Text(lStr(R.string.placeholder_mobile)) },
                        leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("registration_phone_input")
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
                                .testTag("registration_area_input")
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
                                .testTag("registration_city_input")
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(lStr(R.string.field_room_opt)) },
                        leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("registration_address_input")
                    )

                    if (!infoMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        StatusPill(label = infoMessage, tone = PillTone.SUCCESS)
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        ErrorNoticeCard(message = errorMessage)
                    }

                    Spacer(Modifier.height(18.dp))

                    SkyPrimaryButton(
                        text = lStr(R.string.send_join_request_btn),
                        onClick = { onSubmit(registrationCode, fullName, phone, address, area, city) },
                        loading = isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_registration_button")
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("registration_signout_button")
                    ) {
                        Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(lStr(R.string.use_another_google))
                    }
                }
            }
        }
    }
}

@Composable
fun AwaitingApprovalScreen(
    profile: Profile,
    messName: String?,
    request: RegistrationRequest?,
    isBusy: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    val messLabel = messName ?: lStr(R.string.this_mess_fallback)
    val baseBody = lStr(R.string.awaiting_body, messLabel)
    val fullBody = if (request != null) {
        "$baseBody\n\n${lStr(R.string.awaiting_mobile, request.phone)}"
    } else {
        baseBody
    }
    AccountStatusTemplate(
        icon = Icons.Outlined.HourglassTop,
        pillLabel = lStr(R.string.awaiting_pill),
        pillTone = PillTone.WARNING,
        title = lStr(R.string.awaiting_title),
        body = fullBody,
        primaryText = if (isBusy) lStr(R.string.checking_btn) else lStr(R.string.check_again_btn),
        onPrimaryClick = onRefresh,
        isBusy = isBusy,
        errorMessage = errorMessage,
        onSignOut = onSignOut
    )
}

@Composable
fun RejectedRegistrationScreen(
    messName: String?,
    isBusy: Boolean,
    errorMessage: String?,
    onTryAnotherCode: () -> Unit,
    onSignOut: () -> Unit
) {
    val messLabel = messName ?: lStr(R.string.this_mess_fallback)
    AccountStatusTemplate(
        icon = Icons.Outlined.WarningAmber,
        pillLabel = lStr(R.string.rejected_pill),
        pillTone = PillTone.ERROR,
        title = lStr(R.string.rejected_title),
        body = lStr(R.string.rejected_body, messLabel),
        primaryText = lStr(R.string.try_another_code_btn),
        onPrimaryClick = onTryAnotherCode,
        isBusy = isBusy,
        errorMessage = errorMessage,
        onSignOut = onSignOut
    )
}

@Composable
fun SuspendedAccountScreen(
    profile: Profile,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    AccountStatusTemplate(
        icon = Icons.Outlined.Block,
        pillLabel = lStr(R.string.suspended_pill),
        pillTone = PillTone.ERROR,
        title = lStr(R.string.suspended_title),
        body = lStr(R.string.suspended_body, profile.email),
        primaryText = lStr(R.string.check_again_btn),
        onPrimaryClick = onRefresh,
        isBusy = false,
        errorMessage = null,
        onSignOut = onSignOut
    )
}

@Composable
private fun AccountStatusTemplate(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    pillLabel: String,
    pillTone: PillTone,
    title: String,
    body: String,
    primaryText: String,
    onPrimaryClick: () -> Unit,
    isBusy: Boolean,
    errorMessage: String?,
    onSignOut: () -> Unit
) {
    GlassBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(22.dp),
            contentAlignment = Alignment.Center
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
                            .background(SkyPrimary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = SkyPrimary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    StatusPill(label = pillLabel, tone = pillTone)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = DeepBlueText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        textAlign = TextAlign.Center
                    )
                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        ErrorNoticeCard(message = errorMessage)
                    }
                    Spacer(Modifier.height(20.dp))
                    SkyPrimaryButton(
                        text = primaryText,
                        onClick = onPrimaryClick,
                        loading = isBusy,
                        icon = Icons.Outlined.Refresh,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(lStr(R.string.action_sign_out))
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorNoticeCard(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ErrorCoralContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = ErrorCoral,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ErrorCoral,
            fontWeight = FontWeight.Medium
        )
    }
}
