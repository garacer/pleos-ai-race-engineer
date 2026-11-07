# TTS contract correction and remaining engine failure

2026-11-01. Weekly usage at start13:11:43UTC/22:11:43KST:43%; stop threshold60% used /40% remaining.

## Verified findings

1. The earlier local READY_TIMEOUT was an application-side prerequisite problem. The installed ON_DEVICE runtime did not send onReady while the app merely waited after successful initialize/setLocale. Moving listener registration from inside onSuccess to immediately after initialize invocation did not solve it. A filtered timeline proves LISTENER_REGISTERED precedes onServiceConnected/registerCallback/API_INITIALIZED; readiness still did not arrive in8seconds. This rules out that tested listener-registration race as the explanation for the reproduced wait.
2. A separate explicitly labelled diagnostic Activity requested one fixed Korean phrase after API initialization, without claiming engine readiness. Its actual sequence was API_INITIALIZED(ready_events0) → explicit request1 → ready event1 → start event1 → ON_DEVICE_ENGINE_ERROR, completion0. The request button became disabled and there was no retry. Thus the observed runtime can deliver the first ready event after the first request. This observation is specific to the tested emulator/SDK tuple, not a universal promise about future versions.
3. Actual Korean speech is still NOT validated. The SDK error is an on-device engine failure. The emulator host log also contains a PulseAudio driver initialization failure, but the evidence does NOT establish that this caused ON_DEVICE_ENGINE_ERROR. Missing Korean models, unsupported Korean, microphone permissions and missing credentials are not established causes.

## Source correction

In a new preserved project JEV_Pleos_TtsContract, SpeechLifecycle now distinguishes API REQUESTABLE from observed engine READY and successful playback. API initialization plus completed locale configuration permits a bounded explicit synthesis request. API success does not increment ready/start/done or claim audible speech. The onReady callback received during an active request records readiness without changing SPEAKING back to a requestable phase, so it cannot admit a duplicate concurrent request. Initialization timeout still applies to missing API initialization; request/cancel deadlines and terminal failures remain intact.

LocalSpeech records can_request and engine_ready_observed separately. The dashboard manual test retains one preparation/one utterance per Activity instance and UX restrictions. The original session Activity's initialization was not removed to conceal errors. A separate internal diagnostic screen remains available via TTS API 진단; it is not exported and never used for automatic coaching.

## Validation

- Final debug APK0.2.3-tts-contract-dev, versionCode5, com.jev.drivecoach.dashboard built offline with existing JDK17/SDK35/Gradle8.9. No dependency/license/auth changes.
-45 project JVM tests passed: SpeechLifecycle13 plus existing32, zero failures/errors/skips. They cover API-only requestability without fabricated readiness, ready-after-request without duplicate admission, terminal errors without false completion, no automatic retry, lifecycle close/cancel and existing replay/door/session logic.
- Final APK installed and launched on the existing Pleos2.1/API34/Android14 emulator with CPU SwiftShader. GPU/ComfyUI/Wan work was untouched.
- Final normal dashboard preparation-only runtime check observed past the old8second deadline: phase REQUESTABLE, initialized=true, can_request=true, ready=false, engine_ready_observed=false, requested0, error empty. Manual request button was enabled. No additional utterance was requested in this final verification.
- Earlier isolated API probe made exactly one utterance request and observed ON_DEVICE_ENGINE_ERROR as above. Physical audibility was not independently observed; completion0 means speech must not be marked successful.
- Existing dashboard HTML/ten turns/large gauges/dummy labels were not changed in this TTS checkpoint. Previous layout and replay validation remains inherited evidence; no new real-vehicle or driving-state claim.

## Concrete remaining dependency and restart point

The remaining runtime block is ON_DEVICE_ENGINE_ERROR in the bundled Pleos Speech Service4.2.10-coral-aosp with TextToSpeech3.3.1. No exact missing-resource or repair package is identified. The previous report's Speech SDK1.17.1 is historical; it was not re-established as the current underlying version in this run.

Next bounded investigation: inspect the emulator process's existing user audio-session connection and confirm whether its PA driver can use that session; do not replace PipeWire, change ACLs or infer that historical ordinary speaker playback validates the current headless emulator. If a known-good output backend still produces this engine error, use the saved error timeline/runtime tuple with official Pleos support to identify the matching bundled engine/resource repair. No support message, resource installation or service restart was performed here. Do not blindly repeat utterances or install arbitrary Android TTS engines.

Latest local project: tts-contract-diagnosis-20261101T1311Z/JEV_Pleos_TtsContract.
Remote project: /home/garamai/projects/pleos-JEV-20261030/tts-contract-20261101T1311Z/JEV_Pleos_TtsContract.
Latest APK: JEV_TtsContract_0.2.3_debug.apk (debug, not submission release).
SHA256:7ab3d736b5fa835192bec628ea5ff0a5f14c6c87cfefe6cb1e2e911033d7b14e.

All previous APK/source/video checkpoints remain preserved. No host-key, security, network, account, GPU, ComfyUI, system audio configuration or real vehicle change was made.

## Official contract sources consulted

- Initialization result exposes API availability: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/initialization-and-release
- Event descriptions distinguish readiness/start/completion/error: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/event-listeners
- Synthesis request usage, with connection prerequisites stated for registered server modes: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/start-and-stop
- ON_DEVICE needs no app registration/network: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro
- Korean locale support and configuration: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/set-locale

Cached official AAR inspection was used locally to trace callback registration. The source/evidence deliverable excludes SDK AAR/JAR and disassembly files; it contains the app changes and measured results.
