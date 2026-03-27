# Page Builder

AI 채팅으로 HTML 페이지를 생성하고 관리하는 웹 서비스입니다.
원하는 화면을 텍스트로 설명하거나 이미지·문서를 첨부하면 AI가 실시간으로 HTML을 생성해 줍니다.

---

## 주요 기능

### AI 채팅 기반 HTML 생성
- 원하는 페이지를 자연어로 설명하면 AI가 완성된 HTML을 생성합니다.
- SSE(Server-Sent Events) 방식으로 토큰이 생성되는 즉시 실시간 스트리밍합니다.
- 생성 중 경과 시간, 토큰 수, 실시간 코드 미리보기를 화면에 표시합니다.
- 생성 완료 후 사용 모델명, 처리 시간, 토큰 수를 요약 카드로 표시합니다.

### 파일 첨부 및 AI 참고
- **이미지 (PNG/JPG/GIF/WEBP)**: 첨부 시 llava 비전 모델로 자동 전환, 화면 레이아웃을 그대로 재현
- **PowerPoint (PPT/PPTX)**: 텍스트 추출 후 AI 참고 자료로 활용
- **Excel (XLS/XLSX)**: 표 데이터 추출 후 AI 참고 자료로 활용
- 이전에 업로드한 파일을 드롭업 메뉴에서 재사용 가능

### 페이지 관리
- 생성한 페이지를 저장하면 고유 UUID 공개 URL이 발급됩니다 (`/page/{uuid}`)
- 대시보드에서 내 페이지 목록을 iframe 미리보기 카드로 확인
- 기존 페이지에 채팅으로 수정 요청을 계속할 수 있습니다.

### 버전 히스토리
- AI가 생성한 모든 버전이 자동으로 저장됩니다.
- 버전 목록에서 각 버전을 클릭하면 미리보기를 볼 수 있습니다.
- 원하는 버전을 선택해 게시 버전으로 설정하거나 에디터에 적용할 수 있습니다.

### 이전/새 버전 비교
- 페이지 수정 시 이전 버전과 새 버전을 좌우 분할 화면으로 비교합니다.
- "되돌리기" 또는 "유지" 버튼으로 선택합니다.

### 채팅 히스토리 복원
- 모든 대화 내역이 DB에 저장됩니다.
- 페이지 편집 화면 재진입 시 이전 대화 내역을 자동으로 불러옵니다.
- 새 페이지 작업 중 새로고침해도 URL 쿼리 파라미터(`?s=UUID`)로 세션이 복원됩니다.

### HTML 코드 출력
- 생성된 HTML 소스 코드를 모달에서 확인합니다.
- 클립보드 복사 및 파일 다운로드를 지원합니다.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Spring Boot 2.7.18, Java 17 |
| Security | Spring Security 5 (BCrypt, 세션 기반, 세션 유지 8시간) |
| DB | H2 Embedded (파일 모드), JPA / Hibernate |
| AI | Ollama REST API (codellama, llava) |
| Template | Thymeleaf + Bootstrap 5 + Font Awesome 6 |
| 문서 파싱 | Apache POI (Excel, PowerPoint) |
| 스트리밍 | SSE (SseEmitter, 타임아웃 없음) |

---

## 실행 환경

- **Java 17** 필수 (Eclipse Adoptium Temurin 권장)
- **Maven 3.x**
- **Ollama** 설치 및 실행 필요

### Ollama 모델 설치

```bash
ollama pull codellama      # 기본 HTML 생성 모델
ollama pull llava          # 이미지 분석용 비전 모델 (선택)
```

---

## 실행 방법

### Windows (PowerShell)

```powershell
cd page-builder
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.8.101-hotspot"
mvn spring-boot:run
```

### 일반

```bash
cd page-builder
mvn spring-boot:run
```

서버 기동 후 → [http://localhost:9998](http://localhost:9998)

---

## 설정 (application.yml)

```yaml
server:
  port: 9998
  servlet:
    session:
      timeout: 8h

ollama:
  base-url: http://localhost:11434
  model: codellama
  vision-model: llava

spring:
  datasource:
    url: jdbc:h2:file:./data/pagebuilder;AUTO_SERVER=TRUE
  jpa:
    hibernate:
      ddl-auto: update
```

> H2 DB 파일은 `./data/pagebuilder.*`에 생성됩니다. `.gitignore`에 포함되어 있습니다.

---

## 주요 URL

| URL | 설명 |
|-----|------|
| `/register` | 회원가입 |
| `/login` | 로그인 |
| `/dashboard` | 내 페이지 목록 |
| `/editor` | 새 페이지 생성 |
| `/editor/{id}` | 기존 페이지 수정 |
| `/page/{uuid}` | 공개 URL (로그인 불필요) |
| `/docs` | 프로젝트 문서 |
| `/h2-console` | H2 DB 콘솔 (개발용) |

### REST API

| Method | URL | 설명 |
|--------|-----|------|
| `POST` | `/api/chat/stream` | SSE 스트리밍 HTML 생성 |
| `GET` | `/api/pages` | 내 페이지 목록 |
| `POST` | `/api/pages` | 페이지 저장 |
| `PUT` | `/api/pages/{id}` | 제목/설명 수정 |
| `DELETE` | `/api/pages/{id}` | 페이지 삭제 |
| `GET` | `/api/pages/{id}/history` | 채팅 히스토리 조회 |
| `GET` | `/api/pages/{id}/versions` | 버전 목록 조회 |
| `GET` | `/api/pages/{id}/versions/{msgId}` | 특정 버전 HTML 조회 |
| `POST` | `/api/pages/{id}/versions/{msgId}/publish` | 버전 게시 |
| `POST` | `/api/files/upload` | 파일 업로드 |
| `GET` | `/api/files` | 내 파일 목록 |

---

## 프로젝트 구조

```
page-builder/
├── src/main/java/com/example/pagebuilder/
│   ├── config/
│   │   ├── PasswordEncoderConfig.java   # BCrypt 빈 분리 (순환참조 방지)
│   │   └── SecurityConfig.java          # Spring Security 설정
│   ├── controller/
│   │   ├── AuthController.java          # 회원가입/로그인
│   │   ├── ChatApiController.java       # SSE 스트리밍 엔드포인트
│   │   ├── FileApiController.java       # 파일 업로드/조회
│   │   ├── PageApiController.java       # 페이지 CRUD + 버전 관리
│   │   └── ViewController.java          # Thymeleaf 페이지 라우팅
│   ├── dto/
│   │   ├── ChatRequest.java
│   │   ├── ChatResponse.java
│   │   ├── FileDto.java
│   │   └── PageDto.java
│   ├── entity/
│   │   ├── ChatMessage.java             # 채팅 메시지 (모델명/시간/토큰 메타데이터 포함)
│   │   ├── HtmlPage.java               # 생성된 페이지
│   │   ├── Member.java                 # 회원
│   │   └── UploadedFile.java           # 업로드 파일
│   ├── repository/                      # JPA 레포지토리
│   └── service/
│       ├── FileParseService.java        # PPT/Excel/이미지 파싱
│       ├── MemberService.java
│       ├── OllamaService.java           # Ollama API 통신 (동기/스트리밍)
│       └── PageService.java             # 페이지/채팅/버전 비즈니스 로직
└── src/main/resources/
    ├── application.yml
    ├── static/css/style.css
    └── templates/
        ├── dashboard.html
        ├── editor.html                  # 메인 에디터 화면
        ├── login.html
        ├── register.html
        ├── page-view.html               # 공개 URL 페이지
        └── docs.html
```

---

## 개발 과정에서 해결한 주요 문제

| 문제 | 원인 | 해결 |
|------|------|------|
| 채팅 히스토리 저장 안 됨 | `PageDto.setSessionId()`가 빈 메서드라 sessionId가 null로 전달됨 | setter 본문 추가 |
| 생성 중 타임아웃 → 저장 안 됨 | `readTimeout = 180s`로 10분+ 생성 시 연결 강제 종료 | `readTimeout = 0` (무제한)으로 변경 |
| 새로고침 시 히스토리 사라짐 | sessionStorage는 탭 닫으면 삭제됨 | URL 쿼리 파라미터 `?s=UUID` 방식으로 교체 |
| 순환 참조 (SecurityConfig ↔ MemberService) | 같은 빈에서 상호 참조 | `PasswordEncoderConfig` 분리 |
| 한글 쿠키로 RequestRejectedException | StrictHttpFirewall의 헤더값 검증 | `setAllowedHeaderValues` 완화 |
| 로딩 화면만 보이고 생성 안 됨 | JS에서 존재하지 않는 `loadingSubtext` DOM 요소 참조 → TypeError로 fetch 미실행 | HTML에 해당 요소 추가 |

---

## 성능 관련 참고사항

현재 로컬 CPU로 Ollama를 실행하므로 HTML 생성에 수 분이 걸릴 수 있습니다.

**속도 개선 방향:**

- **GPU 활용**: NVIDIA GPU가 있으면 Ollama가 자동으로 CUDA를 사용해 10배+ 빠름
- **모델 교체**: `ollama pull mistral` 또는 `ollama pull llama3.2` — codellama보다 빠르고 지시 이행 능력 향상
- **히스토리 제한**: 긴 대화 내역은 최근 2~3턴만 전달하도록 수정하면 컨텍스트 크기 감소

---

## 스크린샷

### AI 생성 중 화면
실시간 스트리밍 — 경과 시간, 생성 토큰 수, 코드 미리보기를 동시에 표시합니다.

![AI 생성 중 화면](https://github.com/user-attachments/assets/9ca6d5e8-8852-43d0-bef4-52e93e3646cd)

---

## 라이선스

MIT
