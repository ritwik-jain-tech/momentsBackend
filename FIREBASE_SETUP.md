# Firebase Cloud Messaging Setup

## SenderId Mismatch Error Resolution

The "SENDER_ID_MISMATCH" error occurs when the FCM token from the client doesn't match your Firebase project configuration. Here's how to fix it:

### 1. Get Your Firebase Project Configuration

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: `moments-38b77`
3. Go to Project Settings (gear icon) → General tab
4. Scroll down to "Your apps" section
5. If you don't have a web app, click "Add app" → Web app
6. Copy the Firebase configuration

### 2. Update Client App Configuration

Make sure your client app (Android/iOS/Web) is using the correct Firebase configuration:

**For Android:**
- Update `google-services.json` in your Android project
- Ensure the `project_number` matches your Firebase project

**For iOS:**
- Update `GoogleService-Info.plist` in your iOS project
- Ensure the `PROJECT_ID` matches your Firebase project

**For Web:**
- Use the correct Firebase config object with your project details

### 3. Service Account Setup

1. Go to Firebase Console → Project Settings → Service Accounts
2. Click "Generate new private key"
3. Download the JSON file
4. Place it in `src/main/resources/serviceAccountKey.json`
5. Make sure the file is in `.gitignore` for security

### 4. Environment Variables (Alternative)

Instead of using a service account file, you can set environment variables:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/your/serviceAccountKey.json"
export FIREBASE_PROJECT_ID="moments-38b77"
```

### 5. Verify Project ID

Ensure your Firebase project ID is correctly set in:
- `application.properties`: `firebase.project-id=moments-38b77`
- Client app configuration
- Service account JSON file

### 6. Test FCM Token

The FCM token should be generated from the same Firebase project. If you're testing with a token from a different project, you'll get the SENDER_ID_MISMATCH error.

### Common Issues:

1. **Wrong Project ID**: Client and server using different Firebase projects
2. **Outdated Configuration**: Client app using old Firebase config
3. **Invalid Service Account**: Wrong or missing service account key
4. **Token Mismatch**: FCM token from different Firebase project

### Testing:

Use the `/api/otp/verify` endpoint with a valid FCM token from your Firebase project:

```bash
curl -X POST http://localhost:8080/api/otp/verify \
  -H "Content-Type: application/json" \
  -H "fcm-token: YOUR_VALID_FCM_TOKEN" \
  -d '{
    "phoneNumber": "+1234567890",
    "otp": "123456"
  }'
```
