package kr.boothrock.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

@Configuration
public class OpenApiConfig {
    private static final Map<String, String> RESPONSE_DESCRIPTIONS = Map.of(
            "OK", "요청 성공", "Created", "생성 완료", "No Content", "처리 완료(응답 본문 없음)");

    private static final Map<String, String> FIELD_DESCRIPTIONS = Map.ofEntries(
            Map.entry("id", "항목의 고유 식별자(UUID)"),
            Map.entry("accountId", "회원 계정의 고유 ID"),
            Map.entry("onboardingType", "가입 후 사용할 화면 유형입니다. ORGANIZER: 주최자, OPERATOR: 부스 운영자. 조직 소속이나 관리 권한을 부여하지 않습니다."),
            Map.entry("loginId", "로그인 아이디입니다. 영문 소문자·숫자·밑줄로 구성한 4~30자입니다."),
            Map.entry("password", "로그인 비밀번호입니다. 8~72자로 입력합니다."),
            Map.entry("nickname", "화면에 표시할 닉네임"),
            Map.entry("phoneNumber", "연락처입니다. 숫자 8~15자이며 앞에 +를 붙일 수 있습니다."),
            Map.entry("email", "이메일 주소"),
            Map.entry("available", "해당 로그인 아이디를 사용할 수 있는지 여부"),
            Map.entry("token", "변경 요청의 X-CSRF-TOKEN 헤더에 넣을 CSRF 토큰"),
            Map.entry("headerName", "CSRF 토큰을 전달할 요청 헤더 이름"),
            Map.entry("parameterName", "CSRF 토큰의 요청 매개변수 이름"),
            Map.entry("revision", "직전 조회에서 받은 수정 버전입니다. 동시 수정 충돌을 확인하므로 현재 값을 그대로 보냅니다."),
            Map.entry("name", "이름"),
            Map.entry("description", "상세 설명"),
            Map.entry("introduction", "소개글"),
            Map.entry("categoryCode", "부스 카테고리 코드입니다. FOOD: 음식, BEVERAGE: 음료, EXPERIENCE: 체험, GAME: 게임, GOODS: 굿즈, OTHER: 기타"),
            Map.entry("boothProfileId", "참여 신청에 사용할 내 부스 프로필 ID"),
            Map.entry("applicantName", "참여 신청자 이름"),
            Map.entry("applicantPhone", "참여 신청자 연락처"),
            Map.entry("applicantEmail", "참여 신청자 이메일 주소"),
            Map.entry("invitationCode", "초대가 필요한 행사에서 발급받은 초대 코드 원문"),
            Map.entry("reviewNote", "관리자가 작성하는 내부 검토 메모"),
            Map.entry("checkStatus", "서류 확인 상태입니다. NOT_SUBMITTED: 미제출, SUBMITTED: 제출, VERIFIED: 확인 완료, NEEDS_CORRECTION: 보완 필요"),
            Map.entry("decision", "심사 결과입니다. APPROVED: 승인, REJECTED: 반려"),
            Map.entry("boothCode", "행사 안에서 사용하는 부스 번호"),
            Map.entry("rejectionReason", "신청 반려 사유"),
            Map.entry("visibilityStatus", "공개 상태입니다. HIDDEN: 숨김, PUBLIC: 공개, ARCHIVED: 보관"),
            Map.entry("operationStatus", "운영 상태입니다. PREPARING: 준비 중, OPEN: 운영 중, SOLD_OUT: 품절, CLOSED: 마감"),
            Map.entry("priceKrw", "원 단위 가격"),
            Map.entry("stockStatus", "재고 상태입니다. UNLIMITED: 무제한, AVAILABLE: 충분, LOW: 부족, SOLD_OUT: 품절"),
            Map.entry("sortOrder", "화면에 표시할 순서입니다. 작은 숫자부터 표시합니다."),
            Map.entry("venue", "행사 장소"),
            Map.entry("startsAt", "행사 시작 일시(ISO 8601 형식)"),
            Map.entry("endsAt", "행사 종료 일시(ISO 8601 형식)"),
            Map.entry("posterAssetId", "행사 대표 이미지로 사용할 업로드 파일 ID"),
            Map.entry("publicationStatus", "게시 상태 코드입니다. 요청에 표시된 허용 값을 사용합니다."),
            Map.entry("isRequired", "필수 제출 서류인지 여부"),
            Map.entry("applicableCategoryCodes", "해당 서류를 제출할 부스 카테고리 코드 목록"),
            Map.entry("closesAt", "부스 모집 마감 일시(ISO 8601 형식)"),
            Map.entry("targetBoothCount", "모집할 부스 수"),
            Map.entry("allowedCategoryCodes", "참여 신청을 허용할 부스 카테고리 코드 목록"),
            Map.entry("participationFeeKrw", "원 단위 부스 참가비"),
            Map.entry("feeNote", "참가비 납부 방법과 안내 문구"),
            Map.entry("documentRequirements", "필요 서류와 제출 대상 카테고리 목록"),
            Map.entry("expiresAt", "초대 코드 만료 일시(ISO 8601 형식)"),
            Map.entry("code", "발급받은 초대 코드 원문"),
            Map.entry("mediaAssetId", "지도 배경으로 사용할 업로드 이미지 ID"),
            Map.entry("sourceFloorPlanId", "편집 초안으로 복제할 같은 행사의 공개 지도 ID"),
            Map.entry("pinType", "핀 종류입니다. BOOTH: 부스, TOILET: 화장실, INFO: 안내소, MEDICAL: 의무실, OTHER_FACILITY: 기타 시설"),
            Map.entry("label", "지도에 표시할 핀 이름 또는 부스 번호"),
            Map.entry("xRatio", "이미지 왼쪽을 0, 오른쪽을 1로 계산한 가로 상대 좌표"),
            Map.entry("yRatio", "이미지 위쪽을 0, 아래쪽을 1로 계산한 세로 상대 좌표"),
            Map.entry("eventBoothId", "핀에 할당할 승인·직접 등록 부스 ID"),
            Map.entry("service", "백엔드 서비스 식별 이름"),
            Map.entry("stage", "서버 상태 응답의 고정 식별값"));

    private static final Map<String, String> PARAMETER_DESCRIPTIONS = Map.ofEntries(
            Map.entry("X-CSRF-TOKEN", "GET /api/auth/csrf 응답의 token을 입력합니다. 로그인 후에는 다시 조회합니다."),
            Map.entry("eventId", "행사 ID"), Map.entry("organizationId", "조직 ID"),
            Map.entry("announcementId", "공지 ID"), Map.entry("applicationId", "부스 신청 ID"),
            Map.entry("assetId", "업로드한 이미지 파일 ID"), Map.entry("boothId", "행사에 등록된 부스 ID"),
            Map.entry("floorPlanId", "지도 ID"), Map.entry("itemId", "메뉴·상품 ID"),
            Map.entry("profileId", "내 부스 프로필 ID"), Map.entry("requirementId", "필요 서류 항목 ID"),
            Map.entry("page", "페이지 번호입니다. 0부터 시작합니다."),
            Map.entry("size", "한 페이지에 조회할 개수입니다. API별 기본값을 확인합니다."),
            Map.entry("q", "검색어"), Map.entry("sort", "정렬 기준과 방향입니다. API별 기본값을 확인합니다."),
            Map.entry("reviewStatus", "신청 심사 상태입니다. PENDING: 검토 중, APPROVED: 승인, REJECTED: 반려"),
            Map.entry("stockSummary", "부스 재고 요약 상태입니다. NOT_TRACKED: 관리 안 함, SOLD_OUT: 품절, UNLIMITED: 무제한, LOW: 부족, AVAILABLE: 충분"),
            Map.entry("placementStatus", "지도 배치 상태입니다. ASSIGNED: 배치 완료, UNASSIGNED: 미배치"),
            Map.entry("source", "부스 등록 경로입니다. APPLICATION: 신청 승인, MANUAL: 주최자 직접 등록"),
            Map.entry("participation", "행사 참여 유형입니다. ORGANIZER: 주최자, OPERATOR: 부스 운영자"),
            Map.entry("recruitmentStatus", "모집 상태입니다. OPEN: 모집 중, CLOSED: 모집 종료"),
            Map.entry("view", "공지 조회 방식입니다. READ: 읽기, MANAGE: 관리자 편집용 조회"),
            Map.entry("audience", "공지 대상 필터입니다. STAFF: 스태프, OPERATORS: 부스 운영자, PUBLIC: 일반 방문객"));

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("부스럭 API")
                .version("0.0.1")
                .description("행사 운영 서비스 부스럭의 API입니다. 인증, 행사·모집, 부스 신청·심사, 운영 상태·메뉴, 지도·이미지, 공지 기능을 제공합니다. 업무 API는 local·staging에서 사용하며, 데이터 초기화 등 개발 도구는 local 전용입니다."));
    }

    @Bean
    OperationCustomizer csrfHeaderForChanges() {
        return (operation, handler) -> {
            var method = handler.getMethod();
            boolean changesData = method.isAnnotationPresent(PostMapping.class)
                    || method.isAnnotationPresent(PutMapping.class)
                    || method.isAnnotationPresent(PatchMapping.class)
                    || method.isAnnotationPresent(DeleteMapping.class);
            if (!changesData || !handler.getBeanType().getPackageName().startsWith("kr.boothrock.api")) {
                return operation;
            }
            boolean documented = operation.getParameters() != null && operation.getParameters().stream()
                    .anyMatch(parameter -> "header".equals(parameter.getIn())
                            && "X-CSRF-TOKEN".equalsIgnoreCase(parameter.getName()));
            if (!documented) {
                operation.addParametersItem(new Parameter().name("X-CSRF-TOKEN").in("header")
                        .required(true).description("GET /api/auth/csrf 응답의 token을 입력합니다. 로그인 후에는 토큰을 다시 조회합니다."));
            }
            return operation;
        };
    }

    @Bean
    OpenApiCustomizer koreanDocumentation() {
        return openApi -> {
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                    if (operation.getParameters() != null) {
                        operation.getParameters().forEach(parameter -> {
                            if (parameter.getDescription() == null || parameter.getDescription().isBlank()) {
                                parameter.setDescription(PARAMETER_DESCRIPTIONS.getOrDefault(
                                        parameter.getName(), FIELD_DESCRIPTIONS.get(parameter.getName())));
                            }
                        });
                    }
                    if (operation.getResponses() != null) {
                        operation.getResponses().values().forEach(response -> {
                            String description = response.getDescription() == null ? null
                                    : RESPONSE_DESCRIPTIONS.get(response.getDescription());
                            if (description != null) response.setDescription(description);
                        });
                    }
                }));
            }
            if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
                openApi.getComponents().getSchemas().values().forEach(OpenApiConfig::describeProperties);
            }
            if (openApi.getServers() != null) {
                openApi.getServers().forEach(server -> {
                    if ("Generated server url".equals(server.getDescription())) server.setDescription("현재 API 서버");
                });
            }
        };
    }

    private static void describeProperties(Schema<?> schema) {
        if (schema.getProperties() == null) return;
        schema.getProperties().forEach((name, property) -> {
            if (property.getDescription() == null || property.getDescription().isBlank()) {
                property.setDescription(FIELD_DESCRIPTIONS.get(name));
            }
        });
    }
}
