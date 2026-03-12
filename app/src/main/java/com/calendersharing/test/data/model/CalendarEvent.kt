package com.calendersharing.test.data.model

data class CalendarEvent(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val location: String = "",
    val isAllDay: Boolean = false,
    val calendarId: String = "",
    val ownerEmail: String = "",
    val color: String = "#4285F4",
    val timeZone: String = ""
)

data class SharedCalendar(
    val id: String = "",
    val ownerUid: String = "",
    val ownerEmail: String = "",
    val ownerName: String = "",
    val subscriberUids: List<String> = emptyList(),
    val inviteCode: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
