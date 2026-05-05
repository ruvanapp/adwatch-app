# AdWatch - Quick Start Guide

This guide will help you get the AdWatch app running locally for development.

## Prerequisites

- **Java Development Kit (JDK) 17**
- **Android Studio Hedgehog (2023.1.1) or later**
- **Docker Desktop** (for PostgreSQL and Redis)
- **Firebase Account**
- **AdMob Account** (optional for initial development, can use test IDs)

## Step-by-Step Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd ad-watch-app
```

### 2. Firebase Setup

#### Create Firebase Project:
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project or select existing
3. Add an Android app:
   - Package name: `com.adwatch.app`
   - Download `google-services.json`
   - Place it in `android-app/app/google-services.json`

#### Enable Authentication:
1. In Firebase Console, go to **Authentication**
2. Click **Get Started**
3. Enable **Email/Password** provider

#### Get Admin SDK Credentials:
1. In Firebase Console, go to **Project Settings** → **Service Accounts**
2. Click **Generate New Private Key**
3. Save as `backend/firebase-credentials.json`

### 3. Start Backend Services

#### Using Docker Compose (Recommended):
```bash
# Start PostgreSQL and Redis
docker-compose up -d postgres redis

# Check services are running
docker-compose ps
```

#### Manual Setup (Alternative):
```bash
# PostgreSQL
brew install postgresql  # macOS
brew services start postgresql
createdb adwatch
createuser adwatch -P  # password: adwatch

# Redis
brew install redis
brew services start redis
```

### 4. Start Backend Server

```bash
cd backend

# First time - download dependencies
./gradlew build

# Run the server
./gradlew run
```

Backend should start on `http://localhost:8080`

Verify by visiting: `http://localhost:8080/health`

### 5. Configure Android App

#### Open in Android Studio:
1. Launch Android Studio
2. Open `android-app` folder
3. Wait for Gradle sync to complete

#### Update Base URL (for emulator):
Edit `core-network/build.gradle.kts`:
```kotlin
buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080\"")
```
> Note: `10.0.2.2` is how Android emulator accesses host machine's localhost

#### Sync Gradle:
```bash
# In Android Studio, click "Sync Now" or run:
./gradlew build
```

### 6. Run Android App

1. In Android Studio, select an emulator or connected device
2. Click **Run** (green play button) or press `Shift + F10`
3. App should install and launch

## Testing the Setup

### 1. Test Backend API
```bash
# Health check
curl http://localhost:8080/health

# Should return:
# {"status":"healthy","version":"1.0.0"}
```

### 2. Test Database Connection
```bash
# Connect to PostgreSQL
docker exec -it adwatch-postgres psql -U adwatch -d adwatch

# List tables
\dt

# Should show all 11 tables created by Exposed
```

### 3. Test Android App
1. Launch the app
2. You should see the login screen
3. Click "Sign up"
4. Enter email, password, and country (e.g., "US")
5. Click "Sign Up"
6. If successful, you'll be redirected to home screen (currently shows placeholder)

## Common Issues & Solutions

### Issue: "Firebase not initialized"
**Solution:** Ensure `google-services.json` is in `android-app/app/` directory and Gradle is synced.

### Issue: Backend fails to start - "Could not initialize class com.google.firebase.FirebaseApp"
**Solution:** Ensure `firebase-credentials.json` exists in `backend/` directory. If not, the app will warn but continue (auth won't work).

### Issue: Android can't connect to backend
**Solution:** 
- Emulator: Use `http://10.0.2.2:8080`
- Physical device: Use your computer's local IP (e.g., `http://192.168.1.100:8080`)
- Update `BASE_URL` in `core-network/build.gradle.kts`

### Issue: Database connection failed
**Solution:** 
```bash
# Check PostgreSQL is running
docker-compose ps

# Check logs
docker-compose logs postgres

# Restart services
docker-compose restart postgres
```

### Issue: Gradle sync failed
**Solution:**
1. File → Invalidate Caches → Invalidate and Restart
2. Delete `.gradle` folders and sync again
3. Ensure JDK 17 is configured in Android Studio

## Development Workflow

### Making Changes

#### Android:
1. Make code changes
2. Build and run: `Shift + F10`
3. For UI changes, use Compose Preview or run on device

#### Backend:
1. Make code changes
2. Stop server (`Ctrl + C`)
3. Restart: `./gradlew run`
4. Changes will be picked up on restart

### Database Schema Changes
After modifying tables in `backend/src/main/kotlin/com/adwatch/backend/data/table/Tables.kt`:
1. Stop backend
2. Clear database: `docker-compose down -v && docker-compose up -d postgres redis`
3. Restart backend - tables will be recreated

> **Warning:** This deletes all data. In production, use proper migrations.

### Testing with AdMob

#### Test Ads (No AdMob Account Needed):
The app is configured with test ad unit IDs by default:
- App ID: `ca-app-pub-3940256099942544~3347511713`
- Rewarded Ad: `ca-app-pub-3940256099942544/5224354917`

These will show test ads without requiring an AdMob account.

#### Production Ads:
1. Create AdMob account at [AdMob Console](https://apps.admob.com/)
2. Add Android app
3. Create Rewarded Ad Unit
4. Update `android-app/app/src/main/AndroidManifest.xml` with your App ID

## Next Steps

Now that your environment is set up:

1. **Explore the codebase:**
   - Check `docs/PROJECT_STRUCTURE.md` for detailed structure
   - Review `docs/IMPLEMENTATION_STATUS.md` for current progress

2. **Implement remaining features:**
   - Complete feature-ads module with AdMob integration
   - Implement backend services (see todos)
   - Build remaining UI screens

3. **Test the flows:**
   - Sign up → Watch ad → Earn credits → Request cashout
   - Admin review and approve cashout
   - PayPal payout execution

## Useful Commands

```bash
# Backend
./gradlew run                    # Start server
./gradlew build                  # Build project
./gradlew test                   # Run tests

# Docker
docker-compose up -d             # Start all services
docker-compose down              # Stop all services
docker-compose logs -f backend   # View backend logs
docker-compose ps                # List running containers

# Android
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Install on connected device
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests

# Database
docker exec -it adwatch-postgres psql -U adwatch -d adwatch
```

## Getting Help

- Check `README.md` for comprehensive documentation
- Review `docs/SETUP.md` for detailed configuration
- Check `docs/IMPLEMENTATION_STATUS.md` for known issues

---

Happy coding! 🚀
