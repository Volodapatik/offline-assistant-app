# Offline Assistant

Offline Assistant is a minimal single-activity Android app for local text-based assistant interactions.

## Stack
- Kotlin
- Android SDK (min 26, target 34)
- RecyclerView UI
- Local in-memory message storage

## Project structure
- `app/src/main/java/com/volodapatik/offlineassistant/MainActivity.kt` - Activity and UI orchestration.
- `app/src/main/java/com/volodapatik/offlineassistant/ui/ChatAdapter.kt` - RecyclerView adapter for chat messages.
- `app/src/main/java/com/volodapatik/offlineassistant/model/ChatMessage.kt` - Message model and role enum.
- `app/src/main/java/com/volodapatik/offlineassistant/engine/AssistantEngine.kt` - Assistant engine contract and structured response model.
- `app/src/main/java/com/volodapatik/offlineassistant/engine/SimpleLocalEngine.kt` - Placeholder local assistant logic.

## Build
### Local
1. Install Android SDK and Gradle.
2. Run:
   ```bash
   gradle :app:assembleDebug
   ```

### GitHub Actions
Workflow file: `.github/workflows/android.yml`

It builds the debug APK on each push and pull request to `main`.

## Notes
- No internet APIs are used.
- No internet permission is declared.
- Assistant state is kept in memory for MVP simplicity.
