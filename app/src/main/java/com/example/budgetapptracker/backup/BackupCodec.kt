package com.example.budgetapptracker.backup

import com.example.budgetapptracker.data.BillEntity
import com.example.budgetapptracker.data.PayslipEntity
import java.util.Base64
import com.example.budgetapptracker.data.CategoryEntity
import com.example.budgetapptracker.data.DebtEntity
import com.example.budgetapptracker.data.GroupEntity
import com.example.budgetapptracker.data.IncomeEntity
import com.example.budgetapptracker.data.MerchantRuleEntity
import com.example.budgetapptracker.data.PersonEntity
import com.example.budgetapptracker.data.TransactionEntity
import org.json.JSONArray
import org.json.JSONObject
import com.example.budgetapptracker.data.GroupMemberEntity

// Everything the app stores, held in memory while a backup is written or read.
data class BackupData(
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val incomes: List<IncomeEntity>,
    val rules: List<MerchantRuleEntity>,
    val people: List<PersonEntity>,
    val groups: List<GroupEntity>,
    val debts: List<DebtEntity>,
    val groupMembers: List<GroupMemberEntity> = emptyList(),
    val bills: List<BillEntity> = emptyList(),
    val payslips: List<PayslipEntity> = emptyList(),
    // Attached payslip files by file name, stored in the JSON as Base64 text.
    val payslipFiles: Map<String, ByteArray> = emptyMap(),
)

// Turns the data into JSON text and back. JSON is plain text, so an old backup stays
// readable even after the app's tables change.
object BackupCodec {
    private const val FORMAT = 1

    fun toJson(data: BackupData): String = JSONObject()
        .put("format", FORMAT)
        .put("createdAt", System.currentTimeMillis())
        .put("categories", data.categories.toArray {
            JSONObject().put("id", it.id).put("name", it.name)
                .put("monthlyBudgetPaise", it.monthlyBudgetPaise).put("createdAt", it.createdAt)
        })
        .put("transactions", data.transactions.toArray {
            JSONObject().put("id", it.id).put("amountPaise", it.amountPaise)
                .putOpt("categoryId", it.categoryId).put("merchant", it.merchant)
                .put("note", it.note).put("occurredAt", it.occurredAt).put("source", it.source)
                .putOpt("smsKey", it.smsKey).put("excluded", it.excluded)
                .putOpt("paidPaise", it.paidPaise)
                .putOpt("kind", it.kind).putOpt("payee", it.payee)
        })
        .put("incomes", data.incomes.toArray {
            JSONObject().put("id", it.id).put("amountPaise", it.amountPaise)
                .put("source", it.source).put("receivedAt", it.receivedAt)
        })
        .put("rules", data.rules.toArray {
            JSONObject().put("merchant", it.merchant).put("categoryId", it.categoryId)
        })
        .put("people", data.people.toArray {
            JSONObject().put("id", it.id).put("name", it.name)
                .putOpt("phone", it.phone).put("createdAt", it.createdAt)
        })
        .put("groups", data.groups.toArray {
            JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt)
        })
        .put("debts", data.debts.toArray {
            JSONObject().put("id", it.id).put("personId", it.personId)
                .putOpt("groupId", it.groupId).put("amountPaise", it.amountPaise)
                .put("note", it.note).put("createdAt", it.createdAt)
                .putOpt("expenseId", it.expenseId)
        })
        .put("groupMembers", data.groupMembers.toArray {
            JSONObject().put("groupId", it.groupId).put("personId", it.personId)
        })
        .put("bills", data.bills.toArray {
            JSONObject().put("id", it.id).put("name", it.name)
                .put("amountPaise", it.amountPaise).put("dayOfMonth", it.dayOfMonth)
                .putOpt("paidMonth", it.paidMonth).put("createdAt", it.createdAt)
        })
        .put("payslips", data.payslips.toArray {
            JSONObject().put("id", it.id).put("month", it.month).put("employer", it.employer)
                .put("grossPaise", it.grossPaise).put("deductionsPaise", it.deductionsPaise)
                .put("netPaise", it.netPaise).putOpt("fileName", it.fileName)
                .putOpt("fileType", it.fileType).put("createdAt", it.createdAt)
                .putOpt("pfPaise", it.pfPaise).putOpt("taxPaise", it.taxPaise)
        })
        .put("payslipFiles", JSONObject().also { files ->
            data.payslipFiles.forEach { (name, bytes) ->
                files.put(name, Base64.getEncoder().encodeToString(bytes))
            }
        })
        .toString()

    fun fromJson(text: String): BackupData {
        val root = JSONObject(text)
        require(root.optInt("format", 0) in 1..FORMAT) { "Unknown backup format" }
        return BackupData(
            categories = root.list("categories") {
                CategoryEntity(
                    id = it.getString("id"),
                    name = it.getString("name"),
                    monthlyBudgetPaise = it.getLong("monthlyBudgetPaise"),
                    createdAt = it.getLong("createdAt"),
                )
            },
            transactions = root.list("transactions") {
                TransactionEntity(
                    id = it.getString("id"),
                    amountPaise = it.getLong("amountPaise"),
                    categoryId = it.textOrNull("categoryId"),
                    merchant = it.getString("merchant"),
                    note = it.optString("note"),
                    occurredAt = it.getLong("occurredAt"),
                    source = it.optString("source", "MANUAL"),
                    smsKey = it.textOrNull("smsKey"),
                    excluded = it.optBoolean("excluded", false),
                    paidPaise = if (it.isNull("paidPaise")) null else it.getLong("paidPaise"),
                    kind = it.textOrNull("kind"),
                    payee = it.textOrNull("payee"),
                )
            },
            incomes = root.list("incomes") {
                IncomeEntity(
                    id = it.getString("id"),
                    amountPaise = it.getLong("amountPaise"),
                    source = it.getString("source"),
                    receivedAt = it.getLong("receivedAt"),
                )
            },
            rules = root.list("rules") {
                MerchantRuleEntity(merchant = it.getString("merchant"), categoryId = it.getString("categoryId"))
            },
            people = root.list("people") {
                PersonEntity(
                    id = it.getString("id"),
                    name = it.getString("name"),
                    phone = it.textOrNull("phone"),
                    createdAt = it.getLong("createdAt"),
                )
            },
            groups = root.list("groups") {
                GroupEntity(id = it.getString("id"), name = it.getString("name"), createdAt = it.getLong("createdAt"))
            },
            debts = root.list("debts") {
                DebtEntity(
                    id = it.getString("id"),
                    personId = it.getString("personId"),
                    groupId = it.textOrNull("groupId"),
                    amountPaise = it.getLong("amountPaise"),
                    note = it.optString("note"),
                    createdAt = it.getLong("createdAt"),
                    expenseId = it.textOrNull("expenseId"),
                )
            },
            groupMembers = root.list("groupMembers") {
                GroupMemberEntity(groupId = it.getString("groupId"), personId = it.getString("personId"))
            },
            bills = root.list("bills") {
                BillEntity(
                    id = it.getString("id"),
                    name = it.getString("name"),
                    amountPaise = it.getLong("amountPaise"),
                    dayOfMonth = it.getInt("dayOfMonth"),
                    paidMonth = it.textOrNull("paidMonth"),
                    createdAt = it.getLong("createdAt"),
                )
            },
            payslips = root.list("payslips") {
                PayslipEntity(
                    id = it.getString("id"),
                    month = it.getString("month"),
                    employer = it.getString("employer"),
                    grossPaise = it.getLong("grossPaise"),
                    deductionsPaise = it.getLong("deductionsPaise"),
                    netPaise = it.getLong("netPaise"),
                    fileName = it.textOrNull("fileName"),
                    fileType = it.textOrNull("fileType"),
                    pfPaise = if (it.isNull("pfPaise")) null else it.getLong("pfPaise"),
                    taxPaise = if (it.isNull("taxPaise")) null else it.getLong("taxPaise"),
                    createdAt = it.getLong("createdAt"),
                )
            },
            payslipFiles = root.optJSONObject("payslipFiles")?.let { files ->
                files.keys().asSequence().associateWith { Base64.getDecoder().decode(files.getString(it)) }
            } ?: emptyMap(),
        )
    }

    private fun <T> List<T>.toArray(convert: (T) -> JSONObject): JSONArray =
        JSONArray().also { array -> forEach { array.put(convert(it)) } }

    // A table missing from an older backup simply comes back empty.
    private fun <T> JSONObject.list(key: String, convert: (JSONObject) -> T): List<T> {
        val array = optJSONArray(key) ?: return emptyList()
        return List(array.length()) { convert(array.getJSONObject(it)) }
    }

    private fun JSONObject.textOrNull(key: String): String? = if (isNull(key)) null else getString(key)
}