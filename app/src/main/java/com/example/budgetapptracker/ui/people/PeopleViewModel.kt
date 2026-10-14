package com.example.budgetapptracker.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.data.BudgetRepository
import com.example.budgetapptracker.data.DebtRow
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.data.GroupEntryRow
import com.example.budgetapptracker.data.GroupSummary
import com.example.budgetapptracker.data.PersonBalance
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModel(private val repository: BudgetRepository) : ViewModel() {

    private val openPersonId = MutableStateFlow<String?>(null)
    private val openGroupId = MutableStateFlow<String?>(null)

    // Everyone you share money with, each with one net balance across all entries.
    val people: StateFlow<List<PersonBalance>> =
        repository.observePeople()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<GroupSummary>> =
        repository.observeGroups()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Needed when splitting a bill, to choose where your own share comes from.
    val envelopes: StateFlow<List<EnvelopeSummary>> =
        repository.observeEnvelopes(YearMonth.now())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openPerson: StateFlow<PersonBalance?> =
        combine(people, openPersonId) { list, id -> list.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val openPersonEntries: StateFlow<List<DebtRow>> =
        openPersonId
            .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeDebts(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openGroup: StateFlow<GroupSummary?> =
        combine(groups, openGroupId) { list, id -> list.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val openGroupMembers: StateFlow<List<PersonBalance>> =
        openGroupId
            .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeGroupMembers(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openGroupEntries: StateFlow<List<GroupEntryRow>> =
        openGroupId
            .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeGroupEntries(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openPerson(id: String) {
        openPersonId.value = id
    }

    fun closePerson() {
        openPersonId.value = null
    }

    fun openGroup(id: String) {
        openGroupId.value = id
    }

    fun closeGroup() {
        openGroupId.value = null
    }

    fun addPerson(name: String, phone: String) {
        viewModelScope.launch { repository.addPerson(name, phone) }
    }

    // Positive: they owe you. Negative: you owe them.
    fun addEntry(personId: String, signedPaise: Long, note: String) {
        viewModelScope.launch { repository.addDebt(personId, signedPaise, note) }
    }

    // envelopeId: where the money came from when you paid them; null when it is already counted.
    fun settleUp(person: PersonBalance, envelopeId: String?) {
        viewModelScope.launch {
            repository.settleUp(person.id, person.name, person.balancePaise, envelopeId)
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch { repository.deleteDebt(id) }
    }

    fun deletePerson(id: String) {
        openPersonId.value = null
        viewModelScope.launch { repository.deletePerson(id) }
    }

    fun addGroup(name: String, memberIds: List<String>) {
        viewModelScope.launch { repository.addGroup(name, memberIds) }
    }

    fun deleteGroup(id: String) {
        openGroupId.value = null
        viewModelScope.launch { repository.deleteGroup(id) }
    }

    // payerId is null when you paid; envelopeId is null to keep your share out of the budget.
    fun addSplit(
        groupId: String,
        description: String,
        totalPaise: Long,
        sharedWithIds: List<String>,
        payerId: String?,
        envelopeId: String?,
    ) {
        viewModelScope.launch {
            repository.addSplit(groupId, description, totalPaise, sharedWithIds, payerId, envelopeId)
        }
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