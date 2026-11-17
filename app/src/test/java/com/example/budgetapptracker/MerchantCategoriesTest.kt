package com.example.budgetapptracker

import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.sms.KIND_INVESTMENT
import com.example.budgetapptracker.sms.KIND_TRANSFER
import com.example.budgetapptracker.sms.MerchantCategories
import com.example.budgetapptracker.sms.PAYEE_MERCHANT
import com.example.budgetapptracker.sms.PAYEE_PERSON
import com.example.budgetapptracker.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantCategoriesTest {

    private fun env(id: String, name: String) = EnvelopeSummary(id, name, 0, 0)

    private val envelopes = listOf(
        env("food", "Food"),
        env("fuel", "Petrol"),
        env("shop", "Shopping"),
        env("bills", "Bills & Recharge"),
        env("travel", "Travel"),
    )

    @Test fun brandsFileConfidently() {
        val g = MerchantCategories.guess("SWIGGY", PAYEE_MERCHANT, envelopes)!!
        assertEquals("food", g.envelopeId)
        assertTrue(g.confident)
        assertEquals("fuel", MerchantCategories.guess("BHARAT PETROLEUM CORP", null, envelopes)!!.envelopeId)
        assertEquals("shop", MerchantCategories.guess("AMAZON PAY INDIA", null, envelopes)!!.envelopeId)
        assertEquals("travel", MerchantCategories.guess("UBER INDIA SYSTEMS", null, envelopes)!!.envelopeId)
        assertEquals("bills", MerchantCategories.guess("RELIANCE JIO INFOCOMM", null, envelopes)!!.envelopeId)
    }

    @Test fun legalNamesAndGluedNames() {
        assertEquals("food", MerchantCategories.guess("BUNDL TECHNOLOGIES PVT", null, envelopes)!!.envelopeId)
        assertEquals("food", MerchantCategories.guess("SWIGGYINSTAMART", null, envelopes)!!.envelopeId)
    }

    @Test fun everydayWordsAreOnlyHints() {
        val g = MerchantCategories.guess("SRI SAI CAFE", null, envelopes)!!
        assertEquals("food", g.envelopeId)
        assertFalse(g.confident)
    }

    @Test fun shortBrandsNeedWholeWords() {
        // "COLA" must not look like the cab company OLA, nor "JIOTSNA" like JIO.
        assertNull(MerchantCategories.typeOf("COCA COLA DISTRIBUTOR"))
        assertNull(MerchantCategories.typeOf("JIOTSNA SHARMA"))
        assertEquals("travel", MerchantCategories.guess("OLA CABS", null, envelopes)!!.envelopeId)
    }

    @Test fun groceriesFallBackToFoodButAsk() {
        val g = MerchantCategories.guess("BLINKIT", null, envelopes)!!
        assertEquals("food", g.envelopeId)
        assertFalse(g.confident)
    }

    @Test fun noMatchingEnvelopeMeansNoGuess() {
        assertNull(MerchantCategories.guess("APOLLO PHARMACY", null, envelopes))
    }

    @Test fun investmentsAreNotSpending() {
        val g = MerchantCategories.guess("INDIAN CLEARING CORP LTD", null, envelopes)!!
        assertEquals(KIND_INVESTMENT, g.kind)
        assertTrue(g.confident)
        assertEquals(KIND_INVESTMENT, MerchantCategories.guess("ICCL", null, envelopes)!!.kind)
        assertEquals(KIND_INVESTMENT, MerchantCategories.guess("NEXTBILLION TECHNOLOGY", null, envelopes)!!.kind)
    }

    @Test fun gatewaysAreUnknown() {
        assertNull(MerchantCategories.guess("RAZORPAY SOFTWARE", null, envelopes))
    }

    @Test fun personNamedLikeAShopIsNotFiledAlone() {
        val g = MerchantCategories.guess("STARBUCKS", PAYEE_PERSON, envelopes)!!
        assertFalse(g.confident)
    }

    @Test fun ownNameMeansTransfer() {
        val own = listOf("Saraswathula Koushik")
        assertTrue(MerchantCategories.isOwnName("KOUSHIK S", own))
        assertTrue(MerchantCategories.isOwnName("SARASWATHULA KOUSHIK", own))
        assertTrue(MerchantCategories.isOwnName("S KOUSHIK", own))
        assertFalse(MerchantCategories.isOwnName("KOUSHIK REDDY", own))
        assertFalse(MerchantCategories.isOwnName("KOUSHIK", own))
        assertFalse(MerchantCategories.isOwnName("RAHUL KUMAR", own))
        assertEquals(KIND_TRANSFER, MerchantCategories.guess("KOUSHIK S", PAYEE_PERSON, envelopes, own)!!.kind)
    }

    @Test fun payeeFromUpiId() {
        assertEquals(PAYEE_PERSON, MerchantCategories.payeeOf("9876543210@YBL"))
        assertEquals(PAYEE_MERCHANT, MerchantCategories.payeeOf("PAYTMQR281005050101@PAYTM"))
        assertEquals(PAYEE_MERCHANT, MerchantCategories.payeeOf("Q836124870@YBL"))
        assertEquals(PAYEE_MERCHANT, MerchantCategories.payeeOf("SWIGGY"))
        assertNull(MerchantCategories.payeeOf("RAMESH KUMAR"))
    }

    @Test fun smsParserKeepsP2mAndP2a() {
        val shop = SmsParser.parse(
            "INR 450.00 debited\nA/c no. XX1234\n17-11-26, 20:42:10\nUPI/P2M/432187650123/BHARAT PETROLEUM\nNot you? Call 18001035577 to report - Axis Bank"
        )!!
        assertEquals(45_000L, shop.amountPaise)
        assertEquals("BHARAT PETROLEUM", shop.merchant)
        assertEquals(PAYEE_MERCHANT, shop.payee)

        val person = SmsParser.parse(
            "INR 500.00 debited\nA/c no. XX1234\n17-11-26, 21:05:44\nUPI/P2A/432187650124/RAHUL KUMAR\nNot you? Call 18001035577 to report - Axis Bank"
        )!!
        assertEquals("RAHUL KUMAR", person.merchant)
        assertEquals(PAYEE_PERSON, person.payee)
    }
}
