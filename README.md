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
2. Provide llama.cpp sources:
   - Preferred: add llama.cpp as a submodule in `app/src/main/cpp/llama`.
   - Alternatively: let CMake fetch it during configure (requires network access during build). You can pin a version with `-DLLAMA_FETCH_TAG=<tag>` in CMake args.
3. Run:
   ```bash
   ./gradlew :app:assembleDebug
   ```

### GitHub Actions
Workflow file: `.github/workflows/android.yml`

It builds the debug APK on each push and pull request to `main`.

## Offline model setup
- The app does not ship a model.
- Transfer a GGUF file to your device (USB/OTG/Bluetooth) into Downloads.
- Open the app, select the model when prompted, and it will copy into app storage at `filesDir/models/model.gguf`.
- After import, the app works fully offline.

## Engine selection
- `EngineProvider` checks for a real model asset and whether the JNI library loads.
- If both are available, it uses `LlamaEngine`.
- Otherwise it falls back to `SimpleLocalEngine` and tells the user: `Offline model not installed. Using local fallback engine.`

## Runtime notes
- `LLM: Ready` means llama.cpp is loaded and real inference is enabled.
- Example prompt: `Привіт, напиши HTML з кнопками` should return actual HTML output offline.

## Notes
- No internet APIs are used.
- No internet permission is declared.
- Assistant state is kept in memory for MVP simplicity.
