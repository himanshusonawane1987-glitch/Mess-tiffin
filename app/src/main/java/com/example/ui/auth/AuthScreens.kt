package com.example.ui.auth

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
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.Profile
import com.example.domain.RegistrationRequest
import com.example.ui.theme.DeepBlueText
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.ErrorCoralContainer
import com.example.ui.theme.GlassBackdrop
import com.example.ui.theme.GlassCard
import com.example.ui.theme.PillTone
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SkyPrimary
import com.example.ui.theme.SkyPrimaryButton
import com.example.ui.theme.StatusPill
import com.example.ui.theme.WarningAmber

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
                        text = "Loading Tiffin Mess...",
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
                // Brand Badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(SkyPrimary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restaurant,
                        contentDescription = "Tiffin Mess Logo",
                        tint = SkyPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Tiffin Mess",
                    style = MaterialTheme.typography.displaySmall,
                    color = DeepBlueText,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Your daily tiffin, sorted.",
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
                        contentDescription = "Tiffin Mess glassmorphism lunchbox illustration",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Daily delivery status, monthly counts, published menus & transparent billing in one place.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(18.dp))

                    SkyPrimaryButton(
                        text = "Continue with Google",
                        onClick = onContinueWithGoogle,
                        enabled = isSupabaseConfigured,
                        loading = isBusy,
                        icon = Icons.Outlined.Login,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("continue_with_google_button")
                    )
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
                                    text = "Supabase Setup Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DeepBlueText
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Add SUPABASE_URL and SUPABASE_ANON_KEY in the AI Studio Secrets panel, apply the SQL migrations in supabase/migrations/, and enable Google OAuth with redirect URL tiffinmess://auth/callback.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SecondaryText
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(onClick = onRetryRestore) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Re-check Config")
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
fun CustomerRegistrationScreen(
    profile: Profile,
    isBusy: Boolean,
    errorMessage: String?,
    onSubmit: (code: String, fullName: String, phone: String, address: String) -> Unit,
    onSignOut: () -> Unit
) {
    var registrationCode by remember { mutableStateOf("") }
    var fullName by remember(profile.fullName) { mutableStateOf(profile.fullName) }
    var phone by remember(profile.phone) { mutableStateOf(profile.phone) }
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Join Your Mess",
                            style = MaterialTheme.typography.headlineMedium,
                            color = DeepBlueText
                        )
                        Text(
                            text = "Enter the mess registration code shared by your mess owner.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                GlassCard(modifier = Modifier.fillMaxWidth(), strongSurface = true) {
                    StatusPill(
                        label = "Signed in as ${profile.email.ifBlank { "Google Account" }}",
                        tone = PillTone.INFO,
                        icon = Icons.Outlined.CheckCircle
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = registrationCode,
                        onValueChange = { registrationCode = it.uppercase() },
                        label = { Text("Mess Registration Code *") },
                        placeholder = { Text("e.g. ANNAPURNA2026") },
                        leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("registration_code_input")
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name *") },
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
                        label = { Text("Mobile Number *") },
                        placeholder = { Text("+91 9876543210") },
                        leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("registration_phone_input")
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Room / Hostel / Delivery Address (Optional)") },
                        leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("registration_address_input")
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        ErrorNoticeCard(message = errorMessage)
                    }

                    Spacer(Modifier.height(18.dp))

                    SkyPrimaryButton(
                        text = "Submit Registration Request",
                        onClick = { onSubmit(registrationCode, fullName, phone, address) },
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
                        Text("Use Another Google Account")
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
    AccountStatusTemplate(
        icon = Icons.Outlined.HourglassTop,
        pillLabel = "Awaiting Mess Owner Approval",
        pillTone = PillTone.WARNING,
        title = "Request Submitted!",
        body = buildString {
            append("Hi ${profile.fullName.ifBlank { profile.email }}, your request to join ")
            append(messName ?: "the mess")
            append(" is pending admin verification. Once approved, your daily tiffin dashboard will unlock automatically.")
            if (request != null) {
                append("\n\nRegistered Mobile: ${request.phone}")
            }
        },
        primaryText = if (isBusy) "Checking..." else "Refresh Approval Status",
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
    AccountStatusTemplate(
        icon = Icons.Outlined.WarningAmber,
        pillLabel = "Registration Declined",
        pillTone = PillTone.ERROR,
        title = "Request Not Approved",
        body = "Your registration request for ${messName ?: "this mess"} was declined. Please verify your mess registration code with the mess owner or try submitting again.",
        primaryText = "Enter Another Mess Code",
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
        pillLabel = "Account Suspended",
        pillTone = PillTone.ERROR,
        title = "Access Temporarily Paused",
        body = "Your account (${profile.email}) is currently suspended by the mess administrator. Please contact your mess owner to reactivate access.",
        primaryText = "Check Status Again",
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
                        Text("Sign Out")
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
