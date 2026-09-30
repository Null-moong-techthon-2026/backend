# 부스럭 데이터베이스 검토용

현재 데이터베이스 설계를 팀원이 로컬에서 실행하고 DBeaver로 확인하기 위한 저장소입니다. 서버 애플리케이션이나 서비스 기능은 포함하지 않습니다.

## 현재 상태

| 구분 | 상태 |
| --- | --- |
| 앱 DB (`app` 스키마) | Flyway가 스키마만 준비합니다. 업무 테이블과 API 연동은 아직 없습니다. |
| ERD 검토 DB (`erd` 스키마) | 아래 SQL의 설계 초안을 별도 PostgreSQL에 생성합니다. 테스트용 데이터는 넣지 않습니다. |

`erd-lab/schema.sql`은 설계 검토용 초안이며 앱의 Flyway migration이 아닙니다. 테이블 제약은 정의되어 있지만 인증·권한 검사, 상태 변경 규칙 등은 서버에서 구현되지 않았습니다.

## 구성

```text
.
├── README.md
├── .gitignore
└── erd-lab/
    ├── compose.yaml
    ├── .env.example
    └── schema.sql
```

## ERD 검토 DB 실행

요구 사항: Docker Desktop 또는 Docker Engine, Docker Compose v2

저장소 루트에서 실행합니다.

```bash
cd erd-lab
cp -n .env.example .env
```

`.env`의 `ERD_DB_PASSWORD`를 로컬 전용 값으로 바꾼 다음 컨테이너를 시작합니다.

```bash
docker compose up -d --wait
docker compose ps
```

초기화가 완료되면 PostgreSQL 17이 `127.0.0.1:5434`에 열립니다. Compose 설정은 로컬 컴퓨터에서만 접속할 수 있도록 포트를 바인딩합니다.

## DBeaver 연결

| 항목 | 값 |
| --- | --- |
| Host | `localhost` |
| Port | `5434` |
| Database | `boothrock_erd` |
| Username | `erd_local` |
| Password | `erd-lab/.env`의 `ERD_DB_PASSWORD` |
| Schema | `erd` |

DBeaver에서 `erd` 스키마를 선택하고 새로고침하면 테이블과 외래 키 관계를 확인할 수 있습니다. 주요 테이블은 다음과 같이 나뉩니다.

- 계정·조직·행사: `accounts`, `organizations`, `organization_memberships`, `events`, `event_memberships`
- 부스 신청·운영: `booth_profiles`, `booth_applications`, `event_booths`, `booth_memberships`, `booth_items`
- 지도·공지·감사 기록: `media_assets`, `floor_plans`, `map_pins`, `announcements`, `announcement_audiences`, `audit_logs`

지도 핀의 `x_ratio`, `y_ratio`는 지도 이미지의 가로·세로 비율 좌표(0~1)입니다. 이미지 자체는 저장하지 않고, `media_assets`가 파일 저장소의 객체 정보를 보관하도록 설계되어 있습니다.

## 데이터 초기화와 보존

`schema.sql`은 새 Docker volume을 처음 만들 때만 자동 적용됩니다. SQL을 수정해도 이미 생성된 DB에는 자동 반영되지 않습니다. 컨테이너를 내려도 named volume은 남습니다.

```bash
docker compose down
```

검토 DB 데이터를 모두 지우고 처음부터 만들 때만 아래 명령을 실행하세요.

```bash
docker compose down -v
docker compose up -d --wait
```

`down -v`는 이 ERD 검토용 DB의 volume 데이터를 삭제합니다. 실제 앱 DB와는 별도입니다.

## 공개 범위와 보안

- 개인용 `.env`, 비밀번호, 실제 데이터는 커밋하지 않습니다.
- `.env.example`에는 로컬에서 교체할 예시 값만 둡니다.
- 이 DB에는 샘플 계정이나 행사 데이터가 없습니다.
