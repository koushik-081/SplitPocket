package com.example.budgetapptracker.ui.payslips

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.data.PayslipEntity
import com.example.budgetapptracker.payslip.PayslipFiles
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PayslipsViewModel(private val app: BudgetApp) : ViewModel() {

    val payslips: StateFlow<List<PayslipEntity>> =
        app.repository.observePayslips()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun add(
        month: YearMonth,
        employer: String,
        grossPaise: Long,
        deductionsPaise: Long,
        attachment: Uri?,
        addAsIncome: Boolean,
    ) {
        viewModelScope.launch {
            var payslip = PayslipEntity(
                month = month.toString(),
                employer = employer.trim(),
                grossPaise = grossPaise,
                deductionsPaise = deductionsPaise,
                netPaise = grossPaise - deductionsPaise,
            )
            if (attachment != null) {
                // Copying a file is slow work, so it runs off the main thread.
                val stored = withContext(Dispatchers.IO) { PayslipFiles.copyIn(app, attachment, payslip.id) }
                if (stored == null) _message.value = "The file could not be attached (PDF or photo, up to 10 MB)"
                else payslip = payslip.copy(fileName = stored.first, fileType = stored.second)
            }
            app.repository.addPayslip(payslip, addAsIncome)
        }
    }

    // Whether that month already has a salary income, so the payslip should not add another.
    fun hasSalary(month: YearMonth): Flow<Boolean> = app.repository.observeHasSalary(month)

    fun delete(payslip: PayslipEntity) {
        viewModelScope.launch {
            payslip.fileName?.let { name -> withContext(Dispatchers.IO) { PayslipFiles.delete(app, name) } }
            app.repository.deletePayslip(payslip.id)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PayslipsViewModel(this[APPLICATION_KEY] as BudgetApp) }
        }
    }
}
