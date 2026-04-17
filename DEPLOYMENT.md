# SecureChat Android - Deployment Guide

## Production Release Process

### Phase 1: Pre-Release Checklist

#### Security
- [ ] ProGuard/R8 mixing enabled (`minifyEnabled = true`)
- [ ] All hardcoded secrets removed
- [ ] Network security config enforces HTTPS
- [ ] No cleartext traffic for production domain
- [ ] Sensitive logs stripped (`-assumenosideeffects`)

#### Functionality
- [ ] All 4 tabs fully functional (Messages/Contacts/Channels/Settings)
- [ ] Chat: text/image/voice/file sending works
- [ ] Onboarding: complete flow tested end-to-end
- [ ] Contacts: add/search/request acceptance tested
- [ ] Channels: create/view/post tested
- [ ] Push: FCM token registration + notification delivery tested
- [ ] Deep links: notification click → correct conversation

#### Localization
- [ ] English UI strings finalized
- [ ] App name + description set in `strings.xml`
- [ ] Privacy policy URL configured (Settings → About)

#### Branding
- [ ] App icon generated (192x192, 512x512)
- [ ] Launcher icon set
- [ ] App colors match SecureChat theme (blue/dark)
- [ ] Splash screen (if needed)

#### Performance
- [ ] Cold start time < 3s
- [ ] Message list scrolling smooth (60 FPS)
- [ ] Image upload responsive (<5s for small images)
- [ ] Memory usage < 150MB (typical)

#### Testing
- [ ] Unit tests passing
- [ ] Integration tests passing (Firebase/WebSocket)
- [ ] Manual QA on real device (not just emulator)
- [ ] Battery test (30min of normal use)
- [ ] Network test (WiFi + 4G + airplane mode recovery)

### Phase 2: Build Release AAB

#### Generate Release Keystore

```bash
keytool -genkey -v -keystore keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias securechat \
  -keypass $KEY_PASSWORD \
  -storepass $KEYSTORE_PASSWORD
```

#### Build AAB (Android App Bundle)

```bash
export KEYSTORE_PASSWORD="your_pass"
export KEY_ALIAS="securechat"
export KEY_PASSWORD="your_key_pass"

./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

#### Verify Build

```bash
# Check ProGuard mapping
cat app/build/outputs/mapping/release/mapping.txt

# Verify signature
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

### Phase 3: Google Play Console Upload

1. **Create Play Console Project**
   - Go to [Google Play Console](https://play.google.com/console)
   - Create new app: `SecureChat`
   - Content rating: Moderate
   - Category: Communication

2. **App Signing Setup**
   - Enable Play App Signing (recommended)
   - Upload your release key for future builds

3. **Upload Release AAB**
   - Build → Release → Internal Testing
   - Upload `app-release.aab`
   - Fill version code: 1
   - Fill version name: 1.0.0

4. **Configure Release**
   - Release name: "v1.0.0 - Initial Launch"
   - Release notes (optional):
     ```
     🔒 SecureChat v1.0.0 - End-to-End Encrypted Messaging
     
     Features:
     • E2EE messaging with AES-GCM
     • Friend discovery by alias ID
     • Public channels
     • Image/voice/file sharing
     • Push notifications via FCM
     • Full onboarding flow
     
     Privacy: Your keys never leave this device.
     ```

5. **Compliance**
   - Fill data privacy questionnaire
   - Declare permissions: Internet, Notifications, Media
   - Content rating: Moderate
   - Region availability: Select target regions

6. **Review & Publishing**
   - Submit to Internal Testing (1-2 hours review)
   - Test with 5-10 real users
   - Promote to Staged Rollout: 5% → 10% → 50% → 100%
   - Monitor crash reports + user feedback

### Phase 4: Beta Testing (Staged Rollout)

```
Day 1-2:   5% rollout (50 users)
           ↓ monitor crashes, ANRs
Day 3-4:   10% rollout (500 users)
           ↓ monitor network issues
Day 5-7:   50% rollout (5000 users)
           ↓ validate performance at scale
Day 8-14:  100% rollout (production)
```

**Monitoring Dashboard:**
- Crashes: target < 0.1% daily
- ANRs (App Not Responding): target < 0.05% daily
- Ratings: target ≥ 4.0 stars

**If Critical Issues Found:**
```bash
# Quickly deploy patch
./gradlew bundleRelease  # bump versionCode in build.gradle.kts
# Upload new AAB to Play Console
```

### Phase 5: Production Monitoring

#### Metrics to Track

1. **User Growth**
   - Daily active users (DAU)
   - Monthly active users (MAU)
   - Retention (Day 1, Day 7, Day 30)

2. **Performance**
   - Crash-free sessions %
   - ANR-free sessions %
   - P50/P95/P99 latencies

3. **Network**
   - WebSocket connection success rate
   - Message delivery latency
   - Image upload success rate

4. **Business**
   - Install source attribution
   - In-app referrals
   - User feedback (ratings/reviews)

#### Alert Thresholds

```
🔴 CRITICAL
- Crash rate > 1% daily → rollback immediately
- WebSocket conn failure > 30% → check relay server
- ANR rate > 0.5% → investigate blocking calls

🟡 WARNING
- Crash rate 0.1%-1% → investigate + prepare patch
- User ratings drop < 3.5 stars → review feedback
- Install volume down 50% → check ASO/marketing

✅ HEALTHY
- Crash rate < 0.1%
- User ratings 4.0-5.0 stars
- DAU growth 10-20% week-over-week
```

#### Monitoring Tools

- **Firebase Console**
  - Crash reporting
  - Performance monitoring
  - Remote config (feature flags)

- **Google Play Console**
  - Install metrics
  - User reviews
  - Ratings distribution

- **Custom Dashboard** (optional)
  - Analytics events to BigQuery
  - Custom alerts via Slack webhook

### Phase 6: Updates & Maintenance

#### Patch Release (v1.0.1)

```bash
# In build.gradle.kts:
# versionCode = 2        (required increment)
# versionName = "1.0.1"  (semantic versioning)

./gradlew bundleRelease
# Upload to Play Console (same process)
```

#### Major Release (v1.1.0)

```bash
# Add new features:
# - Video calls
# - End-to-end backup
# - Disappearing messages

# Changelog:
# ✨ Features
# 🐛 Bug fixes
# 🔧 Improvements
```

#### Emergency Hotfix

If critical security issue found:

```bash
# 1. Increment versionCode
# 2. Build + upload immediately
./gradlew bundleRelease

# 3. Publish to 10% staged rollout first
# 4. If stable after 2 hours → publish to 100%
# 5. Notify users via in-app banner (if time permits)
```

## Troubleshooting Deployment

### Build Fails: "Signature Verification Failed"

```
症状: zipalign error / signing issue
解决:
1. Clean keystore: rm keystore.jks
2. Regenerate: keytool -genkey ...
3. Update environment variables
```

### Play Console Rejects AAB

```
症状: Upload fails with validation error
原因: 
- versionCode < previously uploaded code
- Incompatible Android version
- Missing required assets

解决:
1. Check build.gradle.kts versionCode
2. Verify minSdk = 26, targetSdk = 35
3. Test locally: ./gradlew installRelease
```

### Firebase Cloud Messaging Not Working

```
症状: FCM tokens not registering / pushes not arriving
原因:
- google-services.json misconfigured
- Server Sender ID mismatch
- Device not receiving FCM broadcasts

解决:
1. Re-download google-services.json from Firebase Console
2. Check Server Sender ID matches in relay-server config
3. Test device: Settings → Apps → Google Play Services → Clear cache
```

### Crash Rate Spike After Release

```
症状: Users reporting frequent crashes
原因:
- Device-specific bug (old Android version)
- SDK incompatibility
- Network connectivity issue

解决:
1. Check Firebase Crash Reporting for stack trace
2. Identify affected Android versions (crashanalytics)
3. If > 1%, initiate rollback:
   - Play Console → Release → Halt rollout
   - Prepare patch
   - Re-publish as new version
```

## Post-Launch Checklist

- [ ] Monitor crash dashboard for 48 hours
- [ ] Respond to user reviews within 24 hours
- [ ] Create in-app feedback mechanism
- [ ] Set up automated alerts for thresholds
- [ ] Document known issues + workarounds
- [ ] Plan first maintenance window (1-2 weeks post-launch)

## Rollback Procedure

If production issue discovered:

```bash
# 1. Play Console: Halt current rollout
#    Release → Cancel/Rollback

# 2. Users on old version unaffected
#    New installs get previous working version

# 3. Prepare patch:
#    - Fix issue
#    - Increment versionCode
#    - Test locally

# 4. Republish:
#    ./gradlew bundleRelease
#    Upload to Play Console
#    Start at 10% staged rollout

# Time to mitigate: 30-60 minutes
```

---

**Contact**  
- Security issues: security@daomessage.com
- Tech support: support@daomessage.com
- Last updated: 2026-04-15
