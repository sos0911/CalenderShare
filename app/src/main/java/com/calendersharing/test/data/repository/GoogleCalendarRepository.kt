package com.calendersharing.test.data.repository

import android.content.Context
import com.calendersharing.test.data.model.CalendarEvent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun getCalendarService(): Calendar? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(CalendarScopes.CALENDAR_READONLY)
        )
        credential.selectedAccount = account.account

        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("CalenderSharing")
            .build()
    }

    suspend fun getEvents(
        timeMinMillis: Long,
        timeMaxMillis: Long
    ): Result<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        try {
            val service = getCalendarService()
                ?: return@withContext Result.failure(Exception("Google 계정이 연결되지 않았습니다"))

            val events = service.events().list("primary")
                .setTimeMin(DateTime(timeMinMillis))
                .setTimeMax(DateTime(timeMaxMillis))
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .setMaxResults(100)
                .execute()

            val calendarEvents = events.items?.map { event ->
                val start = event.start?.dateTime?.value
                    ?: event.start?.date?.value
                    ?: 0L
                val end = event.end?.dateTime?.value
                    ?: event.end?.date?.value
                    ?: 0L
                val isAllDay = event.start?.dateTime == null

                CalendarEvent(
                    id = event.id ?: "",
                    title = event.summary ?: "(제목 없음)",
                    description = event.description ?: "",
                    startTime = start,
                    endTime = end,
                    location = event.location ?: "",
                    isAllDay = isAllDay,
                    calendarId = "primary",
                    ownerEmail = GoogleSignIn.getLastSignedInAccount(context)?.email ?: ""
                )
            } ?: emptyList()

            Result.success(calendarEvents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
