package com.example.budgetapptracker.sms

import com.example.budgetapptracker.data.EnvelopeSummary

// Values saved in TransactionEntity.kind / .payee.
const val KIND_TRANSFER = "TRANSFER"
const val KIND_INVESTMENT = "INVESTMENT"
const val KIND_LENT = "LENT"
const val PAYEE_PERSON = "PERSON"
const val PAYEE_MERCHANT = "MERCHANT"

// What a payment was most likely for. `envelopeWords` find the user's own envelope for it:
// an envelope whose name contains one of them. `fallback` is tried next (a weaker match).
enum class SpendType(val envelopeWords: List<String>, val fallback: SpendType? = null) {
    FOOD(listOf("FOOD", "EAT", "DINING", "RESTAURANT", "MEAL", "SNACK", "SWIGGY", "ZOMATO", "TAKEAWAY")),
    GROCERIES(
        listOf("GROCER", "KIRANA", "HOUSEHOLD", "HOME NEEDS", "ESSENTIAL", "VEGETABLE", "RATION", "PROVISION"),
        fallback = FOOD,
    ),
    TRAVEL(listOf("TRAVEL", "TRANSPORT", "COMMUTE", "CAB", "AUTO", "TRIP", "METRO", "OLA", "UBER")),
    FUEL(listOf("FUEL", "PETROL", "DIESEL", "VEHICLE", "CAR", "BIKE", "SCOOTY"), fallback = TRAVEL),
    SHOPPING(listOf("SHOP", "CLOTH", "AMAZON", "FLIPKART", "PURCHASE", "PERSONAL", "FASHION")),
    BILLS(listOf("BILL", "UTILIT", "RECHARGE", "PHONE", "MOBILE", "INTERNET", "WIFI", "ELECTRIC", "POWER")),
    SUBSCRIPTIONS(listOf("SUBSCRIPTION", "OTT", "STREAMING", "NETFLIX"), fallback = BILLS),
    HEALTH(listOf("HEALTH", "MEDIC", "PHARMA", "DOCTOR", "HOSPITAL", "FITNESS", "GYM")),
    RENT(listOf("RENT", "HOUSE", "HOUSING", "HOSTEL", "PG")),
    ENTERTAINMENT(listOf("ENTERTAIN", "MOVIE", "FUN", "LEISURE", "OUTING", "PARTY"), fallback = SUBSCRIPTIONS),
    EDUCATION(listOf("EDUCATION", "STUDY", "COURSE", "FEES", "LEARN", "BOOK", "COLLEGE", "SCHOOL")),
}

// The best guess for one payment.
// - kind set (INVESTMENT / TRANSFER): not spending, no envelope needed.
// - envelopeId set: put it in that envelope.
// confident = safe to file without asking (a known brand matched to an envelope, or your own name).
data class Guess(
    val envelopeId: String?,
    val kind: String?,
    val confident: Boolean,
    val label: String,
)

// Recognises common Indian merchants from the name the bank or payment app shows. Works fully
// offline: everything is in these lists. The user's own "Remember" rules always win over this.
object MerchantCategories {

    // Brand and legal names: a match is reliable.
    private val brands: Map<SpendType, List<String>> = mapOf(
        SpendType.FUEL to listOf(
            "BHARAT PETROLEUM", "BPCL", "HINDUSTAN PETROLEUM", "HPCL", "INDIAN OIL", "IOCL",
            "SHELL", "NAYARA", "JIO BP", "RELIANCE BP", "ESSAR OIL",
        ),
        SpendType.FOOD to listOf(
            "SWIGGY", "BUNDL TECHNOLOGIES", "ZOMATO", "DOMINOS", "JUBILANT FOODWORKS", "MCDONALD",
            "HARDCASTLE", "WESTLIFE", "KFC", "DEVYANI", "PIZZA HUT", "BURGER KING", "STARBUCKS",
            "CHAAYOS", "CAFE COFFEE DAY", "HALDIRAM", "SUBWAY", "BEHROUZ", "FAASOS", "EATSURE",
        ),
        SpendType.GROCERIES to listOf(
            "BLINKIT", "BLINK COMMERCE", "ZEPTO", "KIRANAKART", "BIGBASKET", "INNOVATIVE RETAIL",
            "INSTAMART", "DMART", "AVENUE SUPERMARTS", "JIOMART", "RELIANCE SMART", "RELIANCE FRESH",
            "MORE RETAIL", "SPENCER", "RATNADEEP", "COUNTRY DELIGHT", "MILKBASKET",
        ),
        SpendType.SHOPPING to listOf(
            "AMAZON", "FLIPKART", "MYNTRA", "MEESHO", "FASHNEAR", "AJIO", "NYKAA", "FSN E COMMERCE",
            "TATA CLIQ", "CROMA", "RELIANCE DIGITAL", "DECATHLON", "IKEA", "LENSKART", "ZARA",
            "PANTALOONS", "WESTSIDE", "MAX FASHION", "TRENDS", "LIFESTYLE INTERNATIONAL",
        ),
        SpendType.TRAVEL to listOf(
            "UBER", "OLA", "ANI TECHNOLOGIES", "RAPIDO", "ROPPEN", "IRCTC", "REDBUS", "METRO RAIL",
            "MAKEMYTRIP", "GOIBIBO", "IXIGO", "CLEARTRIP", "INDIGO", "INTERGLOBE", "AIR INDIA",
            "AKASA", "SPICEJET", "FASTAG", "NAMMA YATRI", "YULU",
        ),
        SpendType.SUBSCRIPTIONS to listOf(
            "NETFLIX", "SPOTIFY", "HOTSTAR", "JIOSTAR", "NOVI DIGITAL", "YOUTUBE", "GOOGLE PLAY",
            "APPLE MEDIA", "APPLE SERVICES", "SONYLIV", "ZEE5", "AUDIBLE", "OPENAI", "PRIME VIDEO",
        ),
        SpendType.BILLS to listOf(
            "RELIANCE JIO", "JIO", "AIRTEL", "BHARTI", "VODAFONE", "BSNL", "ACT FIBERNET",
            "HATHWAY", "TATA PLAY", "DISH TV", "BESCOM", "TSSPDCL", "APEPDCL", "APSPDCL", "MSEDCL",
            "TANGEDCO", "TNEB", "BSES", "TATA POWER", "ADANI ELECTRICITY", "KSEB", "CESC",
            "WBSEDCL", "UPPCL", "INDANE", "HP GAS", "BHARATGAS", "MAHANAGAR GAS", "IGL",
            "LIC", "POLICYBAZAAR",
        ),
        SpendType.HEALTH to listOf(
            "APOLLO", "MEDPLUS", "PHARMEASY", "NETMEDS", "TATA 1MG", "1MG", "PRACTO", "CULT FIT",
            "CUREFIT",
        ),
        SpendType.RENT to listOf("NOBROKER", "NESTAWAY", "STANZA LIVING", "ZOLO"),
        SpendType.ENTERTAINMENT to listOf("BOOKMYSHOW", "BIGTREE", "PVR", "INOX", "CINEPOLIS", "DISTRICT"),
        SpendType.EDUCATION to listOf("UDEMY", "COURSERA", "BYJU", "UNACADEMY", "PHYSICSWALLAH"),
    )

    // Everyday words: a useful hint, but not reliable enough to file without asking.
    private val hints: Map<SpendType, List<String>> = mapOf(
        SpendType.FUEL to listOf("PETROL", "FUEL", "FILLING STATION", "SERVICE STATION", "DIESEL"),
        SpendType.FOOD to listOf(
            "RESTAURANT", "CAFE", "HOTEL", "BAKERY", "BAKERS", "SWEETS", "BIRYANI", "DHABA",
            "KITCHEN", "FOODS", "FOOD", "TIFFIN", "MESS", "JUICE", "TEA", "CHAI",
        ),
        SpendType.GROCERIES to listOf(
            "KIRANA", "SUPERMARKET", "SUPER MARKET", "GENERAL STORE", "PROVISION", "VEGETABLE",
            "FRUITS", "DAIRY", "MILK", "MART",
        ),
        SpendType.SHOPPING to listOf("FASHION", "GARMENTS", "TEXTILE", "FOOTWEAR", "MALL", "ELECTRONICS"),
        SpendType.TRAVEL to listOf("TRAVELS", "TOLL", "PARKING", "METRO", "RAILWAY", "TAXI", "CABS"),
        SpendType.BILLS to listOf("ELECTRICITY", "POWER", "BROADBAND", "RECHARGE", "WATER", "GAS", "INSURANCE"),
        SpendType.HEALTH to listOf(
            "HOSPITAL", "CLINIC", "PHARMACY", "PHARMA", "MEDICAL", "MEDICALS", "CHEMIST",
            "DIAGNOSTIC", "LABS", "DENTAL", "GYM", "FITNESS",
        ),
        SpendType.RENT to listOf("RENT"),
        SpendType.ENTERTAINMENT to listOf("CINEMA", "MULTIPLEX", "GAMING"),
        SpendType.EDUCATION to listOf("SCHOOL", "COLLEGE", "UNIVERSITY", "ACADEMY", "COACHING", "TUITION", "INSTITUTE"),
    )

    // Mutual funds, stocks and pension: money saved, not spent.
    private val investments = listOf(
        "ICCL", "INDIAN CLEARING", "NSE CLEARING", "NSCCL", "BSE LIMITED", "BSE STAR", "CAMS",
        "COMPUTER AGE MANAGEMENT", "KFIN", "KFINTECH", "GROWW", "NEXTBILLION", "ZERODHA",
        "KUVERA", "PAYTM MONEY", "UPSTOX", "RKSV", "ANGEL ONE", "ANGEL BROKING", "ETMONEY",
        "INDMONEY", "SMALLCASE", "MUTUAL FUND", "NPS TRUST", "PROTEAN", "DHAN", "SIP",
    )

    // Payment gateways forward money for thousands of shops, so the name says nothing.
    private val gateways = listOf(
        "RAZORPAY", "PAYU", "CASHFREE", "BILLDESK", "CCAVENUE", "PINE LABS", "PINELABS", "JUSPAY",
        "EASEBUZZ", "PHONEPE PRIVATE", "PAYTM PAYMENTS",
    )

    // Shop QR codes and business UPI IDs.
    private val merchantVpaMarks = listOf(
        "PAYTMQR", "BHARATPE", "OKBIZ", "GPAY-", "MSWIPE", "PINELABS", "RAZORPAY", "@YESBANKLTD",
        "MERCHANT", ".RZP", "@HDFCBANKMER", "Q0", "Q1", "Q2", "Q3", "Q4", "Q5", "Q6", "Q7", "Q8", "Q9",
    )

    // Uppercase, punctuation to spaces, single spaces, padded so " WORD " matches whole words.
    internal fun normalise(text: String): String =
        " " + text.uppercase().replace(Regex("[^A-Z0-9@]+"), " ").trim().replace(Regex(" +"), " ") + " "

    private fun hasWord(normalised: String, word: String): Boolean =
        normalised.contains(" " + normalise(word).trim() + " ")

    // Brand names are often glued to other words ("SWIGGYINSTAMART"), so they match anywhere.
    private fun hasBrand(normalised: String, brand: String): Boolean {
        val b = normalise(brand).trim()
        // Short brands like OLA, JIO, LIC or SIP must be whole words, or "COLA" would be a cab.
        return if (b.length <= 4) hasWord(normalised, b) else normalised.contains(b)
    }

    fun isGateway(merchant: String): Boolean {
        val n = normalise(merchant)
        return gateways.any { hasBrand(n, it) }
    }

    fun isInvestment(merchant: String): Boolean {
        val n = normalise(merchant)
        return investments.any { hasBrand(n, it) }
    }

    // PERSON, MERCHANT or null (cannot tell) from the payee's name or UPI ID alone.
    fun payeeOf(merchant: String): String? {
        val raw = merchant.uppercase().trim()
        if (raw.isEmpty() || raw == "UNKNOWN") return null
        // Phone-number UPI IDs (9876543210@ybl) belong to people.
        if (Regex("""^[6-9]\d{9}(@|$)""").containsMatchIn(raw)) return PAYEE_PERSON
        if (merchantVpaMarks.any { raw.contains(it) && (it.length > 2 || raw.startsWith(it)) }) return PAYEE_MERCHANT
        if (isGateway(raw) || isInvestment(raw) || typeOf(raw) != null) return PAYEE_MERCHANT
        return null
    }

    // The spending type and whether it came from a brand (true) or an everyday word (false).
    fun typeOf(merchant: String): Pair<SpendType, Boolean>? {
        val n = normalise(merchant)
        brands.forEach { (type, names) -> if (names.any { hasBrand(n, it) }) return type to true }
        hints.forEach { (type, words) -> if (words.any { hasWord(n, it) }) return type to false }
        return null
    }

    // The user's envelope for a spending type: its own words first, then the fallback type's.
    // Returns the envelope and whether it was a direct (not fallback) match.
    fun envelopeFor(type: SpendType, envelopes: List<EnvelopeSummary>): Pair<EnvelopeSummary, Boolean>? {
        fun find(t: SpendType) = envelopes.firstOrNull { e ->
            val name = normalise(e.name)
            t.envelopeWords.any { name.contains(it) }
        }
        find(type)?.let { return it to true }
        var next = type.fallback
        while (next != null) {
            find(next)?.let { return it to false }
            next = next.fallback
        }
        return null
    }

    // True when a payee name looks like one of the user's own names, for example
    // "KOUSHIK S" or "SARASWATHULA KOUSHIK" for the saved name "Saraswathula Koushik".
    // Every word of the payee must be one of your words or its first letter, and at least
    // one must be a whole word, so "KOUSHIK REDDY" does not count.
    fun isOwnName(merchant: String, ownNames: List<String>): Boolean {
        val payeeWords = normalise(merchant).trim().split(" ").filter { it.isNotEmpty() && '@' !in it }
        if (payeeWords.isEmpty()) return false
        return ownNames.any { own ->
            val mine = normalise(own).trim().split(" ").filter { it.isNotEmpty() }
            if (mine.isEmpty()) return@any false
            val allKnown = payeeWords.all { w -> mine.any { it == w || (w.length == 1 && it.startsWith(w)) } }
            val oneWhole = payeeWords.any { w -> w.length > 1 && w in mine }
            allKnown && oneWhole && (payeeWords.size >= 2 || mine.size == 1)
        }
    }

    // The best guess for a payment, or null when there is nothing to go on.
    fun guess(
        merchant: String,
        payee: String?,
        envelopes: List<EnvelopeSummary>,
        ownNames: List<String> = emptyList(),
    ): Guess? {
        if (ownNames.isNotEmpty() && isOwnName(merchant, ownNames)) {
            return Guess(envelopeId = null, kind = KIND_TRANSFER, confident = true, label = "Transfer to your own account")
        }
        if (isInvestment(merchant)) {
            return Guess(envelopeId = null, kind = KIND_INVESTMENT, confident = true, label = "Investment")
        }
        if (isGateway(merchant)) return null
        val (type, isBrand) = typeOf(merchant) ?: return null
        val (envelope, direct) = envelopeFor(type, envelopes) ?: return null
        return Guess(
            envelopeId = envelope.id,
            kind = null,
            // A person's UPI name that happens to contain "CAFE" is still a person: ask.
            confident = isBrand && direct && payee != PAYEE_PERSON,
            label = envelope.name,
        )
    }
}
