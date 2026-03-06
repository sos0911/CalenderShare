# CalenderShare

구글 캘린더 연동되는 캘린더 공유 앱 (Android)

## 기능

- Google 계정 로그인 (OAuth 2.0)
- Google Calendar 일정 조회 및 표시
- 초대 코드를 통한 캘린더 공유/구독
- 공유받은 일정과 내 일정을 함께 표시
- 종일/다중일 일정 지원
- FCM 푸시 알림

## 기술 스택

| 항목 | 기술 |
|------|------|
| 언어 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| 인증 | Firebase Auth + Google Sign-In |
| DB | Firebase Firestore |
| 캘린더 | Google Calendar API |
| 푸시 | Firebase Cloud Messaging |

## 프로젝트 구조

```
app/src/main/java/com/calendersharing/test/
├── CalenderSharingApp.kt              # Application
├── di/AppModule.kt                    # Hilt DI
├── data/
│   ├── model/CalendarEvent.kt         # 데이터 모델
│   └── repository/
│       ├── AuthRepository.kt          # Google 로그인
│       ├── GoogleCalendarRepository.kt # Calendar API
│       └── ShareRepository.kt         # Firestore 공유
├── ui/
│   ├── MainActivity.kt
│   ├── theme/Theme.kt
│   ├── navigation/AppNavigation.kt
│   ├── viewmodel/
│   │   ├── AuthViewModel.kt
│   │   └── CalendarViewModel.kt
│   └── screen/
│       ├── login/LoginScreen.kt
│       └── calendar/CalendarScreen.kt
└── notification/CalendarMessagingService.kt
```

## 설정 방법

### 1. Firebase 프로젝트 생성

1. [Firebase Console](https://console.firebase.google.com/)에서 프로젝트 생성
2. Android 앱 추가 (패키지: `com.calendersharing.test`)
3. `google-services.json` 다운로드 → `app/` 폴더에 배치
4. Authentication → Google 로그인 활성화
5. Firestore Database 생성 (보안 규칙: 인증 사용자만 허용)

### 2. Google Cloud Console 설정

1. OAuth 동의 화면 설정
2. OAuth 클라이언트 ID 생성 (Android + 웹 애플리케이션)
3. Google Calendar API 활성화
4. 테스트 사용자 등록

### 3. Firestore 보안 규칙

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### 4. 빌드

```bash
./gradlew assembleDebug
```

## 공유 방식

1. A가 앱에서 공유 버튼 → 초대 코드 생성
2. 카카오톡 등으로 초대 코드 전달
3. B가 앱에서 🔍 버튼 → 초대 코드 입력 → A의 캘린더 구독
4. A의 일정이 B의 앱에 실시간 표시
