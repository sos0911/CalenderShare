package com.calendersharing.test.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calendersharing.test.data.model.CalendarEvent
import com.calendersharing.test.data.model.SharedCalendar
import com.calendersharing.test.data.repository.GoogleCalendarRepository
import com.calendersharing.test.data.repository.ShareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val myEvents: List<CalendarEvent> = emptyList(),
    val sharedCalendars: List<SharedCalendar> = emptyList(),
    val sharedEvents: Map<String, List<CalendarEvent>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val shareLink: String? = null,
    val inviteResult: String? = null
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val shareRepository: ShareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState

    init {
        loadMyEvents()
        observeSharedCalendars()
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        loadMyEvents()
    }

    fun loadMyEvents() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val date = _uiState.value.selectedDate
            val zone = ZoneId.systemDefault()
            val startOfMonth = date.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val endOfMonth = date.withDayOfMonth(date.lengthOfMonth())
                .atTime(23, 59, 59)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()

            googleCalendarRepository.getEvents(startOfMonth, endOfMonth)
                .onSuccess { events ->
                    _uiState.value = _uiState.value.copy(
                        myEvents = events,
                        isLoading = false,
                        error = null
                    )
                    shareRepository.syncEventsToFirestore(events)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    private fun observeSharedCalendars() {
        viewModelScope.launch {
            shareRepository.getSubscribedCalendars().collectLatest { calendars ->
                _uiState.value = _uiState.value.copy(sharedCalendars = calendars)

                calendars.forEach { calendar ->
                    launch {
                        shareRepository.getSharedEvents(calendar.ownerEmail)
                            .collectLatest { events ->
                                val currentSharedEvents = _uiState.value.sharedEvents.toMutableMap()
                                currentSharedEvents[calendar.ownerEmail] = events
                                _uiState.value = _uiState.value.copy(sharedEvents = currentSharedEvents)
                            }
                    }
                }
            }
        }
    }

    fun createShareLink() {
        viewModelScope.launch {
            shareRepository.createShareLink()
                .onSuccess { link ->
                    _uiState.value = _uiState.value.copy(shareLink = link)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
        }
    }

    fun acceptInvite(inviteCode: String) {
        viewModelScope.launch {
            shareRepository.acceptInvite(inviteCode)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        inviteResult = "${it.ownerName}님의 캘린더를 구독했습니다"
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(error = it.message)
                }
        }
    }

    fun clearShareLink() {
        _uiState.value = _uiState.value.copy(shareLink = null)
    }

    fun clearInviteResult() {
        _uiState.value = _uiState.value.copy(inviteResult = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
