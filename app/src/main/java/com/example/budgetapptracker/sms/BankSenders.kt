package com.example.budgetapptracker.sms

// Names that appear in the sender IDs of Indian banks, card issuers and payment banks.
private val bankNames = listOf(
    "HDFC", "SBI", "ICICI", "AXIS", "KOTAK", "PNB", "BOB", "BARODA", "CANBNK", "CANARA",
    "UNION", "IDFC", "INDUS", "YESB", "FEDB", "FEDERAL", "IDBI", "RBL", "AUBANK", "BOI",
    "CENTBK", "IOB", "UCO", "KVB", "CUB", "DBS", "SCB", "CITI", "HSBC", "AMEX",
    "ONECRD", "SLICE", "PAYTM", "JUPITR", "FIMONY", "AIRBNK", "IPPB", "BANDHN", "EQUITA",
    "UJJIVN", "DCB", "SARASW", "TMB", "JKB", "KBL", "SIB", "CSB", "DHAN", "INDBNK",
    "MAHABK", "PSB",
)

// A sender ID looks like "VM-HDFCBK" or "JD-SBIUPI-S": a route code, the sender's name,
// and sometimes a one-letter suffix. Only the name in the middle identifies the bank.
fun isBankSender(address: String): Boolean {
    val parts = address.uppercase().split('-')
    val name = if (parts.size >= 2) parts[1] else parts[0]

    // Personal numbers have no letters, so friends and family are never read.
    if (name.none { it.isLetter() }) return false

    return bankNames.any { it in name } ||
            name.endsWith("BK") || name.endsWith("BNK") || name.endsWith("BANK")
}