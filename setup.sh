#!/bin/bash
# AdWatch Project Setup Script
# Run this after installing prerequisites

set -e

echo "==================================="
echo "  AdWatch Project Setup"
echo "==================================="
echo ""

# Check prerequisites
echo "Checking prerequisites..."

if ! command -v java &> /dev/null; then
    echo "ERROR: Java not found. Install with:"
    echo "  brew install openjdk@17"
    echo ""
    echo "Then add to PATH:"
    echo "  echo 'export JAVA_HOME=/opt/homebrew/opt/openjdk@17' >> ~/.zshrc"
    echo "  echo 'export PATH=\$JAVA_HOME/bin:\$PATH' >> ~/.zshrc"
    echo "  source ~/.zshrc"
    exit 1
fi

echo "  Java: $(java -version 2>&1 | head -1)"

if ! command -v docker &> /dev/null; then
    echo "WARNING: Docker not found. Install Docker Desktop for local database."
    echo "  https://www.docker.com/products/docker-desktop/"
fi

# Check Firebase config
echo ""
echo "Checking Firebase configuration..."

if [ ! -f "android-app/app/google-services.json" ]; then
    echo "  WARNING: android-app/app/google-services.json NOT FOUND"
    echo "  Download from Firebase Console:"
    echo "  1. Go to https://console.firebase.google.com/"
    echo "  2. Project Settings → Your Apps → Download google-services.json"
    echo "  3. Place in: android-app/app/google-services.json"
    echo ""
else
    echo "  google-services.json: OK"
fi

if [ ! -f "backend/firebase-credentials.json" ]; then
    echo "  WARNING: backend/firebase-credentials.json NOT FOUND"
    echo "  Download from Firebase Console:"
    echo "  1. Project Settings → Service Accounts"
    echo "  2. Generate New Private Key"
    echo "  3. Save as: backend/firebase-credentials.json"
    echo ""
else
    echo "  firebase-credentials.json: OK"
fi

# Start services
echo ""
echo "Starting database services..."

if command -v docker &> /dev/null; then
    docker-compose up -d postgres redis
    echo "  PostgreSQL: localhost:5432"
    echo "  Redis: localhost:6379"
    sleep 3
else
    echo "  Skipping (Docker not installed)"
fi

# Build backend
echo ""
echo "Building backend..."
cd backend
if [ -f "gradlew" ]; then
    chmod +x gradlew
    ./gradlew build -x test 2>&1 | tail -5
    echo "  Backend build: OK"
else
    echo "  WARNING: No gradlew found in backend/"
fi
cd ..

echo ""
echo "==================================="
echo "  Setup Complete!"
echo "==================================="
echo ""
echo "Next steps:"
echo "  1. Start backend:  cd backend && ./gradlew run"
echo "  2. Open Android Studio → Open 'android-app' folder"
echo "  3. Let Gradle sync complete"
echo "  4. Run app on emulator or device"
echo ""
echo "API will be at: http://localhost:8080"
echo "Health check:   http://localhost:8080/health"
echo ""
