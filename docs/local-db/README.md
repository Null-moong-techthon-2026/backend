# 로컬 DB 직접 확인 (선택 사항)

백엔드 담당자가 DB 내부를 확인할 때 사용하는 안내입니다. 팀원의 서버 실행·Swagger 테스트에는 DBeaver가 필요하지 않습니다.
DB와 서버 실행 방법은 [프로젝트 README](../../README.md)를 참고합니다.

## DBeaver 연결

| 항목 | 기본값 |
| --- | --- |
| Driver | PostgreSQL |
| Host / Port | `127.0.0.1` / `5433` |
| Database / Schema | `boothrock` / `app` |
| Username / Password | `boothrock_local` / `1234` |

위 자격증명은 loopback에만 노출하는 로컬 개발 DB의 공개 테스트 값입니다.
Docker 내부 PostgreSQL 포트는 5432이며 외부에서는 5433으로 연결합니다.

서버가 처음 시작되면 Flyway V1/V2가 `app` 스키마와 테이블을 자동 생성합니다.
`Schemas → app → Tables`에서 `accounts`, `local_credentials`, `organizations`, `organization_memberships`, `flyway_schema_history`를 확인합니다.
SQL로 스키마를 다시 생성하거나 이미 적용된 migration 파일을 수정하지 않습니다.

## 저장된 데이터 확인

- [계정·조직 조회 SQL](check-auth-data.sql)
- [계정·조직 전체 삭제 SQL](clear-auth-data.sql)

두 가입 유형 모두 회원가입 시 `accounts`와 `local_credentials`에만 저장됩니다. 조직·소속·관리 권한은 생성하지 않습니다.
계정 `id`는 UUID이고 로그인 아이디는 원문, 비밀번호는 `{bcrypt}...` 해시로 저장됩니다.
직접 비밀번호 원문을 삽입하지 말고 회원가입 API를 사용합니다.
로그인·로그아웃은 서버 메모리의 세션을 변경하므로 DB 행 수는 바뀌지 않습니다.

삭제 SQL은 **모든 계정·로그인 정보·조직·소속을 삭제**합니다. 테이블과 Flyway 이력은 유지합니다.
삭제 후 기존 로그인 세션은 로그아웃하거나 서버를 재시작하고, 다시 가입·로그인합니다.

## DBeaver 결과가 바뀌지 않을 때

- Swagger에서 `GET /api/dev/auth-data`를 실행해 현재 DB 이름과 행 수를 확인합니다.
- SQL 편집기의 연결이 `127.0.0.1:5433`, DB `boothrock`인지 확인하고 조회 SQL을 다시 실행합니다. Docker 내부에서 반환되는 포트 `5432`는 정상입니다.
- 이미 열린 데이터 탭은 이전 조회 결과를 유지하므로 새로고침해야 합니다. 테이블 목록 새로고침과 데이터 탭의 행 새로고침은 다릅니다.
- 회원가입 201 또는 삭제 204 이후 확인합니다. 로그인·로그아웃만으로는 데이터가 늘거나 줄지 않습니다.
- API 개수와 SQL 개수가 다르면 연결 대상과 조회 필터를 확인합니다. REPEATABLE READ/SERIALIZABLE의 오래 열린 트랜잭션이라면 미저장 편집을 확인하고 트랜잭션을 종료한 뒤 재조회합니다.
