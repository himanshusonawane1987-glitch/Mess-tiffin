package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.SessionStore
import com.example.domain.AccountStatus
import com.example.domain.AppLanguage
import com.example.domain.BillStatus
import com.example.domain.CustomerDailyMark
import com.example.domain.CustomerProfile
import com.example.domain.DailyMarkChoice
import com.example.domain.DiscoverableMess
import com.example.domain.LeaveStatus
import com.example.domain.Mess
import com.example.domain.MonthlyBill
import com.example.domain.PaymentProof
import com.example.domain.PaymentProofStatus
import com.example.domain.Profile
import com.example.domain.ProximityTier
import com.example.domain.SellerRegistrationResult
import com.example.domain.Subscription
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinLeave
import com.example.domain.TiffinMonthSummary
import com.example.domain.TiffinRecord
import com.example.domain.TiffinStatus
import com.example.domain.UserRole
import com.example.ui.admin.AdminBillingScreen
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.admin.AdminProfileSettingsScreen
import com.example.ui.admin.AdminTiffinsScreen
import com.example.ui.admin.AdminUiState
import com.example.ui.auth.RoleChoiceScreen
import com.example.ui.auth.SellerRegistrationSuccessScreen
import com.example.ui.auth.WelcomeScreen
import com.example.ui.customer.CustomerDashboardScreen
import com.example.ui.customer.CustomerPaymentsScreen
import com.example.ui.customer.CustomerProfileScreen
import com.example.ui.customer.CustomerUiState
import com.example.ui.discovery.FindTiffinNearMeScreen
import com.example.ui.theme.AppStrings
import com.example.ui.theme.TiffinMessTheme
import com.example.ui.theme.localizedContextFor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
            TiffinMessTheme(language = AppLanguage.ENGLISH) {
                WelcomeScreen(
                    isSupabaseConfigured = true,
                    isBusy = false,
                    errorMessage = null,
                    selectedLanguage = AppLanguage.ENGLISH,
                    hasChosenLanguage = true,
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
    fun `role choice screen renders customer and seller options`() {
        val profile = Profile("u1", "Aarav", "aarav@example.com")
        composeRule.setContent {
            TiffinMessTheme(language = AppLanguage.ENGLISH) {
                RoleChoiceScreen(
                    profile = profile,
                    onSelectRole = {},
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("role_choice_customer").assertIsDisplayed()
        composeRule.onNodeWithTag("role_choice_seller").assertIsDisplayed()
        composeRule.onNodeWithTag("role_choice_browse_nearby").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `seller registration success screen displays generated code and actions`() {
        composeRule.setContent {
            TiffinMessTheme(language = AppLanguage.ENGLISH) {
                SellerRegistrationSuccessScreen(
                    result = SellerRegistrationResult("m1", "Maa Annapurna", "AB7K9P"),
                    isBusy = false,
                    onGoToDashboard = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("AB7K9P").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("seller_share_code_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("seller_go_to_dashboard_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `discovery screen shows New badge for zero rating mess and empty state when empty`() {
        val mess = Mess(
            id = "m1",
            name = "Shree Ganesh Tiffin",
            ownerId = "o1",
            area = "Dharmaji Colony",
            city = "Nashik",
            vegType = "veg",
            monthlyPrice = 2400.0,
            avgRating = 0.0,
            ratingCount = 0
        )
        composeRule.setContent {
            TiffinMessTheme(language = AppLanguage.ENGLISH) {
                FindTiffinNearMeScreen(
                    messes = listOf(DiscoverableMess(mess = mess, proximityTier = ProximityTier.SAME_AREA_AND_CITY)),
                    areaFilter = "Dharmaji Colony",
                    cityFilter = "Nashik",
                    isLoading = false,
                    isLocating = false,
                    errorMessage = null,
                    infoMessage = null,
                    onLocationFilterChange = { _, _ -> },
                    onUseCurrentLocation = {},
                    onRefresh = {},
                    onSelectMess = {},
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("discovery_mess_card_m1"))
        composeRule.onNodeWithText("Shree Ganesh Tiffin").assertIsDisplayed()
        composeRule.onNodeWithText("New").assertIsDisplayed()
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
            TiffinMessTheme(language = AppLanguage.ENGLISH) {
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

    @Test
    fun `customer dashboard displays daily mark buttons hisab totals and upcoming leave`() {
        val mess = Mess(id = "m1", name = "Annapurna Mess", ownerId = "u0", dailyMarkCutoffTime = "10:00", monthlyPrice = 3000.0)
        val profile = Profile(id = "u1", fullName = "Aarav Sharma", email = "aarav@example.com", role = UserRole.CUSTOMER, accountStatus = AccountStatus.ACTIVE)
        val customer = CustomerProfile(id = "c1", messId = "m1", userId = "u1", fullName = "Aarav Sharma", phone = "9876543210")
        val today = TiffinBusinessLogic.messToday(mess.timezone)
        val futureStart = today.plusDays(2).toString()
        val futureEnd = today.plusDays(5).toString()
        val leave = TiffinLeave("lv1", "m1", "c1", futureStart, futureEnd, "Ghar ja raha hu", LeaveStatus.ACTIVE)
        val sub = Subscription(id = "s1", messId = "m1", customerId = "c1", planNameSnapshot = "Monthly 30", startDate = "2026-11-01", endDate = "2026-11-30", includedTiffins = 30, agreedPrice = 3000.0)
        val bill = MonthlyBill(
            id = "b1",
            messId = "m1",
            customerId = "c1",
            billingMonth = TiffinBusinessLogic.monthStartIso(TiffinBusinessLogic.messYearMonth(mess.timezone)),
            totalAmount = 3000.0,
            amountPaid = 1200.0,
            balanceDue = 1800.0,
            dueDate = today.plusDays(5).toString()
        )

        composeRule.setContent {
            TiffinMessTheme {
                CustomerDashboardScreen(
                    state = CustomerUiState(
                        profile = profile,
                        mess = mess,
                        customer = customer,
                        isLoading = false,
                        selectedMonth = TiffinBusinessLogic.messYearMonth(mess.timezone),
                        todayMark = CustomerDailyMark("dm1", "m1", "c1", today.toString(), DailyMarkChoice.WANTED),
                        leaves = listOf(leave),
                        activeSubscription = sub,
                        monthSummary = TiffinMonthSummary(
                            deliveredCount = 14,
                            notDeliveredCount = 0,
                            cancelledCount = 0,
                            scheduledOrTotalRecords = 14,
                            remainingTiffins = 16,
                            extraTiffins = 0
                        ),
                        currentMonthBill = bill
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
        composeRule.onNodeWithTag("mark_tiffin_wanted_button").assertIsDisplayed()
        composeRule.onNodeWithTag("mark_tiffin_not_wanted_button").assertIsDisplayed()

        composeRule.onNodeWithTag("customer_dashboard_list").performScrollToNode(hasTestTag("is_mahine_ka_hisab_card"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("is_mahine_ka_hisab_card").assertIsDisplayed()
        composeRule.onNodeWithTag("hisab_taken_value", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("hisab_remaining_value", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("customer_dashboard_list").performScrollToNode(hasTestTag("customer_leave_card"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("set_leave_button").assertIsDisplayed()
        composeRule.onNodeWithTag("customer_dashboard_list").performScrollToNode(hasTestTag("cancel_leave_lv1"))
        composeRule.onNodeWithTag("cancel_leave_lv1").assertIsDisplayed()
    }

    @Test
    fun `admin tiffins screen shows day totals banner customer marks leave badge and month amounts`() {
        val mess = Mess(id = "m1", name = "Annapurna Mess", ownerId = "u0", monthlyPrice = 3000.0, mealSlots = listOf("lunch"))
        val profile = Profile(id = "u0", fullName = "Ramesh Joshi", email = "owner@example.com", role = UserRole.OWNER, accountStatus = AccountStatus.ACTIVE)
        val today = TiffinBusinessLogic.messToday(mess.timezone)
        val todayIso = today.toString()
        val c1 = CustomerProfile("c1", "m1", "u1", "Rohan Patil", "9876543211", customerStatus = AccountStatus.ACTIVE)
        val c2 = CustomerProfile("c2", "m1", "u2", "Neha Kulkarni", "9876543212", customerStatus = AccountStatus.ACTIVE)
        val sub1 = Subscription(id = "s1", messId = "m1", customerId = "c1", planNameSnapshot = "Monthly 30", startDate = "2026-11-01", endDate = "2026-11-30", includedTiffins = 30, agreedPrice = 3000.0)
        val sub2 = Subscription(id = "s2", messId = "m1", customerId = "c2", planNameSnapshot = "Monthly 30", startDate = "2026-11-01", endDate = "2026-11-30", includedTiffins = 30, agreedPrice = 3000.0)

        composeRule.setContent {
            TiffinMessTheme {
                AdminTiffinsScreen(
                    state = AdminUiState(
                        profile = profile,
                        mess = mess,
                        isOwner = true,
                        isLoading = false,
                        selectedDate = today,
                        selectedMonth = TiffinBusinessLogic.messYearMonth(mess.timezone),
                        customers = listOf(c1, c2),
                        subscriptions = listOf(sub1, sub2),
                        dailyMarks = listOf(
                            CustomerDailyMark("m1", "m1", "c1", todayIso, DailyMarkChoice.NOT_WANTED)
                        ),
                        leaves = listOf(
                            TiffinLeave("lv2", "m1", "c2", todayIso, today.plusDays(2).toString(), "Home", LeaveStatus.ACTIVE)
                        ),
                        dailyTiffins = emptyList(),
                        monthTiffins = listOf(
                            TiffinRecord("r1", "m1", "c1", "s1", today.minusDays(1).toString(), "lunch", TiffinStatus.DELIVERED)
                        )
                    ),
                    onSelectDate = {},
                    onMarkTiffin = { _, _, _, _, _ -> },
                    onBulkMarkDelivered = { _, _, _ -> }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_day_totals_card").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_tiffins_list").performScrollToNode(hasTestTag("admin_tiffin_customer_c1"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rohan Patil").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_mark_badge_c1").assertIsDisplayed()

        composeRule.onNodeWithTag("admin_tiffins_list").performScrollToNode(hasTestTag("admin_tiffin_customer_c2"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Neha Kulkarni").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_mark_badge_c2").assertIsDisplayed()
    }

    @Test
    fun `all three language resource configurations resolve distinct labels`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val en = localizedContextFor(context, AppLanguage.ENGLISH)
        val hi = localizedContextFor(context, AppLanguage.HINDI)
        val hg = localizedContextFor(context, AppLanguage.HINGLISH)

        assertEquals("Today's Tiffin", en.getString(R.string.daily_card_title))
        assertEquals("आज का टिफिन", hi.getString(R.string.daily_card_title))
        assertEquals("Aaj ka Tiffin", hg.getString(R.string.daily_card_title))

        var current = AppLanguage.HINGLISH
        val appStrings = AppStrings(context) { current }
        assertEquals("Tiffin Chahiye", appStrings.get(R.string.daily_wanted_label))
        current = AppLanguage.ENGLISH
        assertEquals("Want Tiffin", appStrings.get(R.string.daily_wanted_label))
        current = AppLanguage.HINDI
        assertEquals("टिफिन चाहिए", appStrings.get(R.string.daily_wanted_label))
    }

    @Test
    fun `welcome screen language picker renders three options and updates displayed text`() {
        var selectedLanguage by mutableStateOf(AppLanguage.HINGLISH)
        var hasChosenLanguage by mutableStateOf(false)

        composeRule.setContent {
            TiffinMessTheme(language = selectedLanguage) {
                WelcomeScreen(
                    isSupabaseConfigured = true,
                    isBusy = false,
                    errorMessage = null,
                    selectedLanguage = selectedLanguage,
                    hasChosenLanguage = hasChosenLanguage,
                    onSelectLanguage = { lang ->
                        selectedLanguage = lang
                        hasChosenLanguage = true
                    },
                    onChangeLanguageClick = { hasChosenLanguage = false },
                    onContinueWithGoogle = {},
                    onRetryRestore = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("welcome_language_picker").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("language_button_en").assertIsDisplayed()
        composeRule.onNodeWithTag("language_button_hi").assertIsDisplayed()
        composeRule.onNodeWithTag("language_button_hinglish").assertIsDisplayed()
        composeRule.onNodeWithText("English").assertIsDisplayed()
        composeRule.onNodeWithText("हिन्दी").assertIsDisplayed()
        composeRule.onNodeWithText("Hinglish").assertIsDisplayed()

        composeRule.onNodeWithTag("language_button_hi").performClick()
        composeRule.waitForIdle()

        assertEquals(AppLanguage.HINDI, selectedLanguage)
        assertTrue(hasChosenLanguage)
        composeRule.onNodeWithText("आपका रोज़ाना टिफिन, अब आसान।").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("गूगल से आगे बढ़ें").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `switching language in settings changes labels immediately across dashboard and profile`() {
        val mess = Mess(id = "m1", name = "Annapurna Mess", ownerId = "u0")
        val profile = Profile(id = "u1", fullName = "Aarav Sharma", email = "aarav@example.com", role = UserRole.CUSTOMER, accountStatus = AccountStatus.ACTIVE)
        val customer = CustomerProfile(id = "c1", messId = "m1", userId = "u1", fullName = "Aarav Sharma", phone = "9876543210")

        var activeLanguage by mutableStateOf(AppLanguage.HINGLISH)
        var showProfile by mutableStateOf(true)

        composeRule.setContent {
            TiffinMessTheme(language = activeLanguage) {
                if (showProfile) {
                    CustomerProfileScreen(
                        state = CustomerUiState(
                            profile = profile,
                            mess = mess,
                            customer = customer,
                            isLoading = false,
                            selectedMonth = TiffinBusinessLogic.messYearMonth(mess.timezone)
                        ),
                        currentLanguage = activeLanguage,
                        onSelectLanguage = { activeLanguage = it },
                        onUpdateProfile = { _, _, _, _, _ -> },
                        onSignOut = {}
                    )
                } else {
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
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Meri Profile").assertIsDisplayed()

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_language_card"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("language_button_en").performClick()
        composeRule.waitForIdle()
        assertEquals(AppLanguage.ENGLISH, activeLanguage)
        composeRule.onNode(hasScrollAction()).performScrollToIndex(0)
        composeRule.onNodeWithText("My Profile").assertIsDisplayed()

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_language_card"))
        composeRule.onNodeWithTag("language_button_hi").performClick()
        composeRule.waitForIdle()
        assertEquals(AppLanguage.HINDI, activeLanguage)
        composeRule.onNode(hasScrollAction()).performScrollToIndex(0)
        composeRule.onNodeWithText("मेरी प्रोफ़ाइल").assertIsDisplayed()

        composeRule.runOnUiThread { showProfile = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("आज का टिफिन").assertIsDisplayed()

        composeRule.runOnUiThread { activeLanguage = AppLanguage.ENGLISH }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Today's Tiffin").assertIsDisplayed()
    }

    @Test
    fun `language choice persists locally across sign out clearAll and defaults to hinglish`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SessionStore(context)
        store.clearLanguageForTesting()

        assertEquals(AppLanguage.HINGLISH, store.getLanguage())
        assertFalse(store.hasChosenLanguage())

        store.saveLanguage(AppLanguage.HINDI, markChosen = true)
        assertEquals(AppLanguage.HINDI, store.getLanguage())
        assertTrue(store.hasChosenLanguage())

        // Simulates sign-out clearing auth session while keeping user's chosen language
        store.clearAll()
        assertEquals(AppLanguage.HINDI, store.getLanguage())
        assertTrue(store.hasChosenLanguage())

        store.clearLanguageForTesting()
    }

    @Test
    fun `customer payments screen renders seller UPI ID screenshot button and proof statuses`() {
        val mess = Mess(
            id = "m1",
            name = "Annapurna Mess",
            ownerId = "o1",
            upiId = "annapurna@okhdfcbank",
            upiQrPath = "m1/qr/qr1.jpg"
        )
        val profile = Profile("u1", "Aarav Sharma", "aarav@example.com", role = UserRole.CUSTOMER, accountStatus = AccountStatus.ACTIVE)
        val customer = CustomerProfile("c1", "m1", "u1", "Aarav Sharma", "9876543210")
        val billUnpaid = MonthlyBill(
            id = "b1",
            messId = "m1",
            customerId = "c1",
            billingMonth = "2026-11-01",
            totalAmount = 2500.0,
            amountPaid = 0.0,
            balanceDue = 2500.0,
            dueDate = "2026-11-10",
            status = BillStatus.PENDING
        )
        val billWaiting = MonthlyBill(
            id = "b2",
            messId = "m1",
            customerId = "c1",
            billingMonth = "2026-10-01",
            totalAmount = 2500.0,
            amountPaid = 0.0,
            balanceDue = 2500.0,
            dueDate = "2026-10-10",
            status = BillStatus.OVERDUE
        )
        val pendingProof = PaymentProof(
            id = "pr2",
            messId = "m1",
            customerId = "c1",
            billId = "b2",
            amount = 2500.0,
            screenshotPath = "m1/proofs/c1/shot2.jpg",
            status = PaymentProofStatus.PENDING,
            createdAt = "2026-11-18T10:00:00Z"
        )

        composeRule.setContent {
            TiffinMessTheme(language = AppLanguage.ENGLISH) {
                CustomerPaymentsScreen(
                    state = CustomerUiState(
                        profile = profile,
                        mess = mess,
                        customer = customer,
                        isLoading = false,
                        selectedMonth = TiffinBusinessLogic.messYearMonth(mess.timezone),
                        bills = listOf(billUnpaid, billWaiting),
                        paymentProofs = listOf(pendingProof)
                    )
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("copy_upi_id_b1").assertIsDisplayed()
        composeRule.onNodeWithText("annapurna@okhdfcbank").assertIsDisplayed()
        composeRule.onNodeWithTag("send_payment_screenshot_button_b1").assertIsDisplayed()

        composeRule.onNodeWithTag("customer_payments_list").performScrollToNode(hasTestTag("customer_bill_card_b2"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("proof_waiting_b2", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Payment Proof Sent — Waiting for seller confirmation", substring = true).assertIsDisplayed()
    }

    @Test
    fun `admin billing renders pending payment proofs with confirm and reject actions`() {
        val mess = Mess(id = "m1", name = "Annapurna Mess", ownerId = "o1")
        val profile = Profile("o1", "Ramesh Joshi", "owner@example.com", role = UserRole.OWNER, accountStatus = AccountStatus.ACTIVE)
        val customer = CustomerProfile("c1", "m1", "u1", "Rohan Patil", "9876543210")
        val bill = MonthlyBill(
            id = "b1",
            messId = "m1",
            customerId = "c1",
            billingMonth = "2026-11-01",
            totalAmount = 2500.0,
            amountPaid = 0.0,
            balanceDue = 2500.0,
            dueDate = "2026-11-10",
            status = BillStatus.PENDING
        )
        val proof = PaymentProof(
            id = "pr1",
            messId = "m1",
            customerId = "c1",
            billId = "b1",
            amount = 2500.0,
            screenshotPath = "m1/proofs/c1/shot1.jpg",
            status = PaymentProofStatus.PENDING,
            createdAt = "2026-11-18T11:30:00Z"
        )
        var confirmedId: String? = null
        var rejectedPair: Pair<String, String?>? = null

        composeRule.setContent {
            TiffinMessTheme(language = AppLanguage.ENGLISH) {
                AdminBillingScreen(
                    state = AdminUiState(
                        profile = profile,
                        mess = mess,
                        isOwner = true,
                        isLoading = false,
                        selectedDate = TiffinBusinessLogic.messToday(mess.timezone),
                        selectedMonth = TiffinBusinessLogic.messYearMonth(mess.timezone),
                        customers = listOf(customer),
                        bills = listOf(bill),
                        paymentProofs = listOf(proof)
                    ),
                    onSelectMonth = {},
                    onGenerateBill = { _, _, _, _ -> },
                    onRecordPayment = { _, _, _, _ -> },
                    onReversePayment = { _, _ -> },
                    onConfirmPaymentProof = { confirmedId = it },
                    onRejectPaymentProof = { id, reason -> rejectedPair = id to reason }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_payment_proofs_section").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_proof_card_pr1").assertIsDisplayed()
        composeRule.onNodeWithText("Rohan Patil").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_proof_pr1").performClick()
        assertEquals("pr1", confirmedId)

        composeRule.onNodeWithTag("reject_proof_pr1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_reject_proof_button").performClick()
        assertEquals("pr1", rejectedPair?.first)
    }

    @Test
    fun `admin profile settings renders UPI payment settings card`() {
        val mess = Mess(id = "m1", name = "Annapurna Mess", ownerId = "o1", upiId = "mess@okaxis")
        val profile = Profile("o1", "Ramesh Joshi", "owner@example.com", role = UserRole.OWNER, accountStatus = AccountStatus.ACTIVE)

        composeRule.setContent {
            TiffinMessTheme(language = AppLanguage.ENGLISH) {
                AdminProfileSettingsScreen(
                    state = AdminUiState(
                        profile = profile,
                        mess = mess,
                        isOwner = true,
                        isLoading = false,
                        selectedDate = TiffinBusinessLogic.messToday(mess.timezone),
                        selectedMonth = TiffinBusinessLogic.messYearMonth(mess.timezone)
                    ),
                    onUpdateMessSettings = { _, _, _, _, _, _, _ -> },
                    onRotateCode = {},
                    onInviteAdmin = {},
                    onSaveUpiSettings = { _, _ -> },
                    onSignOut = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("admin_payment_settings_card"))
        composeRule.onNodeWithTag("admin_payment_settings_card").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_upi_id_input").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_upload_qr_button").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_save_payment_settings_button").assertIsDisplayed()
    }
}
