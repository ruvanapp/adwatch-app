# AdWatch Project Structure

```
ad-watch-app/
├── README.md                           # Main project documentation
├── .gitignore                          # Git ignore rules
├── docker-compose.yml                  # Local development environment
│
├── docs/
│   ├── SETUP.md                        # Setup instructions
│   └── IMPLEMENTATION_STATUS.md        # Current implementation status
│
├── android-app/                        # Android application
│   ├── build.gradle.kts                # Root build configuration
│   ├── settings.gradle.kts             # Module settings
│   ├── gradle.properties               # Gradle properties
│   │
│   ├── app/                            # Main application module
│   │   ├── build.gradle.kts
│   │   ├── proguard-rules.pro
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/adwatch/app/
│   │   │   │   ├── AdWatchApplication.kt      # Application class
│   │   │   │   ├── MainActivity.kt            # Main activity
│   │   │   │   └── navigation/
│   │   │   │       └── AdWatchNavHost.kt      # Navigation graph
│   │   │   └── res/
│   │   │       ├── values/
│   │   │       │   ├── strings.xml
│   │   │       │   └── themes.xml
│   │   │       └── xml/
│   │   │           ├── network_security_config.xml
│   │   │           └── data_extraction_rules.xml
│   │   └── google-services.json        # [REQUIRED] Firebase config (gitignored)
│   │
│   ├── core-ui/                        # Shared UI components
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/adwatch/core/ui/
│   │       └── theme/
│   │           ├── Color.kt
│   │           ├── Type.kt
│   │           └── Theme.kt
│   │
│   ├── core-network/                   # API client & networking
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/adwatch/core/network/
│   │       ├── di/
│   │       │   ├── NetworkModule.kt    # Retrofit, OkHttp DI
│   │       │   └── FirebaseModule.kt   # Firebase Auth DI
│   │       ├── interceptor/
│   │       │   └── AuthInterceptor.kt  # JWT token interceptor
│   │       └── model/
│   │           └── ApiResult.kt        # Result wrapper
│   │
│   ├── core-storage/                   # Local storage
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/adwatch/core/storage/
│   │       └── preferences/
│   │           └── AppPreferences.kt   # DataStore preferences
│   │
│   ├── feature-auth/                   # Authentication feature
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/adwatch/feature/auth/
│   │       ├── navigation/
│   │       │   └── AuthNavigation.kt
│   │       ├── screen/
│   │       │   ├── LoginScreen.kt
│   │       │   └── SignupScreen.kt
│   │       └── viewmodel/
│   │           ├── LoginViewModel.kt
│   │           └── SignupViewModel.kt
│   │
│   ├── feature-home/                   # Home dashboard [STUB]
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/adwatch/feature/home/
│   │       └── navigation/
│   │           └── HomeNavigation.kt
│   │
│   ├── feature-ads/                    # AdMob integration [STUB]
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │
│   ├── feature-wallet/                 # Wallet & ledger [STUB]
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/adwatch/feature/wallet/
│   │       └── navigation/
│   │           └── WalletNavigation.kt
│   │
│   ├── feature-cashout/                # PayPal cashout [STUB]
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/adwatch/feature/cashout/
│   │       └── navigation/
│   │           └── CashoutNavigation.kt
│   │
│   └── feature-trust/                  # Fraud status [STUB]
│       ├── build.gradle.kts
│       └── src/main/
│
└── backend/                            # Kotlin/Ktor backend
    ├── build.gradle.kts                # Backend build config
    ├── settings.gradle.kts
    ├── Dockerfile                      # Production container
    ├── firebase-credentials.json       # [REQUIRED] Firebase Admin SDK (gitignored)
    │
    └── src/main/
        ├── resources/
        │   ├── application.conf        # Configuration
        │   └── logback.xml             # Logging config
        │
        └── kotlin/com/adwatch/backend/
            ├── Application.kt          # Main entry point
            │
            ├── config/
            │   └── DatabaseFactory.kt  # Database initialization
            │
            ├── di/
            │   └── FirebaseInitializer.kt  # Firebase Admin init
            │
            ├── plugins/
            │   ├── Serialization.kt    # JSON serialization
            │   ├── Monitoring.kt       # Logging
            │   ├── HTTP.kt             # CORS
            │   ├── Security.kt         # Firebase JWT auth
            │   └── Routing.kt          # Route registration
            │
            ├── data/
            │   └── table/
            │       └── Tables.kt       # All database tables (Exposed)
            │
            ├── domain/
            │   ├── model/
            │   │   └── Models.kt       # Domain models & enums
            │   ├── request/
            │   │   └── Requests.kt     # API request DTOs
            │   └── response/
            │       └── Responses.kt    # API response DTOs
            │
            └── routes/
                ├── AuthRoutes.kt       # /auth/*
                ├── UserRoutes.kt       # /me, /home
                ├── AdsRoutes.kt        # /ads/*
                ├── WalletRoutes.kt     # /wallet/*
                ├── CashoutRoutes.kt    # /cashouts/*
                └── AdminRoutes.kt      # /admin/*
```

## Key Files to Configure

### Android:
1. `android-app/app/google-services.json` - Firebase configuration (download from Firebase Console)
2. `android-app/app/src/main/AndroidManifest.xml` - Update AdMob App ID before production

### Backend:
1. `backend/firebase-credentials.json` - Firebase Admin SDK credentials
2. `backend/src/main/resources/application.conf` - Database, Redis, PayPal configuration

### Environment Variables (Production):
- `DATABASE_URL` - PostgreSQL connection string
- `DATABASE_USER` - Database username
- `DATABASE_PASSWORD` - Database password
- `REDIS_URL` - Redis connection string
- `FIREBASE_CREDENTIALS_PATH` - Path to Firebase credentials
- `PAYPAL_CLIENT_ID` - PayPal API client ID
- `PAYPAL_CLIENT_SECRET` - PayPal API secret
- `JWT_SECRET` - JWT signing secret
- `APP_ENV` - Environment (development/production)

## Module Dependencies

### Android Module Graph:
```
app
├── core-ui
├── core-network
├── core-storage
├── feature-auth
│   ├── core-ui
│   ├── core-network
│   └── core-storage
├── feature-home
│   ├── core-ui
│   └── core-network
├── feature-ads
│   ├── core-ui
│   └── core-network
├── feature-wallet
│   ├── core-ui
│   └── core-network
├── feature-cashout
│   ├── core-ui
│   └── core-network
└── feature-trust
    └── core-ui
```

## Database Tables

1. **users** - User profiles and status
2. **user_devices** - Device fingerprints
3. **sessions** - Auth sessions
4. **ad_watch_sessions** - Ad completion tracking
5. **reward_rules** - Country-based earning configuration
6. **ledger_entries** - Immutable transaction log
7. **wallets** - User credit balances
8. **cashout_requests** - Payout requests queue
9. **payout_transactions** - PayPal transactions
10. **fraud_events** - Fraud detection log
11. **audit_logs** - Admin action audit trail

## API Endpoints

### Public:
- `GET /` - API info
- `GET /health` - Health check
- `POST /auth/signup` - Create account
- `POST /auth/login` - Login

### Authenticated (requires Firebase token):
- `GET /me` - User profile
- `GET /home` - Dashboard data
- `POST /ads/session/start` - Start ad session
- `POST /ads/session/claim-reward` - Claim reward
- `GET /wallet` - Get balance
- `GET /wallet/ledger` - Transaction history
- `POST /cashouts/request` - Request cashout
- `GET /cashouts` - List cashout requests

### Admin:
- `GET /admin/users` - List users
- `GET /admin/users/{id}` - User details
- `POST /admin/users/{id}/status` - Update user status
- `GET /admin/cashouts` - Cashout queue
- `POST /admin/cashouts/{id}/approve` - Approve cashout
- `POST /admin/cashouts/{id}/reject` - Reject cashout
- `GET /admin/fraud-events` - Fraud alerts
- `POST /admin/reward-rules` - Configure earning rules

## Technology Stack Summary

### Android:
- Kotlin 1.9.20
- Jetpack Compose (BOM 2023.10.01)
- Material 3
- Hilt 2.48
- Retrofit 2.9.0 + OkHttp 4.12.0
- Kotlinx Serialization 1.6.2
- Firebase Auth 22.3.1
- Firebase Analytics & Crashlytics
- AdMob 22.6.0
- Play Integrity 1.3.0
- Room 2.6.1
- DataStore 1.0.0
- Navigation Compose 2.7.6

### Backend:
- Kotlin 1.9.20
- Ktor 2.3.7
- Exposed 0.46.0
- PostgreSQL 42.7.1
- HikariCP 5.1.0
- Redis (Lettuce 6.3.1)
- Firebase Admin SDK 9.2.0
- Logback 1.4.14

### Infrastructure:
- PostgreSQL 14
- Redis 6
- Docker & Docker Compose
- Gradle 8.5
