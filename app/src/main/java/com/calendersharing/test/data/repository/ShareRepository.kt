package com.calendersharing.test.data.repository

import com.calendersharing.test.data.model.CalendarEvent
import com.calendersharing.test.data.model.SharedCalendar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) {
    private val sharedCalendarsCollection = firestore.collection("shared_calendars")
    private val sharedEventsCollection = firestore.collection("shared_events")

    suspend fun createShareLink(): Result<String> {
        return try {
            val user = firebaseAuth.currentUser
                ?: return Result.failure(Exception("로그인이 필요합니다"))

            val inviteCode = UUID.randomUUID().toString().take(8)

            val existingQuery = sharedCalendarsCollection
                .whereEqualTo("ownerUid", user.uid)
                .get()
                .await()

            if (existingQuery.documents.isNotEmpty()) {
                val existing = existingQuery.documents.first()
                val code = existing.getString("inviteCode") ?: inviteCode
                return Result.success(code)
            }

            val sharedCalendar = SharedCalendar(
                id = user.uid,
                ownerUid = user.uid,
                ownerEmail = user.email ?: "",
                ownerName = user.displayName ?: "",
                inviteCode = inviteCode
            )

            sharedCalendarsCollection.document(user.uid).set(sharedCalendar).await()
            Result.success(inviteCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptInvite(inviteCode: String): Result<SharedCalendar> {
        return try {
            val user = firebaseAuth.currentUser
                ?: return Result.failure(Exception("로그인이 필요합니다"))

            val query = sharedCalendarsCollection
                .whereEqualTo("inviteCode", inviteCode)
                .get()
                .await()

            if (query.documents.isEmpty()) {
                return Result.failure(Exception("유효하지 않은 초대 코드입니다"))
            }

            val doc = query.documents.first()
            val sharedCalendar = doc.toObject(SharedCalendar::class.java)
                ?: return Result.failure(Exception("데이터 파싱 실패"))

            if (sharedCalendar.ownerUid == user.uid) {
                return Result.failure(Exception("자신의 캘린더는 구독할 수 없습니다"))
            }

            val updatedSubscribers = sharedCalendar.subscriberUids.toMutableList()
            if (!updatedSubscribers.contains(user.uid)) {
                updatedSubscribers.add(user.uid)
                sharedCalendarsCollection.document(doc.id)
                    .update("subscriberUids", updatedSubscribers)
                    .await()
            }

            Result.success(sharedCalendar.copy(subscriberUids = updatedSubscribers))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSubscribedCalendars(): Flow<List<SharedCalendar>> = callbackFlow {
        val user = firebaseAuth.currentUser
        if (user == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val registration: ListenerRegistration = sharedCalendarsCollection
            .whereArrayContains("subscriberUids", user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val calendars = snapshot?.documents?.mapNotNull {
                    it.toObject(SharedCalendar::class.java)
                } ?: emptyList()
                trySend(calendars)
            }

        awaitClose { registration.remove() }
    }

    suspend fun syncEventsToFirestore(events: List<CalendarEvent>) {
        val user = firebaseAuth.currentUser ?: return

        try {
            val batch = firestore.batch()

            events.forEach { event ->
                val docRef = sharedEventsCollection
                    .document("${user.uid}_${event.id}")
                batch.set(docRef, event.copy(ownerEmail = user.email ?: ""))
            }

            batch.commit().await()
        } catch (e: Exception) {
            // Firestore 권한 오류 등은 무시하고 로컬에서만 표시
        }
    }

    fun getSharedEvents(ownerUid: String): Flow<List<CalendarEvent>> = callbackFlow {
        val registration = sharedEventsCollection
            .whereEqualTo("ownerEmail", ownerUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val events = snapshot?.documents?.mapNotNull {
                    it.toObject(CalendarEvent::class.java)
                } ?: emptyList()
                trySend(events)
            }

        awaitClose { registration.remove() }
    }
}
