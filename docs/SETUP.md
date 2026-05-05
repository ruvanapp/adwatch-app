# Firebase Configuration

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project or select existing
3. Enable Authentication with Email/Password provider
4. Download `google-services.json` and place in `android-app/app/`
5. Download Admin SDK credentials JSON for backend

# AdMob Configuration

1. Go to [AdMob Console](https://apps.admob.com/)
2. Create an app for Android
3. Create a Rewarded Ad Unit
4. Update the App ID in `android-app/app/src/main/AndroidManifest.xml`
5. Use test ad unit IDs during development:
   - Test App ID: `ca-app-pub-3940256099942544~3347511713`
   - Test Rewarded Ad Unit: `ca-app-pub-3940256099942544/5224354917`

# PayPal Setup

1. Create a developer account at [PayPal Developer](https://developer.paypal.com/)
2. Create a REST API app
3. Get Client ID and Secret for sandbox
4. Configure webhook endpoints for payout notifications
5. Set environment variables in backend config

# PostgreSQL Setup

```bash
# Install PostgreSQL
brew install postgresql  # macOS
# or use Docker
docker run --name adwatch-postgres -e POSTGRES_PASSWORD=adwatch -p 5432:5432 -d postgres:14

# Create database
createdb adwatch
createuser adwatch -P
```

# Redis Setup

```bash
# Install Redis
brew install redis  # macOS
# or use Docker
docker run --name adwatch-redis -p 6379:6379 -d redis:6

# Start Redis
redis-server
```
