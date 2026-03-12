package com.calendersharing.test.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calendersharing.test.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val myEventColor = settingsRepository.myEventColor.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsRepository.DEFAULT_MY_COLOR
    )

    val sharedEventColor = settingsRepository.sharedEventColor.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsRepository.DEFAULT_SHARED_COLOR
    )

    fun setMyEventColor(color: Long) {
        viewModelScope.launch { settingsRepository.setMyEventColor(color) }
    }

    fun setSharedEventColor(color: Long) {
        viewModelScope.launch { settingsRepository.setSharedEventColor(color) }
    }
}
