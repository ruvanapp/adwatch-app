# AdWatch - Earn Money by Watching Ads

AdWatch is an Android app that allows users to earn credits by voluntarily watching rewarded video ads. Users can request PayPal cashouts which are manually reviewed before payout.

## Project Structure

### Android App (`/android-app`)

Multi-module Android application built with:
- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** Clean Architecture + MVVM
- **DI:** Hilt
- **Ads:** Google AdMob (Rewarded Ads)
- **Auth:** Firebase Authentication

#### Modules:
- `app` - Main application module with navigation
- `core-ui` - Shared UI components and theme
- `core-network` - API client, interceptors, auth headers
- `core-storage` - Room database and DataStore preferences
- `feature-auth` - Authentication (signup/login)
- `feature-home` - Home dashboard
- `feature-ads` - AdMob rewarded ad integration
- `feature-wallet` - Balance and transaction history
- `feature-cashout` - PayPal cashout requests
- `feature-trust` - Fraud status and account health

### Backend (`/backend`)

Kotlin backend service built with:
- **Framework:** Ktor
- **Database:** PostgreSQL (via Exposed ORM)
- **Cache:** Redis
- **Auth:** Firebase Admin SDK
- **Payout:** PayPal Payout API

#### Key Services:
- Identity Service - User management
- Ad Reward Service - Reward validation and granting
- Wallet/Ledger Service - Credits accounting
- Fraud & Risk Service - Abuse detection
- Cashout Service - Payout request handling
- Admin Service - Manual review dashboard

## Database Schema

### Main Tables:
- `users` - User profiles
- `user_devices` - Device fingerprinting
- `sessions` - Auth sessions
- `ad_watch_sessions` - Ad completion tracking
- `reward_rules` - Country-based earning rules
- `ledger_entries` - Immutable transaction log
- `wallets` - User balance states
- `cashout_requests` - Payout requests queue
- `payout_transactions` - PayPal transaction records
- `fraud_events` - Suspicious activity log
- `audit_logs` - Admin action audit trail

## Setup Instructions

### Prerequisites:
- Android Studio Hedgehog or later
- JDK 17
- PostgreSQL 14+
- Redis 6+
- Firebase project with Authentication enabled
- AdMob account

### Android App Setup:

1. Open `android-app` in Android Studio
2. Create `google-services.json` from Firebase Console and place in `app/` directory
3. Update AdMob App ID in `app/src/main/AndroidManifest.xml`
4. Sync Gradle and build

### Backend Setup:

1. Install dependencies:
   ```bash
   cd backend
   ./gradlew build
   ```

2. Configure PostgreSQL:
   ```bash
   createdb adwatch
   createuser adwatch -P  # set password
   ```

3. Set environment variables or update `src/main/resources/application.conf`:
   - `DATABASE_URL`
   - `DATABASE_USER`
   - `DATABASE_PASSWORD`
   - `REDIS_URL`
   - `FIREBASE_CREDENTIALS_PATH`
   - `PAYPAL_CLIENT_ID`
   - `PAYPAL_CLIENT_SECRET`

4. Run the backend:
   ```bash
   ./gradlew run
   ```

5. API will be available at `http://localhost:8080`

## Key Features

### Phase 1 (Foundation):
- [x] Android app structure with modular architecture
- [x] Backend API with database schema
- [x] Firebase Authentication integration
- [x] Basic routing and API endpoints
- [ ] Signup/login flows (UI complete, backend integration pending)
- [ ] AdMob rewarded ad integration
- [ ] Wallet balance and transaction ledger

### Phase 2 (Core Functionality):
- [ ] Ad session validation and reward granting
- [ ] Country-based reward rules
- [ ] Rate limiting and cooldowns
- [ ] Play Integrity API integration
- [ ] Cashout request flow
- [ ] Admin dashboard for review
- [ ] PayPal sandbox integration

### Phase 3 (Production Ready):
- [ ] Fraud detection heuristics
- [ ] Device fingerprinting
- [ ] Velocity checks
- [ ] Production security hardening
- [ ] Analytics and monitoring
- [ ] Comprehensive testing

## Monetization Model

**Earning rates vary by country:**
- US/UK: ~$0.003-0.010 per ad (higher eCPM)
- India: ~$0.001-0.003 per ad (lower eCPM)
- Other regions: ~$0.001-0.005 per ad

**Credits system:**
- 100 credits = $1.00 USD equivalent
- Minimum cashout: $5.00 (500 credits)
- User receives 40-60% of realized ad revenue

**Daily caps:**
- US/UK: 20-50 ads/day
- India: 30-70 ads/day
- Configurable per country group

## Anti-Fraud Measures

1. **Device-level:**
   - Play Integrity API verification
   - Emulator/root detection
   - Device fingerprinting

2. **Account-level:**
   - One account per device enforcement
   - Signup cooldown before first cashout
   - Velocity and pattern analysis

3. **Payout-level:**
   - Manual admin review required
   - Credits reserved during review
   - PayPal email validation
   - Duplicate payout destination detection

## API Endpoints

### Public:
- `POST /auth/signup` - Create account
- `POST /auth/login` - Login

### Authenticated:
- `GET /me` - User profile
- `GET /home` - Home dashboard
- `POST /ads/session/start` - Start ad watch session
- `POST /ads/session/claim-reward` - Claim reward
- `GET /wallet` - Get balance
- `GET /wallet/ledger` - Transaction history
- `POST /cashouts/request` - Request payout
- `GET /cashouts` - List cashout requests

### Admin:
- `GET /admin/users` - List users
- `GET /admin/users/{id}` - User details
- `POST /admin/users/{id}/status` - Update user status
- `GET /admin/cashouts` - Payout queue
- `POST /admin/cashouts/{id}/approve` - Approve payout
- `POST /admin/cashouts/{id}/reject` - Reject payout
- `GET /admin/fraud-events` - Fraud alerts
- `POST /admin/reward-rules` - Configure rules

## Security Considerations

1. **Firebase tokens** are verified on backend before processing requests
2. **Rewards are never granted based solely on client events** - backend validates all claims
3. **Ledger is append-only** - no direct balance updates
4. **Cashout requests reserve credits** atomically to prevent double-spend
5. **Admin actions are logged** for audit compliance
6. **PayPal credentials** stored in secure secrets management
7. **Certificate pinning** (optional) for production
8. **Rate limiting** on all API endpoints

## Testing Strategy

### Android:
- Unit tests for ViewModels
- Integration tests for repositories
- UI tests for critical flows (Compose UI tests)

### Backend:
- Unit tests for services
- Integration tests for API endpoints
- Database transaction tests
- Mock PayPal integration for testing

## Deployment

### Android:
1. Update version in `app/build.gradle.kts`
2. Generate signed APK/Bundle
3. Upload to Google Play Console
4. Submit for review (ensure AdMob policies compliance)

### Backend:
1. Docker containerization recommended
2. Deploy to GCP/AWS with managed PostgreSQL and Redis
3. Set up secrets management
4. Configure load balancing and auto-scaling
5. Enable monitoring and alerting

## License

Proprietary - AdWatch Team

## Support

For issues or questions, contact the development team.
