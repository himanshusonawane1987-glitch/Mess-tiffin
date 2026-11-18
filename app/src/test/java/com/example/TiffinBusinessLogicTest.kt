package com.example

import com.example.domain.AccountStatus
import com.example.domain.AuthContextPayload
import com.example.domain.AuthRouteState
import com.example.domain.BillStatus
import com.example.domain.CustomerProfile
import com.example.domain.Mess
import com.example.domain.MessMember
import com.example.domain.Payment
import com.example.domain.PaymentMethod
import com.example.domain.PaymentStatus
import com.example.domain.Profile
import com.example.domain.RegistrationRequest
import com.example.domain.Subscription
import com.example.domain.TiffinBusinessLogic
import com.example.domain.TiffinRecord
import com.example.domain.TiffinStatus
import com.example.domain.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TiffinBusinessLogicTest {

    @Test
    fun `delivered count excludes cancelled and not delivered and floors remaining at zero`() {
        val sub = Subscription(
            id = "s1",
            messId = "m1",
            customerId = "c1",
            planNameSnapshot = "26 Tiffins",
            startDate = "2026-11-01",
            endDate = "2026-11-30",
            includedTiffins = 2,
            agreedPrice = 2000.0
        )
        val records = listOf(
            TiffinRecord("1", "m1", "c1", "s1", "2026-11-01", "lunch", TiffinStatus.DELIVERED),
            TiffinRecord("2", "m1", "c1", "s1", "2026-11-02", "lunch", TiffinStatus.DELIVERED),
            TiffinRecord("3", "m1", "c1", "s1", "2026-11-03", "lunch", TiffinStatus.DELIVERED),
            TiffinRecord("4", "m1", "c1", "s1", "2026-11-04", "lunch", TiffinStatus.CANCELLED),
            TiffinRecord("5", "m1", "c1", "s1", "2026-11-05", "lunch", TiffinStatus.NOT_DELIVERED)
        )

        val summary = TiffinBusinessLogic.calculateMonthlyTiffinSummary(records, sub)
        assertEquals(3, summary.deliveredCount)
        assertEquals(1, summary.cancelledCount)
        assertEquals(1, summary.notDeliveredCount)
        assertEquals(0, summary.remainingTiffins)
        assertEquals(1, summary.extraTiffins)
    }

    @Test
    fun `billing ignores reversed payments and computes overdue and partially paid accurately`() {
        val payments = listOf(
            Payment("p1", "m1", "c1", "b1", 1000.0, PaymentMethod.UPI, "2026-11-02T10:00:00Z", null, "admin1", PaymentStatus.CONFIRMED),
            Payment("p2", "m1", "c1", "b1", 500.0, PaymentMethod.CASH, "2026-11-03T10:00:00Z", null, "admin1", PaymentStatus.REVERSED)
        )

        val partial = TiffinBusinessLogic.calculateMonthlyBillTotals(
            subscriptionCharge = 2000.0,
            extraTiffinCharge = 200.0,
            previousBalance = 300.0,
            discountAmount = 100.0,
            payments = payments,
            dueDateIso = "2026-11-10",
            todayInMessZone = LocalDate.of(2026, 11, 8)
        )
        assertEquals(2400.0, partial.totalPayable, 0.01)
        assertEquals(1000.0, partial.amountPaid, 0.01)
        assertEquals(1400.0, partial.balanceDue, 0.01)
        assertEquals(BillStatus.PARTIALLY_PAID, partial.status)

        val overdue = TiffinBusinessLogic.calculateMonthlyBillTotals(
            subscriptionCharge = 2000.0,
            extraTiffinCharge = 200.0,
            previousBalance = 300.0,
            discountAmount = 100.0,
            payments = payments,
            dueDateIso = "2026-11-10",
            todayInMessZone = LocalDate.of(2026, 11, 15)
        )
        assertEquals(BillStatus.OVERDUE, overdue.status)
    }

    @Test
    fun `role routing never grants admin access unless both profile and membership verify admin`() {
        val mess = Mess("m1", "Annapurna", "owner1")
        val customer = CustomerProfile("c1", "m1", "u1", "Rohan", "9999999999")
        val spoofedProfile = Profile("u1", "Rohan", "rohan@test.com", role = UserRole.ADMIN, accountStatus = AccountStatus.ACTIVE)
        val customerMembership = MessMember("mm1", "m1", "u1", UserRole.CUSTOMER, AccountStatus.ACTIVE)

        val resolved = TiffinBusinessLogic.resolveAuthRouteState(
            AuthContextPayload(
                profile = spoofedProfile,
                membership = customerMembership,
                mess = mess,
                customerProfile = customer
            )
        )
        assertTrue(resolved is AuthRouteState.CustomerActive)
    }

    @Test
    fun `pending registration request routes to awaiting approval`() {
        val profile = Profile("u2", "Neha", "neha@test.com", role = UserRole.CUSTOMER, accountStatus = AccountStatus.PENDING)
        val req = RegistrationRequest("r1", "m1", "u2", "Neha", "9898989898", status = "pending")
        val resolved = TiffinBusinessLogic.resolveAuthRouteState(
            AuthContextPayload(profile = profile, registrationRequest = req)
        )
        assertTrue(resolved is AuthRouteState.AwaitingApproval)
    }
}
