# FTP Synchronization

[中文](../README.md) | [English](README.en.md) | [日本語](README.ja.md) | [한국어](README.ko.md)

FTP Synchronization은 로컬 디렉터리와 FTP 디렉터리 사이에서 파일을 예약 업로드 또는 다운로드하는 JavaFX 데스크톱 앱입니다. 시간 기반 동적 경로를 지원하여 데이터, 이미지, 메시지 및 장비 파일 동기화에 사용할 수 있습니다.

## 주요 기능

- 여러 업로드/다운로드 규칙을 추가, 보기, 편집, 삭제 및 활성화합니다.
- 년, 월, 일, 시, 분, 초 자리 표시자와 분 단위 시간 오프셋.
- 대상에 없는 파일만 동기화하여 중복 전송을 방지합니다.
- 규칙별 또는 전역 최근 파일 수, 안정 시간, FTP 시간 제한 및 연결 풀.
- FTP 연결 테스트, 시스템 기록 및 파일별 전송 기록.
- 시스템 언어, 중국어, 영어, 일본어, 한국어 즉시 전환.
- Windows, Linux, macOS x64/ARM64 패키지 자동 생성.

## 다운로드 및 실행

GitHub Actions Artifacts 또는 Releases에서 운영체제와 CPU에 맞는 패키지를 받으세요.

| OS | x64 | ARM64 |
|---|---|---|
| Windows | `ftp-synchronization-<version>-windows-x64.zip` | `ftp-synchronization-<version>-windows-arm64.zip` |
| Linux | `ftp-synchronization-<version>-linux-x64.tar.gz` | `ftp-synchronization-<version>-linux-arm64.tar.gz` |
| macOS | `ftp-synchronization-<version>-macos-x64.tar.gz` | `ftp-synchronization-<version>-macos-arm64.tar.gz` |

Java를 별도로 설치할 필요가 없습니다. 각 패키지의 `jre/`에 JavaFX가 포함된 Liberica Java 21 JRE가 있습니다. 압축을 푼 뒤 `run.bat`, `run.sh` 또는 `run.command`를 실행하세요.

`ftp-synchronization.jar`에는 앱과 JavaFX 이외의 실행 의존성만 포함됩니다. JavaFX 클래스, 모듈 및 네이티브 라이브러리는 JAR에 포함되지 않습니다.

## 빠른 시작

1. “FTP 및 실행 설정”을 엽니다.
2. FTP 주소, 포트, 사용자 이름 및 비밀번호를 입력하고 연결을 테스트합니다.
3. 설정을 저장합니다.
4. 업로드 또는 다운로드 규칙을 추가합니다.
5. 디렉터리 미리 보기를 확인하고 저장합니다.
6. “지금 실행”으로 첫 동작을 확인합니다.

업로드는 최근 N개의 안정된 로컬 파일을, 다운로드는 최근 N개의 FTP 파일을 비교합니다. 대상에 같은 이름의 파일이 있으면 건너뜁니다.

## 규칙과 자리 표시자

규칙에는 이름, 방향, 로컬 루트, 원격 루트, 동적 경로, 분 오프셋 및 선택적인 최근 파일 수가 있습니다.

| 시간 | 0 없음 | 0 채움 |
|---|---|---|
| 년 | `{year}` | `{YEAR}` |
| 월 | `{month}` | `{MONTH}` |
| 일 | `{day}` | `{DAY}` |
| 시 | `{hh}` | `{HH}` |
| 분 | `{mm}` | `{MM}` |
| 초 | `{ss}` | `{SS}` |

`2026-08-01 03:05:09`에서 `{year}/{MONTH}/{DAY}/{HH}/{MM}/{SS}`는 `2026/08/01/03/05/09`가 됩니다. 이전 규칙의 호환 동작은 유지됩니다.

## 언어 및 설정 파일

설정 화면에서 System, 中文, English, 日本語, 한국어를 선택할 수 있습니다. 변경 시 스케줄러와 FTP 풀을 중단하지 않고 화면만 다시 불러옵니다.

- `pathRules.json`: 이전 버전과 호환되는 규칙 파일.
- `ftp-synchronization-settings.properties`: FTP, 일정 및 언어 설정.
- `application.properties` / `application.yml`: 외부 설정으로 계속 지원됩니다.

새 설정 파일이 없으면 기존 `ftp-upload-settings.properties`를 읽어 새 이름으로 복사합니다. 기존 파일은 삭제하지 않습니다.

## 빌드 및 테스트

Java 21에서 `./mvnw clean verify`를 실행합니다. `target/ftp-synchronization.jar`가 생성되고 설정 마이그레이션, 4개 언어, FXML, JSON 호환성, 템플릿, 리스너, ProGuard 및 JAR 내 JavaFX 부재를 테스트합니다. CI는 6개 배포 패키지도 검증합니다.

워크플로 유지 방법은 [GitHub Actions 패키징 안내](GITHUB_ACTIONS_PACKAGING.zh-CN.md)를 참고하세요.

## 보안

FTP 비밀번호는 로컬 설정 파일에 일반 텍스트로 저장됩니다. 파일 접근을 제한하고 스크린샷, 로그, 커밋 또는 이슈에 비밀번호를 포함하지 마세요. 제공된 SHA-256 파일로 다운로드 무결성을 확인할 수 있습니다.
