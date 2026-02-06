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
- `app/src/main/java/com/volodapatik/offlineassistant/engine/EngineProvider.kt` - Runtime engine selection.
- `app/src/main/java/com/volodapatik/offlineassistant/engine/LlamaEngine.kt` - JNI-backed local LLM engine (loads a GGUF asset).
- `app/src/main/java/com/volodapatik/offlineassistant/engine/SimpleLocalEngine.kt` - Local fallback engine.

## Build
### Local
1. Install Android SDK, NDK (26.1.10909125), and CMake (3.22.1).
2. Place your GGUF model at `app/src/main/assets/model.gguf` locally.
3. Run:
   ```bash
   ./gradlew :app:assembleDebug
   ```

### GitHub Actions
Workflow file: `.github/workflows/android.yml`

It builds the debug APK on each push and pull request to `main`.

## Offline model setup
- The app expects a GGUF file at `app/src/main/assets/model.gguf`.
- Do not commit this file to git or push it to GitHub.
- Keep the model small enough for mobile memory limits.

## Engine selection
- `EngineProvider` checks for a real model asset and whether the JNI library loads.
- If both are available, it uses `LlamaEngine`.
- Otherwise it falls back to `SimpleLocalEngine` and tells the user: `Offline model not installed. Using local fallback engine.`

## Notes
- No internet APIs are used.
- No internet permission is declared.
- Assistant state is kept in memory for MVP simplicity.
