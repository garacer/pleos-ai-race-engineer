# 플레오스 AI 레이스 엔지니어

**한 줄 소개:** 내 서킷 주행 기록을 재생하며 GPS 서킷 지도, 랩·섹터 비교, 한국어 코칭으로 정차 후 주행을 복기하는 Pleos 차량용 앱입니다.

- 팀명: 플레오스 AI 레이스 엔지니어
- 버전: 0.2.7 (versionCode 9)
- 패키지: `com.jev.drivecoach.dashboard`
- 언어/플랫폼: Kotlin, Android API 28 이상, Pleos Connect (AAOS, API 34)

> 현재 범위는 **기록 재생과 정차 후 복기**입니다. 실차 실시간 안내, 실제 브레이크 신호 기반 제동점, 검증된 최적 라인, 노면 AR은 제공하지 않습니다.

## APK 위치

- 서명된 release APK: [`apk/플레오스AI레이스엔지니어_플레오스AI레이스엔지니어_release.apk`](apk/)
- 이 APK는 팀이 관리하는 release 키로 서명되어 있고, 디버깅이 꺼진 빌드입니다.

## 실행 방법

### 1) APK 설치 (가장 빠른 방법)

Pleos 에뮬레이터를 실행한 뒤:

```sh
adb install "apk/플레오스AI레이스엔지니어_플레오스AI레이스엔지니어_release.apk"
adb shell am start -n com.jev.drivecoach.dashboard/com.jev.drivecoach.DashboardActivity
```

같은 패키지의 다른 서명 빌드(예: debug)가 이미 설치돼 있으면 `adb uninstall com.jev.drivecoach.dashboard` 후 설치합니다.
런처에서는 **플레오스 AI 레이스 엔지니어** 아이콘으로 실행할 수 있습니다.

### 2) 소스에서 빌드

```sh
./gradlew testDebugUnitTest assembleDebug
./gradlew lintRelease assembleRelease
```

Windows에서는 `gradlew.bat`를 사용합니다. 의존성이 캐시에 있으면 `--offline`을 쓸 수 있습니다.

- debug 출력: `app/build/outputs/apk/debug/app-debug.apk`
- release 출력: 서명 설정 파일이 있으면 `app-release.apk`, 없으면 `app-release-unsigned.apk`

release 서명 정보는 저장소에 넣지 않습니다. 빌드할 때 저장소 밖의 properties 파일을 읽습니다.
(`-PjevSigningProps=<파일>` 또는 환경변수 `JEV_SIGNING_PROPS`, 기본값 `~/.config/jev-signing/keystore.properties`)

## 테스트 환경 및 필요한 설정

| 항목 | 값 |
|---|---|
| JDK | 17 |
| Gradle / AGP / Kotlin | 8.9 (wrapper) / 8.7.3 / 2.0.21 |
| SDK | compileSdk 35, targetSdk 34, minSdk 28 |
| Pleos SDK | Vehicle 2.3.2, TextToSpeech 3.3.1 (`settings.gradle.kts`의 공식 Pleos Maven 저장소) |
| 에뮬레이터 이미지 | `system-images;android-34;pleos_car_emulator;x86_64` (Pleos Connect 2.1.0 / Android 14) |
| 검증 화면 | 2560×1440 (`-skin 2560x1440`), 2코어/2GB RAM, SwiftShader |

- 앱 화면은 **2560×1440**에서 검증했습니다. 기본 320×640 화면에서는 앱 영역이 매우 좁게 나옵니다.
- 인터넷 권한을 쓰지 않습니다. 모든 기록 데이터는 앱 안의 assets로 들어 있습니다.
- 요청 권한: `pleos.car.permission.CAR_DOORS`(문 상태 읽기 전용), `pleos.car.permission.TTS_SERVICE`(한국어 음성).

## 2분 시연 순서

1. 대시보드: 기록 재생·더미 라벨, GPS 서킷 지도(영암 10턴), 기준 4랩 비교를 설명합니다.
2. `9랩 기록 / 세션 요약` → `시연 시작 · 자막`: 자막 코칭으로 기록을 재생합니다. 동적 음성은 기본으로 꺼져 있습니다.
3. 재생이 7랩에 도달하거나 `다음 랩`으로 7랩까지 이동한 뒤 `랩 비교 보기`를 누릅니다. **7랩 1:40.4**, 이전 베스트 4랩 대비 전체 −0.6초(S1 −0.4 / S2 +0.7 / S3 −0.9초)가 나옵니다.
4. `세션 요약 보기`: 전체 9랩 보존, 베스트 7랩, 9랩 S3 137.7초는 원인 미확정으로 표시합니다.
5. (선택) `동적 음성 선택 · 기본 꺼짐`을 켜면 현재 랩의 한국어 코칭을 합성합니다. 실제 청취는 현장에서 따로 확인합니다.

재생 속도 10초/랩은 검토용이며 실제 랩 경과 시간이 아닙니다.

## 화면 데이터의 의미

| 항목 | 근거 | 제한 |
|---|---|---|
| GPS 위치·속도 | 사용자 주행 기록 재생 (베스트 7랩 GPS 487개 지점 · 랩·섹터 시간은 9랩) | 현재 실차 위치·속도 아님 |
| 악셀 | 기록의 `accelerator_percent`와 유효 플래그 | 물리 페달/TPS 독립 교정 미확인 |
| 조향 | 사용자 기록 | 차량별 교정·실차 수신 미검증 |
| G 표시 | GPS 기반 추정 | IMU 가속도계 아님 |
| 브레이크 | 시연용 더미 | 실제 제동 신호 없음, 제동 지점 근거로 쓰지 않음 |
| 휠별 psi·°C | 시연용 TPMS 더미 | 실제 센서 수신 아님 |
| 코너 위치 | GPS 추정 매핑 (영암 10턴) | 공식 측량·노면 투영 정합 미검증 |
| 섹터 색 | 기준 랩 대비 시간 차 | 연두 빠름 / 노랑 느림 / 중립 동률·미확인 |
| 빨간 강조 | 기록 비교 검토 후보 | 고장·원인 진단이나 차량 경고 아님 |

화면 왼쪽 차량 패널은 Pleos 에뮬레이터 기본 UI이며 사용자 실차 사진이 아닙니다.

## 검증 결과

- **0.2.7 서명 release (2026-11-07):** 단위시험 45/45 통과, lint 오류 0 / 경고 18. 서명 검증(v2) 통과. Pleos 에뮬레이터에서 설치·실행, 디버깅 꺼짐, 진단 버튼 숨김, 내부 진단 화면 외부 실행 차단, 시연 1/3 → 2/3 → 3/3 흐름 통과. 이 시험에서 음성 발화 요청은 0회입니다.
- **0.2.4 동적 한국어 코칭:** 깨끗한 Pleos AVD에서 ON_DEVICE 합성 요청/시작/완료 1/1/1과 엔진 출력을 확인했습니다. 물리적 청취는 독립 확인하지 않았습니다.
- 0.2.5: 런처 아이콘, Android 12+ 데이터 추출 제외 규칙 추가(`allowBackup=false` 유지).
- 0.2.6: 진단용 음성 버튼과 내부 진단 Activity를 debug 빌드에서만 사용하도록 제한.
- 0.2.7: 앱 표시명을 "플레오스 AI 레이스 엔지니어"로 변경, release 서명 설정 추가. 코칭·신호·음성 로직은 0.2.6과 같습니다.

## 아직 하지 않은 것

물리적 음성 청취 확인, 실차 신호·인캠 연결, 원본 영상과 기록의 전체 구간 시간·공간 정합, 실제 브레이크 입력, 노면 AR과 실차 실시간 안내.
