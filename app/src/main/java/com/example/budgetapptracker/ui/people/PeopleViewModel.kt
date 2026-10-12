package com.example.budgetapptracker.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.data.BudgetRepository
import com.example.budgetapptracker.data.DebtRow
import com.example.budgetapptracker.data.PersonBalance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleViewModel(private val repository: BudgetRepository) : ViewModel() {

    private val openPersonId = MutableStateFlow<String?>(null)

    // Everyone you share money with, each with one net balance across all entries.
    val people: StateFlow<List<PersonBalance>> =
        repository.observePeople()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openPerson: StateFlow<PersonBalance?> =
        combine(people, openPersonId) { list, id -> list.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val openPersonEntries: StateFlow<List<DebtRow>> =
        openPersonId
            .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeDebts(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openPerson(id: String) {
        openPersonId.value = id
    }

    fun closePerson() {
        openPersonId.value = null
    }

    fun addPerson(name: String, phone: String) {
        viewModelScope.launch { repository.addPerson(name, phone) }
    }

    // Positive: they owe you. Negative: you owe them.
    fun addEntry(personId: String, signedPaise: Long, note: String) {
        viewModelScope.launch { repository.addDebt(personId, signedPaise, note) }
    }

    fun settleUp(person: PersonBalance) {
        viewModelScope.launch { repository.addDebt(person.id, -person.balancePaise, "Settled up") }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch { repository.deleteDebt(id) }
    }

    fun deletePerson(id: String) {
        openPersonId.value = null
        viewModelScope.launch { repository.deletePerson(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BudgetApp
                PeopleViewModel(app.repository)
            }
        }
    }
}