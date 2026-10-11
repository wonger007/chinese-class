# Project Guidelines & AI Workflow: Hok6

This repository follows the multi-AI agent cooperation playbook (Claude Code, OpenAI Codex, xAI Grok Build, and Gemini/Antigravity).

## 1. Project Mission, System Architecture & Macro Vision
- **Core Purpose & Vision:** "Hok6: Trace, Write and Learn" is an Android tablet and phone app for working through textbooks (PDF, Word, audio): write and trace on pages, play audio, and practice stroke order, worksheets, and quizzes for character-based languages.
- **Packages & Namespaces:**
  - Release: `com.wonger.hok6` ("Hok6")
  - Debug: `com.wonger.hok6.debug` ("Hok6 (test)")
  - Kotlin code package: `com.studybook.reader`
- **Supported Languages:** `yue` (Cantonese), `cmn` (Mandarin), `ja` (Japanese), `ko` (Korean); profiles in `Core.LANGS` (`app/src/main/assets/training/core.js`). Focus is square-cell scripts with stroke order (characters, kana, hangul).
- **System Architecture & Component Map:**
  - **Android (Kotlin):** `app/src/main/java/com/studybook/reader/`
    - `MainActivity.kt`: book cards, folder navigation, backup and restore
    - `ChapterActivity.kt`: PDF and Word document reader with ink tracing layer and audio playlist
    - `TrainingActivity.kt`: hosts the WebView for Practice & Quiz (`assets/training/`) and native bridges
    - `AboutActivity.kt`, `SetupActivity.kt`: About page, updates, and language/download setup
    - `Ink.kt`, `PageDrawing.kt`: touch/stylus ink handling, stroke rendering, PDF export with tracing
    - `PdfText.kt`, `PdfOcr.kt`: text extraction and on-device OCR
    - `Handwriting.kt`, `Downloads.kt`: Google ML Kit handwriting models and TTS voice engines
    - `SystemBars.kt`: edge-to-edge system bar insets for Android 15+
  - **Practice & Quiz (HTML/JS):** `app/src/main/assets/training/`
    - `index.html`, `app.js`, `style.css`: Practice & Quiz UI
    - `core.js`: stroke order generation, decomposition, readings, offline dictionary lookup
    - `hanzi.bin`, `kanji.bin`, `hanja.bin`: binary stroke data bundles
    - `readings/`: readings sharded by codepoint (Jyutping, Pinyin, Japanese, Korean)
    - `dict-*/`: offline dictionary shards
  - **Tests:** `app/src/test/`
    - `java/com/studybook/reader/`: Kotlin unit tests
    - `js/*.test.js`: Node.js tests for `core.js`
  - **Data & Backup:** All user data saved locally and synced to `Hok6 backup.json` in user's book folder. Zero external servers or cloud accounts.
- **Project Rules & Invariants:**
  - **Language-Agnostic Wording:** User-facing text never says "Chinese". Use "word", "character", "the language you're learning", "voices".
  - **Test Every Language:** Any language test or feature check must cover Japanese and Korean as well as Cantonese/Mandarin.
  - **Credit Every Source:** Add new libraries/data sources to README Credits and in-app About (`app/src/main/assets/about.html`).
  - **`/test/` is Owner's Real Book:** Git-ignored; never commit it or put sample/fake files there. Unit tests belong in `app/src/test/`.
  - **No Data Collection:** Google Play Families Policy compliant: zero ads, accounts, tracking, or analytics. Keep `PRIVACY.md` accurate.
  - **Secrets Outside Repo:** Upload keystore, `~/.gradle/gradle.properties` passwords, and Play service account keys must NEVER be committed.
  - **Code Style:** Idiomatic Kotlin; vanilla ES2020+ JavaScript (pure functions tested in `core.test.js`). Plain sentence commit messages matching git history.

## 2. Project Commands
- **Run Tests:** `tools/run_tests.sh` (Kotlin unit tests + JavaScript tests; no device needed)
- **Build Debug APK:** `./gradlew assembleDebug`
- **Build Release APKs:** `./gradlew assembleRelease` (one APK per CPU in `app/build/outputs/apk/release/`)
- **Build Play Bundle:** `./gradlew bundleRelease`
- **Device Checks:** `python3 tools/device_check.py` (checks connected device or emulator)
- **Regenerate Assets:** `python3 tools/build_assets.py` (regenerates stroke/reading data)
- **Google Play Release:** `tools/play_upload.py` (upload & track release via Play Developer API)
- **Device & Environment Notes:**
  - WSL reaches devices via `tools/adbw` (translates file paths; standard `adb` is not on WSL PATH).
  - Always upgrade in place (`tools/adbw install -r`); never uninstall on test devices.
  - Test only with the debug app (`com.wonger.hok6.debug`), never the owner's release data.
  - JDK 17, Android SDK platform 36 (targetSdk/compileSdk 36).

## 3. Antigravity's Purpose & How Builders Help
- **Antigravity's Role:** Antigravity (Gemini) is the repository orchestrator, bootstrapper, and dedicated final reviewer. It initializes project standards, performs high-context full repository sweeps, and audits completed work.
- **How Builders (Claude / Codex / Grok) Help Antigravity:**
  1. **Atomic, Descriptive Commits:** Commit work with clear messages matching repository history (`git commit -m "..."`). Antigravity inspects `git show HEAD` or commit ranges; clean commits enable rapid, precise reviews.
  2. **Hand Off Green Code Only:** Always run `tools/run_tests.sh` before handing off. Antigravity focuses on logic, edge cases, and regressions—never hand off broken syntax or failing tests.
  3. **Leave Context Continuity Breadcrumbs:** When completing a task or switching sessions, leave a clear summary (what was done, open decisions, next steps scaled to scope) so Antigravity or the next builder can pick up immediately via `git log -n 5 -p`.
  4. **Respect Docs-Only Boundaries:** Treat the codebase as ground truth for documentation (`USER_GUIDE.md`, `README.md`). Never modify application code during docs tasks. If dynamic runtime output is unknown, ask the user to run terminal commands rather than guessing.
  5. **Keep the Working Tree Clean:** Remove all debug debris (`console.log`, `print()`, scratch files) and ensure agent caches (`.claude/`, `.codex/`, `.grok/`, `.gemini/`, `.antigravity/`) remain ignored in `.gitignore`.

## 4. Builder Guidelines (Claude / Codex / Grok)
- **Autonomy:** You have complete freedom in how you reason, plan, and implement solutions. Leverage your natural strengths (deep reasoning, rapid prototyping, dense test generation).
- **Definition of Done (Exit Criteria):** Before declaring completion or handing off for review:
  1. **Intake the bigger picture:** Read Section 1 above for Android/JS architecture, language invariants, and test commands.
  2. Implement focused, clean code scoped strictly to the request.
  3. Run `tools/run_tests.sh`. Never hand off uncompiled code or failing unit tests.
  4. Add or update tests in `app/src/test/` for new behavior or bug fixes.
  5. Remove debug debris (`console.log`, `println`, scratch files).
  6. Commit changes with a descriptive message in plain sentences matching git history.
  7. **Context Continuity:** When starting a fresh session, inspect `git status`, recent commits (`git log -n 5 -p`), or local session history on disk to restore context before asking questions.

## 5. Reviewer Guidelines (Gemini / Antigravity)
- **Role:** Antigravity / Gemini is the dedicated final reviewer.
- **Scope:** Review `git show HEAD` (or the task's commit range) read-only.
- **Checklist:**
  - Logic bugs, boundary edge cases, and regressions across all 4 supported languages.
  - Adherence to language-agnostic wording ("word", "character", not "Chinese").
  - Privacy and safety (zero tracking/analytics, no hardcoded secrets or keystores).
  - Documentation dependencies: verify `USER_GUIDE.md` and `README.md` match actual UI and code.
- **Format:** List each problem with file, line, why, and confidence.
- **Verification:** Re-run `tools/run_tests.sh` to confirm zero regressions before final handoff.

## 6. Technical Documentation Standards & Docs-Only Tasks
- **Code as Ground Truth:** All documentation (`USER_GUIDE.md`, `README.md`, `PRIVACY.md`) must accurately reflect the actual Kotlin and JS implementation. Never guess UI labels, flags, or behavior.
- **Docs-Only Mode:** When tasked with writing or editing documentation, builders must strictly modify documentation files. Do not modify application source code unless explicitly instructed.
- **Documentation Verification Checklist:**
  - Verify every code snippet, terminal command, and configuration block against actual implementation.
  - Verify doc prerequisites, cross-references, and relative file links.
  - Check readability, grammar, spelling, and Markdown heading/table rendering.
- **Resolving Runtime Unknowns:** If dynamic outputs, device behavior, or UI states cannot be determined by static code reading, never guess. Give the user the exact command or test snippet to run in their environment (e.g. via `tools/adbw`), and use the real output to write accurate documentation.

## 7. Solo Mode (Gemini Builds Alone)
- When no builder is available, Gemini/Antigravity can lead and build following the same Builder Guidelines and Definition of Done.
- **Reviewing its own work:** Because a model reviewing its own work shares its blind spots:
  - Review in a fresh session or with a different model from the picker.
  - For large or risky changes, flag the commit as *"not independently reviewed"* so a builder model can review it when available later.

## 8. Boundaries & Safety
- **Strict Project Boundary:** All work, reviews, and context stay strictly inside `/git/hok6`.
- Never leak keystores, passwords, or credentials into other directories or global configs.
- **Git Hygiene:** Ensure AI agent scratch and cache directories (`.claude/`, `.codex/`, `.grok/`, `.gemini/`, `.antigravity/`, `*.log`) are excluded in `.gitignore` and never committed.
