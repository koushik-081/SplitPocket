package com.example.budgetapptracker

import com.example.budgetapptracker.payslip.PayslipParser
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayslipParserTest {

    // The layout of a real Indian payslip with every personal detail replaced by made-up values.
    private val tableStyle = """
        Acme Cloud Services Limited
        Plot No. 1, Industrial Area, Pune - 411001, Maharashtra, India
        Salary Slip for the Month of October - 2026
        Employee Name : Test Person Employee ID : 1234 Date of Joining : 01-Jun-2026
        PAN : ABCDE1234F ESIC number : N.A
        PF number : N.A UAN Number : 100000000001 Bank Name : SOME BANK
        Account Number : 000000000000000 Swift/IFSC code : TEST0000001
        Earnings Deductions Reimbursement
        Description Amount YTD Description Amount YTD Description Amount
        Basic 29,167 1,45,835 Employer Provident Fund 3,000 10,760
        HRA 11,667 58,335 Professional Tax 200 1,000
        Advance Bonus 1,700 8,500 Canteen Deduction 890 5,735
        Special Allowance 2,396 16,573 CSR Voluntary Contribution 50 250
        Performance Payment 26,250 35,000 LWF 0 25
        Gross Pay (A) 71,180 2,64,243 Gross Deductions (B) 4,140 17,770 Reimbursement (C) 0
        Net Pay (A-B) 67,040 Total Pay (A-B)+C 67,040
        Total Pay In Words: Sixty Seven Thousand Forty Rupees Only.
        Income Tax Computation Sheet FY 2026-27 Tax Regime: New
        Gross Salary 4,53,893 Not Declared -
        Income Tax on Net Taxable Income (Before Rebate) 695
    """.trimIndent()

    @Test fun readsATableStylePayslip() {
        val p = PayslipParser.parse(tableStyle)
        assertEquals("Acme Cloud Services Limited", p.employer)
        assertEquals(YearMonth.of(2026, 10), p.month)
        assertEquals(7_118_000L, p.grossPaise)       // not the year-to-date 2,64,243
        assertEquals(414_000L, p.deductionsPaise)
        assertEquals(6_704_000L, p.netPaise)
        assertEquals(300_000L, p.pfPaise)            // not the UAN after "PF number : N.A"
        assertEquals(20_000L, p.taxPaise)            // professional tax only; the tax sheet is ignored
        assertEquals(listOf("Professional tax" to 20_000L), p.taxParts)
        assertTrue(p.addsUp)
    }

    @Test fun readsALabelColonStylePayslip() {
        val p = PayslipParser.parse(
            """
            Bright Minds Technologies Pvt. Ltd.
            Payslip for Sep 2026
            Date of Birth: 12-Mar-1999
            Total Earnings: Rs. 85,000.00
            Provident Fund: 1,800.00
            Income Tax: 6,250.00
            Profession Tax: 200.00
            Total Deductions: 8,250.00
            Net Salary: ₹ 76,750.00
            """.trimIndent()
        )
        assertEquals(YearMonth.of(2026, 9), p.month)
        assertEquals(8_500_000L, p.grossPaise)
        assertEquals(825_000L, p.deductionsPaise)
        assertEquals(7_675_000L, p.netPaise)
        assertEquals(180_000L, p.pfPaise)
        assertEquals(645_000L, p.taxPaise)
        assertTrue(p.addsUp)
        assertTrue(p.employer!!.startsWith("Bright Minds Technologies"))
    }

    @Test fun fillsTheMissingNumber() {
        val p = PayslipParser.parse("Payslip for 10/2026\nGross Earnings 50,000\nNet Pay 46,000")
        assertEquals(400_000L, p.deductionsPaise)
        assertEquals(YearMonth.of(2026, 10), p.month)
    }

    @Test fun joiningDateIsNotThePayMonth() {
        val p = PayslipParser.parse("Some Company Ltd\nDate of Joining : 01-Jun-2026\nNet Pay 10,000")
        assertNull(p.month)
    }

    @Test fun nothingFoundInUnrelatedText() {
        val p = PayslipParser.parse("Electricity bill for consumer 12345")
        assertNull(p.grossPaise)
        assertNull(p.netPaise)
    }

    // Mistakes text recognition makes on a photo: dots for commas, a label at the end of one
    // line with its number on the next, and a misspelled tax-sheet heading.
    @Test fun survivesPhotoReadingMistakes() {
        val p = PayslipParser.parse(
            """
            Acme Cloud Services Limited
            Salary Slip for the Month of October - 2026
            1,45,835 Employer Provident Fund
            29.167 Basic
            Employer Provident Fund 3.000 10,760
            Professional Tax 200 1,000
            Gross Pay (A) 71,180 2,64.243 Gross Deductions (B) 4,140 17,770
            Net Pay (A-B) 67,040
            Income Tax Compuțation Sheet FY 2026-27
            Income Tax on Net Taxable Income 695
            """.trimIndent()
        )
        assertEquals(300_000L, p.pfPaise)
        assertEquals(7_118_000L, p.grossPaise)
        assertEquals(20_000L, p.taxPaise)
        assertTrue(p.addsUp)
    }

    // A digit dropped by text recognition ("71,18") is not a real amount, so gross is worked
    // out from net pay and deductions instead of being read as ₹71.
    @Test fun brokenAmountIsRejectedAndGrossIsWorkedOut() {
        val p = PayslipParser.parse("Gross Pay (A) 71,18 2,64,243 Gross Deductions (B) 4,140\nNet Pay (A-B) 67,040")
        assertEquals(7_118_000L, p.grossPaise)
        assertTrue(p.addsUp)
    }
}
