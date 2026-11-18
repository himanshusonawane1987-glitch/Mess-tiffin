package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.domain.AccountStatus
import com.example.domain.CustomerProfile
import com.example.domain.Mess
import com.example.domain.Profile
import com.example.domain.TiffinBusinessLogic
import com.example.domain.UserRole
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.admin.AdminUiState
import com.example.ui.auth.WelcomeScreen
import com.example.ui.customer.CustomerDashboardScreen
import com.example.ui.customer.CustomerUiState
import com.example.ui.theme.TiffinMessTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Tiffin Mess", appName)
    }

    @Test
    fun `welcome screen renders title tagline and google button`() {
        composeRule.setContent {
            TiffinMessTheme {
                WelcomeScreen(
                    isSupabaseConfigured = true,
                    isBusy = false,
                    errorMessage = null,
                    onContinueWithGoogle = {},
                    onRetryRestore = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tiffin Mess").assertIsDisplayed()
        composeRule.onNodeWithText("Your daily tiffin, sorted.").assertIsDisplayed()
        composeRule.onNodeWithTag("continue_with_google_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `customer dashboard renders today status card without admin controls`() {
        val mess = Mess(id = "m1", name = "Annapurna Mess", ownerId = "u0")
        val profile = Profile(id = "u1", fullName = "Aarav Sharma", email = "aarav@example.com", role = UserRole.CUSTOMER, accountStatus = AccountStatus.ACTIVE)
        val customer = CustomerProfile(id = "c1", messId = "m1", userId = "u1", fullName = "Aarav Sharma", phone = "9876543210")

        composeRule.setContent {
            TiffinMessTheme {
                CustomerDashboardScreen(
                    state = CustomerUiState(
                        profile = profile,
                        mess = mess,
                        customer = customer,
                        isLoading = false,
                        selectedMonth = TiffinBusinessLogic.messYearMonth(mess.timezone)
                    ),
                    onRefresh = {},
                    onNavigateToHistory = {},
                    onNavigateToMenu = {},
                    onNavigateToSubscription = {},
                    onNavigateToPayments = {},
                    onNavigateToProfile = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today_tiffin_status_card").assertIsDisplayed()
        composeRule.onNodeWithText("Aaj ka Tiffin").assertIsDisplayed()
    }

    @Test
    fun `admin dashboard renders overview and quick actions`() {
        val mess = Mess(id = "m1", name = "Annapurna Mess", ownerId = "u0")
        val profile = Profile(id = "u0", fullName = "Ramesh Joshi", email = "owner@example.com", role = UserRole.OWNER, accountStatus = AccountStatus.ACTIVE)

        composeRule.setContent {
            TiffinMessTheme {
                AdminDashboardScreen(
                    state = AdminUiState(
                        profile = profile,
                        mess = mess,
                        isOwner = true,
                        isLoading = false,
                        selectedDate = TiffinBusinessLogic.messToday(mess.timezone),
                        selectedMonth = TiffinBusinessLogic.messYearMonth(mess.timezone)
                    ),
                    onRefresh = {},
                    onOpenTiffins = {},
                    onOpenMenu = {},
                    onOpenCustomers = {},
                    onOpenBilling = {},
                    onOpenSubscriptions = {},
                    onOpenReports = {},
                    onOpenProfile = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Today's Overview").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("quick_action_billing"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_action_tiffins").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_action_billing").assertIsDisplayed()
    }
}
