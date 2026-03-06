package com.calendersharing.test.ui.screen.calendar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.calendersharing.test.data.model.CalendarEvent
import com.calendersharing.test.ui.viewmodel.AuthViewModel
import com.calendersharing.test.ui.viewmodel.CalendarViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    deepLinkInviteCode: String? = null,
    onSignOut: () -> Unit,
    calendarViewModel: CalendarViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by calendarViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showShareDialog by remember { mutableStateOf(false) }
    var showSharedCalendarsDialog by remember { mutableStateOf(false) }
    var showInviteCodeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deepLinkInviteCode) {
        deepLinkInviteCode?.let { calendarViewModel.acceptInvite(it) }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            calendarViewModel.clearError()
        }
    }

    LaunchedEffect(uiState.inviteResult) {
        uiState.inviteResult?.let {
            snackbarHostState.showSnackbar(it)
            calendarViewModel.clearInviteResult()
        }
    }

    if (showShareDialog && uiState.shareLink != null) {
        ShareDialog(
            shareLink = uiState.shareLink!!,
            onDismiss = {
                showShareDialog = false
                calendarViewModel.clearShareLink()
            },
            onCopyLink = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("초대 링크", uiState.shareLink))
            },
            onShareViaKakao = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "내 캘린더를 공유합니다!\n초대 코드: ${uiState.shareLink}\n\n캘린더 공유 앱에서 👤+ 버튼을 눌러 코드를 입력하세요.")
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "공유하기"))
            }
        )
    }

    if (showSharedCalendarsDialog) {
        SharedCalendarsDialog(
            sharedCalendars = uiState.sharedCalendars,
            onDismiss = { showSharedCalendarsDialog = false }
        )
    }

    if (showInviteCodeDialog) {
        InviteCodeDialog(
            onDismiss = { showInviteCodeDialog = false },
            onSubmit = { code ->
                calendarViewModel.acceptInvite(code)
                showInviteCodeDialog = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("캘린더 공유") },
                actions = {
                    IconButton(onClick = { showInviteCodeDialog = true }) {
                        Icon(Icons.Filled.PersonSearch, "초대 코드 입력")
                    }
                    IconButton(onClick = { showSharedCalendarsDialog = true }) {
                        Icon(Icons.Default.People, "공유 캘린더")
                    }
                    IconButton(onClick = {
                        authViewModel.signOut()
                        onSignOut()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "로그아웃")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    calendarViewModel.createShareLink()
                    showShareDialog = true
                }
            ) {
                Icon(Icons.Default.Share, "캘린더 공유")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MonthCalendarView(
                selectedDate = uiState.selectedDate,
                events = uiState.myEvents + uiState.sharedEvents.values.flatten(),
                onDateSelected = { calendarViewModel.selectDate(it) },
                onMonthChanged = { calendarViewModel.selectDate(it) }
            )

            HorizontalDivider()

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                EventList(
                    selectedDate = uiState.selectedDate,
                    myEvents = uiState.myEvents,
                    sharedEvents = uiState.sharedEvents
                )
            }
        }
    }
}

@Composable
private fun MonthCalendarView(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChanged: (LocalDate) -> Unit
) {
    val yearMonth = YearMonth.from(selectedDate)
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value % 7

    val eventDates = events.flatMap { event ->
        if (event.startTime <= 0) return@flatMap emptyList()
        val startDate = Instant.ofEpochMilli(event.startTime)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val rawEndDate = if (event.endTime > 0) {
            Instant.ofEpochMilli(event.endTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        } else startDate
        // 종일 이벤트: Google Calendar API는 endDate를 exclusive(다음 날 00:00)로 반환
        val endDate = if (event.isAllDay && rawEndDate.isAfter(startDate)) {
            rawEndDate.minusDays(1)
        } else rawEndDate
        val dates = mutableListOf<LocalDate>()
        var d = startDate
        while (!d.isAfter(endDate)) {
            dates.add(d)
            d = d.plusDays(1)
        }
        dates
    }.toSet()

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                onMonthChanged(selectedDate.minusMonths(1))
            }) {
                Icon(Icons.Default.ChevronLeft, "이전 달")
            }

            Text(
                text = "${yearMonth.year}년 ${yearMonth.monthValue}월",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = {
                onMonthChanged(selectedDate.plusMonths(1))
            }) {
                Icon(Icons.Default.ChevronRight, "다음 달")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")
            dayNames.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = when (day) {
                        "일" -> Color.Red
                        "토" -> Color.Blue
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        var dayCounter = 1
        for (week in 0..5) {
            if (dayCounter > daysInMonth) break
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dayOfWeek in 0..6) {
                    if (week == 0 && dayOfWeek < firstDayOfWeek || dayCounter > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val currentDay = dayCounter
                        val date = yearMonth.atDay(currentDay)
                        val isSelected = date == selectedDate
                        val isToday = date == LocalDate.now()
                        val hasEvents = eventDates.contains(date)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) Modifier.background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                                    else if (isToday) Modifier.background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape
                                    )
                                    else Modifier
                                )
                                .clickable { onDateSelected(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$currentDay",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (hasEvents) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.primary,
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        }
                        dayCounter++
                    }
                }
            }
        }
    }
}

@Composable
private fun EventList(
    selectedDate: LocalDate,
    myEvents: List<CalendarEvent>,
    sharedEvents: Map<String, List<CalendarEvent>>
) {
    val zone = ZoneId.systemDefault()
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val filteredMyEvents = myEvents.filter { event ->
        if (event.startTime <= 0) return@filter false
        val startDate = Instant.ofEpochMilli(event.startTime).atZone(zone).toLocalDate()
        val rawEndDate = if (event.endTime > 0) {
            Instant.ofEpochMilli(event.endTime).atZone(zone).toLocalDate()
        } else startDate
        val endDate = if (event.isAllDay && rawEndDate.isAfter(startDate)) {
            rawEndDate.minusDays(1)
        } else rawEndDate
        !selectedDate.isBefore(startDate) && !selectedDate.isAfter(endDate)
    }

    val filteredSharedEvents = sharedEvents.flatMap { (owner, events) ->
        events.filter { event ->
            if (event.startTime <= 0) return@filter false
            val startDate = Instant.ofEpochMilli(event.startTime).atZone(zone).toLocalDate()
            val rawEndDate = if (event.endTime > 0) {
                Instant.ofEpochMilli(event.endTime).atZone(zone).toLocalDate()
            } else startDate
            val endDate = if (event.isAllDay && rawEndDate.isAfter(startDate)) {
                rawEndDate.minusDays(1)
            } else rawEndDate
            !selectedDate.isBefore(startDate) && !selectedDate.isAfter(endDate)
        }.map { it to owner }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = selectedDate.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (filteredMyEvents.isEmpty() && filteredSharedEvents.isEmpty()) {
            item {
                Text(
                    text = "일정이 없습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (filteredMyEvents.isNotEmpty()) {
            item {
                Text(
                    text = "내 일정",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            items(filteredMyEvents) { event ->
                EventCard(event = event, timeFormatter = timeFormatter, zone = zone)
            }
        }

        if (filteredSharedEvents.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "공유 일정",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
            items(filteredSharedEvents) { (event, owner) ->
                EventCard(
                    event = event,
                    timeFormatter = timeFormatter,
                    zone = zone,
                    ownerLabel = owner
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun EventCard(
    event: CalendarEvent,
    timeFormatter: DateTimeFormatter,
    zone: ZoneId,
    ownerLabel: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ownerLabel != null)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(
                        if (ownerLabel != null) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (ownerLabel != null) {
                    Text(
                        text = ownerLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (event.location.isNotBlank()) {
                    Text(
                        text = event.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = if (event.isAllDay) "종일"
                else {
                    val startFormatted = Instant.ofEpochMilli(event.startTime)
                        .atZone(zone)
                        .format(timeFormatter)
                    val endFormatted = Instant.ofEpochMilli(event.endTime)
                        .atZone(zone)
                        .format(timeFormatter)
                    "$startFormatted - $endFormatted"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShareDialog(
    shareLink: String,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit,
    onShareViaKakao: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("캘린더 공유") },
        text = {
            Column {
                Text("아래 초대 코드를 공유하면 상대방이 내 캘린더를 구독할 수 있습니다.")
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = shareLink,
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "상대방은 앱의 👤+ 버튼에서 이 코드를 입력하면 됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onShareViaKakao()
                onDismiss()
            }) {
                Text("공유하기")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onCopyLink()
                onDismiss()
            }) {
                Text("코드 복사")
            }
        }
    )
}

@Composable
private fun SharedCalendarsDialog(
    sharedCalendars: List<com.calendersharing.test.data.model.SharedCalendar>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("구독 중인 캘린더") },
        text = {
            if (sharedCalendars.isEmpty()) {
                Text("구독 중인 캘린더가 없습니다.\n초대 링크를 통해 다른 사람의 캘린더를 구독할 수 있습니다.")
            } else {
                Column {
                    sharedCalendars.forEach { calendar ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = calendar.ownerName.ifBlank { "이름 없음" },
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = calendar.ownerEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("확인") }
        }
    )
}

@Composable
private fun InviteCodeDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var inviteCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("초대 코드 입력") },
        text = {
            Column {
                Text("공유받은 초대 코드를 입력하세요.")
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.trim() },
                    label = { Text("초대 코드") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (inviteCode.isNotBlank()) onSubmit(inviteCode) },
                enabled = inviteCode.isNotBlank()
            ) {
                Text("구독")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
