package com.example.budgetapptracker.pay

import android.net.Uri

// Who is being paid. `source` is the full link from a scanned QR code, when there was one.
data class UpiRequest(
    val payeeId: String,
    val payeeName: String,
    val fixedAmountRupees: String?,
    val source: Uri?,
)

object UpiLink {
    private val upiId = Regex("""^[A-Za-z0-9._-]{2,}@[A-Za-z0-9.-]{2,}$""")

    fun isValidId(id: String): Boolean = upiId.matches(id.trim())

    // Reads the text inside a UPI QR code, e.g. upi://pay?pa=shop@bank&pn=Shop&am=120
    fun parse(text: String): UpiRequest? {
        val uri = runCatching { Uri.parse(text.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("upi", ignoreCase = true) || uri.isOpaque) return null
        val payeeId = uri.getQueryParameter("pa")?.takeIf { isValidId(it) } ?: return null
        val amount = uri.getQueryParameter("am")
            ?.takeIf { (it.toBigDecimalOrNull()?.signum() ?: 0) > 0 }
        return UpiRequest(
            payeeId = payeeId.trim(),
            payeeName = uri.getQueryParameter("pn")?.takeIf { it.isNotBlank() } ?: payeeId,
            fixedAmountRupees = amount,
            source = uri,
        )
    }

    fun fromUpiId(id: String, name: String): UpiRequest? {
        if (!isValidId(id)) return null
        return UpiRequest(id.trim(), name.trim().ifBlank { id.trim() }, null, null)
    }

    // Builds the link handed to the payment app.
    fun build(request: UpiRequest, amountRupees: String): Uri {
        val source = request.source
        // A QR that already carries its amount is passed on untouched, signature and all.
        if (source != null && request.fixedAmountRupees != null) return source

        val builder = Uri.Builder().scheme("upi").authority("pay")
        if (source != null) {
            // Keep everything the QR said except the parts we are setting ourselves.
            source.queryParameterNames
                .filter { it !in setOf("am", "cu", "sign") }
                .forEach { key -> builder.appendQueryParameter(key, source.getQueryParameter(key)) }
        } else {
            builder.appendQueryParameter("pa", request.payeeId)
            builder.appendQueryParameter("pn", request.payeeName)
        }
        return builder
            .appendQueryParameter("am", amountRupees)
            .appendQueryParameter("cu", "INR")
            .build()
    }
}