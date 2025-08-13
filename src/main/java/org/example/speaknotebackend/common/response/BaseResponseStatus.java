package org.example.speaknotebackend.common.response;


import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 에러 코드 관리
 */
@Getter
public enum BaseResponseStatus {
    /**
     * 200 : 요청 성공
     */
    SUCCESS(true, HttpStatus.OK.value(), "요청에 성공하였습니다."),

    /**
     * 400 : Request, Response 오류
     */
    OTHER_SERVER_ERROR(false, HttpStatus.BAD_REQUEST.value(), "외부 서버 오류입니다."),
    INVALID_FIELD_VALUE(false, HttpStatus.BAD_REQUEST.value(), "필드 값이 올바르지 않습니다."),
    INVALID_PARAM(false, HttpStatus.BAD_REQUEST.value(), "필수 파라미터가 누락되었습니다."),

    TEST_EMPTY_COMMENT(false, HttpStatus.BAD_REQUEST.value(), "코멘트를 입력해주세요."),
    POST_TEST_EXISTS_MEMO(false, HttpStatus.BAD_REQUEST.value(), "중복된 메모입니다."),

    RESPONSE_ERROR(false, HttpStatus.NOT_FOUND.value(), "값을 불러오는데 실패하였습니다."),
    DATE_VALIDATE_ERROR(false, HttpStatus.BAD_REQUEST.value(), "시작 날짜가 종료 날짜보다 이른 날짜입니다."),

    /**
     * Auth
     */
    DUPLICATED_EMAIL(false, HttpStatus.BAD_REQUEST.value(), "중복된 이메일입니다."),
    INVALID_PASSWORD(false, HttpStatus.BAD_REQUEST.value(), "설정할 수 없는 비밀번호 입니다."),
    INVALID_MEMO(false, HttpStatus.NOT_FOUND.value(), "존재하지 않는 메모입니다."),
    FAILED_TO_LOGIN(false, HttpStatus.NOT_FOUND.value(), "없는 아이디거나 비밀번호가 틀렸습니다."),
    INVALID_USER_JWT(false, HttpStatus.FORBIDDEN.value(), "권한이 없는 유저의 접근입니다."),
    INVALID_OAUTH_TYPE(false, HttpStatus.BAD_REQUEST.value(), "알 수 없는 소셜 로그인 형식입니다."),
    SLEPT_USER(false, HttpStatus.BAD_REQUEST.value(), "휴면계정입니다."),
    RECENT_WITHDRAW_USER(false, HttpStatus.BAD_REQUEST.value(), "탈퇴한지 90일이 지나지 않은 계정입니다."),
    PASSWORD_UPDATE_NEEDED(false, HttpStatus.OK.value(), "비밀번호를 변경한 지 90일이 경과되었습니다."),
    EMPTY_SOCIAL_INFO(false, HttpStatus.BAD_REQUEST.value(), "소셜 정보를 입력해주세요."),
    FAIL_TO_GET_APPLE_PRIVATE_KEY(false, HttpStatus.UNAUTHORIZED.value(), "애플 개인키를 가져오는데 실패하였습니다."),
    FAIL_TO_GET_APPLE_ID_TOKEN(
            false, HttpStatus.UNAUTHORIZED.value(), "애플 id token 가져오는데 실패하였습니다."),
    FAIL_TO_VERIFY_APPLE_ID_TOKEN(
            false, HttpStatus.UNAUTHORIZED.value(), "애플 id token을 검증하는데 실패하였습니다."),
    FAIL_TO_GET_APPLE_PUBLIC_KEY(false, HttpStatus.UNAUTHORIZED.value(), "애플 공개키를 가져오는 실패하였습니다."),
    FAIL_TO_GET_APPLE_EMAIL(false, HttpStatus.UNAUTHORIZED.value(), "애플 이메일 값이 없습니다."),
    TERMS_AGREEMENT_NEEDED(false, HttpStatus.BAD_REQUEST.value(), "필수약관 동의가 필요합니다."),
    DUPLICATED_PHONE_NUMBER(false, HttpStatus.BAD_REQUEST.value(), "중복된 전화번호입니다."),
    IS_WITHDRAWN_USER(false, BAD_REQUEST.value(), "탈퇴한 계정이에요 / 탈퇴일 기준 91일 후 재가입 가능해요"),
    SMS_NOT_AGREED(false, BAD_REQUEST.value(), "SMS 수신 거부한 유저입니다."),
    GENERAL_LOGIN_PASSWORD_ERROR(false, BAD_REQUEST.value(), "비밀번호를 입력해주세요."),
    SOCIAL_LOGIN_ERROR(false, BAD_REQUEST.value(), "소셜 로그인 오류입니다."),

    /**
     * Product
     */
    PRODUCT_NOT_FOUND_ERROR(false, HttpStatus.NOT_FOUND.value(), "상품을 찾을 수 없습니다."),
    PRODUCT_NOT_AVAILABLE(false, HttpStatus.BAD_REQUEST.value(), "상품이 판매 불가 상태입니다."),
    PRODUCT_NOT_SUBSCRIPTION_TYPE_ERROR(false, HttpStatus.BAD_REQUEST.value(), "구독형 상품이 아닙니다."),
    PRODUCT_NOT_PURCHASE_TYPE_ERROR(false, HttpStatus.BAD_REQUEST.value(), "구매형 상품이 아닙니다."),
    PRODUCT_STOCK_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "상품 재고 정보를 찾을 수 없습니다."),
    PRODUCT_SALES_TYPE_NOT_FOUND_ERROR(
            false, HttpStatus.BAD_REQUEST.value(), "상품 판매 타입을 찾을 수 없습니다."),
    PRODUCT_SUBSCRIPTION_OPTION_NOT_FOUND(
            false, HttpStatus.NOT_FOUND.value(), "구독 기간 옵션을 찾을 수 없습니다."),
    ADDITIONAL_PRODUCT_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "추가 상품 정보를 찾을 수 없습니다."),
    PRODUCT_ONE_STOCK_NOT_FOUND_ERROR(
            false, HttpStatus.NOT_FOUND.value(), "상품 단일 재고 정보를 찾을 수 없습니다."),
    ALREADY_EXISTS_PRODUCT_REVIEW_ERROR(false, HttpStatus.BAD_REQUEST.value(), "이미 리뷰를 작성했습니다."),
    PRODUCT_REVIEW_NOT_FOUND_ERROR(false, HttpStatus.NOT_FOUND.value(), "리뷰를 찾을 수 없습니다."),
    PRODUCT_REVIEW_USER_NOT_MATCHED_ERROR(
            false, HttpStatus.BAD_REQUEST.value(), "리뷰를 작성한 유저가 아닙니다."),
    PRODUCT_REVIEW_USER_CANT_REPORT_ERROR(
            false, HttpStatus.BAD_REQUEST.value(), "본인 리뷰를 신고할 수 없습니다."),
    PRODUCT_INQUIRY_NOT_FOUND_ERROR(false, HttpStatus.NOT_FOUND.value(), "문의 정보를 찾을 수 없습니다."),
    PRODUCT_INQUIRY_USER_NOT_MATCHED_ERROR(
            false, HttpStatus.BAD_REQUEST.value(), "문의를 작성한 유저가 아닙니다."),
    NOT_SECRET_PRODUCT_INQUIRY_ERROR(false, HttpStatus.BAD_REQUEST.value(), "해당 문의는 비공개 문의가 아닙니다."),
    ALREADY_USER_LIKE_PRODUCT_ERROR(false, HttpStatus.BAD_REQUEST.value(), "이미 해당 유저가 찜한 상품입니다."),
    PRODUCT_LIKE_NOT_FOUND_ERROR(false, HttpStatus.NOT_FOUND.value(), "찜하기 정보를 찾을 수 없습니다."),
    NOT_MATCH_PRODUCT_LIKE_USER_ERROR(
            false, HttpStatus.BAD_REQUEST.value(), "해당 유저의 찜하기 정보가 아닙니다."),
    PRODUCT_OPTION_DETAIL_NOT_FOUND_ERROR(
            false, HttpStatus.NOT_FOUND.value(), "상품 옵션 상세 정보를 찾을 수 없습니다."),
    LARGE_CATEGORY_NOT_FOUND_ERROR(false, HttpStatus.NOT_FOUND.value(), "대분류 정보를 찾을 수 없습니다."),
    MEDIUM_CATEGORY_NOT_FOUND_ERROR(false, HttpStatus.NOT_FOUND.value(), "중분류 정보를 찾을 수 없습니다."),
    SMALL_CATEGORY_NOT_FOUND_ERROR(false, HttpStatus.NOT_FOUND.value(), "소분류 정보를 찾을 수 없습니다."),
    DETAIL_CATEGORY_NOT_FOUND_ERROR(false, HttpStatus.NOT_FOUND.value(), "세분류 정보를 찾을 수 없습니다."),
    PRODUCT_REVIEW_REPORT_REASON_NOT_FOUND_ERROR(
            false, HttpStatus.NOT_FOUND.value(), "리뷰 신고 사유 정보를 찾을 수 없습니다."),
    PRODUCT_CANNOT_CHANGE_AS_NEW_PRODUCT(
            false, HttpStatus.BAD_REQUEST.value(), "새상품 구매 전환이 불가능한 상품입니다."),
    MINORS_CAN_NOT_ACCESS_PRODUCT_ERROR(false, BAD_REQUEST.value(), "미성년자는 접근이 불가능한 상품입니다."),
    NOT_LOGIN_USER_CAN_NOT_ACCESS_PRODUCT_ERROR(false, BAD_REQUEST.value(), "비회원은 조회가 불가능한 상품입니다."),
    STOCK_SEARCH_BAD_REQUEST(false, HttpStatus.NOT_FOUND.value(), "배송희망일 또는 회수희망일을 넣어주세요."),
    STOCK_DATE_BAD_REQUEST(false, HttpStatus.NOT_FOUND.value(), "해당 상품에 배송희망일 또는 회수희망일이 없습니다."),

    /**
     * Ilsang
     */
    ILSANG_NOT_FOUND_ERROR(false, HttpStatus.NOT_FOUND.value(), "일상 정보를 찾을 수 없습니다."),
    SUB_ILSANG_NOT_FOUND_ERROR(false, NOT_FOUND.value(), "중일상 정보를 찾을 수 없습니다."),
    USER_ILSANG_NOT_FOUND_ERROR(false, HttpStatus.NOT_FOUND.value(), "유저 일상 구독 정보를 찾을 수 없습니다."),
    USER_ILSANG_USER_NOT_MATCHED_ERROR(
            false, HttpStatus.BAD_REQUEST.value(), "해당 유저의 구독한 일상 정보가 아닙니다."),
    ALREADY_EXISTS_USER_ILSANG_ERROR(false, HttpStatus.BAD_REQUEST.value(), "이미 해당 일상을 구독한 유저입니다."),

    /**
     * Notification
     */
    NOTIFICATION_NOT_FOUND_ERROR(false, HttpStatus.NOT_FOUND.value(), "알림 정보를 찾을 수 없습니다."),
    NOTIFICATION_USER_NOT_MATCHED_ERROR(false, HttpStatus.BAD_REQUEST.value(), "해당 유저의 알림이 아닙니다."),

    /**
     * Basket
     */
    BASKET_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "장바구니 정보를 찾을 수 없습니다"),
    DATE_NOT_ALLOWED_BEFORE_4(false, HttpStatus.BAD_REQUEST.value(), "오늘로부터 최소 4일 이후의 날짜를 선택하세요."),
    DATE_NOT_ALLOWED_BEFORE_6(false, HttpStatus.BAD_REQUEST.value(), "오늘로부터 최소 6일 이후의 날짜를 선택하세요."),
    BASKET_COUNT_EXCEEDED(false, HttpStatus.BAD_REQUEST.value(), "장바구니 최대 한도를 초과했습니다."),
    ALREADY_EXIST_IN_BASKET_CACHE(false, HttpStatus.BAD_REQUEST.value(), "이미 락이 잡혀있는 재고입니다."),
    INVALID_BASKET_COUPON_IDS(
            false, HttpStatus.BAD_REQUEST.value(), "장바구니와 쿠폰 아이디의 개수가 일치하지 않습니다."),
    INVALID_DELIVERY_DESIRED_DATE(
            false, HttpStatus.BAD_REQUEST.value(), "배송희망일이 오늘 혹은 이전인 상품은 구매할 수 없습니다."),

    /**
     * Order
     */
    INVALID_DELIVERY_TRACKING(false, HttpStatus.BAD_REQUEST.value(), "배송 조회 정보가 올바르지 않습니다."),
    ORDER_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "주문 정보를 찾을 수 없습니다"),
    ORDER_DELIVERY_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "배송 정보를 찾을 수 없습니다"),
    ORDER_TRACKING_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "배송 정보 추적에 실패했습니다"),
    BAD_RETURN_TYPE(false, HttpStatus.NOT_FOUND.value(), "회수 타입이 올바르지 않습니다"),
    ORDER_DELIVERY_IN_QUERY_EXPIRED(false, HttpStatus.NOT_FOUND.value(), "배송 정보 조회 기간이 만료되었습니다."),
    ORDER_GROUP_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "주문 그룹 정보를 찾을 수 없습니다"),
    DELIVERY_COMPANY_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "배송사 정보를 찾을 수 없습니다"),
    MULTIPLE_SALES_TYPE_NOT_ALLOWED(
            false, HttpStatus.BAD_REQUEST.value(), "다른 판매 타입의 상품을 함께 주문할 수 없습니다."),
    ORDER_NOT_MATCHED_USER_AND_PRODUCT(
            false, HttpStatus.BAD_REQUEST.value(), "해당 주문 정보와 유저, 상품 정보가 맞지 않습니다."),
    INVALID_SALES_TYPE(false, BAD_REQUEST.value(), "결제 타입이 다른 상품은 함께 구매할 수 없습니다."),
    INVALID_RETURN_AMOUNT(false, BAD_REQUEST.value(), "추가 배송비가 필요 없는 상품입니다."),
    ORDER_CANNOT_BE_EXTENDED(false, BAD_REQUEST.value(), "구독 연장이 불가능한 상품입니다."),
    BAD_SUBSCRIPTION_DATE(false, BAD_REQUEST.value(), "구독일 설정 오류입니다."),
    INVALID_EARLY_RETURN_AMOUNT(false, BAD_REQUEST.value(), "조기반납 추가 금액이 필요 없는 상품입니다."),
    EARLY_RETURN_NOT_ALLOWED_ORDER(false, BAD_REQUEST.value(), "조기 반납이 불가능한 주문입니다."),
    CANNOT_RETURN(false, BAD_REQUEST.value(), "해당 상태에서는 반품 요청이 불가합니다."),
    ADDITIONAL_RETURN_PAYMENT_NOT_REQUIRED(false, BAD_REQUEST.value(), "교환 반품 추가 금액이 필요 없는 주문입니다."),
    ADDITIONAL_RETURN_PAYMENT_ALREADY_PAID(
            false, BAD_REQUEST.value(), "이미 교환 반품 추가 금액이 결제된 주문입니다."),
    ORDER_RETURN_ALREADY_REQUESTED(false, BAD_REQUEST.value(), "이미 반품 요청된 상태입니다."),
    ORDER_RETURN_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "반품 정보를 찾을 수 없습니다."),
    ORDER_EARLY_RETURN_NOT_AVAILABLE(false, BAD_REQUEST.value(), "조기 반납이 불가능한 상태입니다."),
    EARLY_RETURN_ALREADY_REQUESTED(false, BAD_REQUEST.value(), "이미 조기 반납이 요청된 주문입니다."),
    NEW_PRODUCT_COUPON_ALREADY_ISSUED(false, BAD_REQUEST.value(), "이미 새상품 구매 쿠폰이 발급된 주문입니다."),
    ORDER_CHANGE_NOT_AVAILABLE(false, BAD_REQUEST.value(), "판매 전환이 불가능한 주문입니다."),
    ORDER_CHANGE_ALREADY_DONE(false, BAD_REQUEST.value(), "이미 판매 전환이 완료된 주문입니다."),
    ONLY_DELIVERED_ORDER_CAN_BE_CONFIRMED(false, BAD_REQUEST.value(), "배송 완료된 주문만 구매 확정할 수 있습니다."),
    ONLY_PURCHASE_ORDER_CAN_BE_CONFIRMED(false, BAD_REQUEST.value(), "구매 확정은 구매 주문만 가능합니다."),
    OUT_OF_PRODUCT_ONE_STOCK(false, BAD_REQUEST.value(), "상품의 재고가 부족합니다."),

    /**
     * Payment
     */
    PAYMENT_ERROR(false, HttpStatus.BAD_REQUEST.value(), "결제에 실패하였습니다."),
    CANCELED_BY_USER(false, BAD_REQUEST.value(), "유저에 의해 취소된 주문입니다."),
    INVALID_PG_ENCRYPTION(false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "PG 암호화 정보가 일치하지 않습니다."),
    NOT_REGULAR_TYPE(false, BAD_REQUEST.value(), "정기 결제 상품이 아닙니다."),
    ADDITIONAL_PAYMENT_REQUIRED(false, BAD_REQUEST.value(), "추가 결제가 필요합니다."),
    PAYMENT_NOT_FOUND(false, NOT_FOUND.value(), "결제 정보를 찾을 수 없습니다."),
    NOT_UNPAID_SUBSCRIPTION_PAYMENT(false, NOT_FOUND.value(), "미납 구독을 갱신할 수 없는 결제 건 입니다."),
    PAYMENT_IS_NOT_PENDING(false, BAD_REQUEST.value(), "결제 대기 상태가 아닙니다."),

    /**
     * User
     */
    USER_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "일치하는 유저가 없습니다."),
    USER_EMAIL_OR_PASSWORD_NOT_FOUND(
            false, HttpStatus.NOT_FOUND.value(), "이메일 또는 비밀번호가 / 올바르지 않아요"),
    USER_ADDRESS_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "배송지 정보를 찾을 수 없습니다."),
    MAXIMUM_USER_ADDRESS(false, HttpStatus.BAD_REQUEST.value(), "배송지 등록 한도를 초과했습니다."),
    USER_CARD_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "카드 정보를 찾을 수 없습니다."),
    TOO_MANY_CARDS(false, HttpStatus.NOT_FOUND.value(), "등록 가능한 카드 개수를 초과했어요."),
    DUPLICATED_CARD_NICKNAME(false, HttpStatus.BAD_REQUEST.value(), "이미 등록된 별명이에요."),
    DUPLICATED_CARD(false, HttpStatus.BAD_REQUEST.value(), "카드가 이미 등록되었어요."),
    INVALID_CARD_DELETE(false, HttpStatus.BAD_REQUEST.value(), "정기결제 중인 카드는 삭제가 불가합니다."),
    WITHDRAWAL_REASON_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "일치하는 탈퇴사유가 없습니다."),
    INVALID_WITHDRAWAL_USER(false, HttpStatus.BAD_REQUEST.value(), "탈퇴할 수 없는 유저입니다."),
    AWAKE_USER(false, HttpStatus.BAD_REQUEST.value(), "휴면처리 되지 않은 유저입니다."),
    VERIFICATION_CODE_NOT_FOUND(false, NOT_FOUND.value(), "인증번호가 존재하지 않습니다."),
    WRONG_VERIFICATION_CODE(false, HttpStatus.BAD_REQUEST.value(), "인증번호가 일치하지 않습니다."),
    VERIFICATION_CODE_EXPIRED(false, HttpStatus.BAD_REQUEST.value(), "만료된 인증번호 입니다."),
    DIFFERENT_EMAIL_PHONE_NUMBER(false, HttpStatus.BAD_REQUEST.value(), "해당 계정의 핸드폰번호가 아닙니다."),
    USER_NOT_CERTIFICATED(false, HttpStatus.BAD_REQUEST.value(), "본인인증이 필요합니다."),

    /**
     * Coupon
     */
    COUPON_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "쿠폰 정보를 찾을 수 없습니다."),
    COUPON_ALREADY_ISSUED(false, HttpStatus.BAD_REQUEST.value(), "이미 쿠폰이 발급되었어요."),
    UN_EXPOSED_COUPON(false, HttpStatus.BAD_REQUEST.value(), "다운로드 가능 쿠폰이 아닙니다."),
    UNAVAILABLE_DATE_TO_DOWNLOAD(false, HttpStatus.BAD_REQUEST.value(), "쿠폰 다운로드 가능기간이 아닙니다."),
    COUPON_NOT_APPLICABLE_ON_REGULAR_PAYMENT(false, BAD_REQUEST.value(), "정기 결제에는 쿠폰을 적용할 수 없습니다."),
    REQUIRED_RECAPTCHA_VERIFICATION(false, HttpStatus.UNAUTHORIZED.value(), "로그인을 5회 이상 실패하였습니다."),

    /**
     * Content
     */
    USING_TIP_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "사용팁 정보를 찾을 수 없습니다."),
    USING_TIP_NOT_FOUND_ERROR(false, HttpStatus.NOT_FOUND.value(), "사용팁 정보를 찾을 수 없습니다."),
    NOTICE_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "공지사항 정보를 찾을 수 없습니다."),
    QUESTION_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "자주하는 질문 정보를 찾을 수 없습니다."),

    /**
     * Term
     */
    TERM_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "약관 정보를 찾을 수 없습니다."),

    /**
     * Version
     */
    AOS_VERSION_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "AOS 버전 정보를 찾을 수 없습니다."),
    IOS_VERSION_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "IOS 버전 정보를 찾을 수 없습니다."),
    VERSION_UPDATE_NEEDED(
            false,
            HttpStatus.NON_AUTHORITATIVE_INFORMATION.value(),
            "새로운 버전이 나왔습니다. 원활한 서비스를 위하여 업데이트를 해주세요."),
    ALREADY_UPDATED(true, HttpStatus.OK.value(), "이미 새로운 버전으로 업데이트 되었습니다."),

    /**
     * Maintenance
     */
    MAINTENANCE_NOT_FOUND(false, HttpStatus.NOT_FOUND.value(), "서버 점검 여부를 찾을 수 없습니다."),
    MAINTENANCE_ALREADY_EXISTS(false, HttpStatus.BAD_REQUEST.value(), "서버 점검이 이미 설정되어있습니다."),
    MAINTENANCE_IN_PROGRESS(false, HttpStatus.NON_AUTHORITATIVE_INFORMATION.value(), "서버 점검중입니다."),

    /**
     * 500 : Database, Server 오류
     */
    DATABASE_ERROR(false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "데이터베이스 연결에 실패하였습니다."),
    SERVER_ERROR(false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "서버와의 연결에 실패하였습니다."),
    PASSWORD_ENCRYPTION_ERROR(
            false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "비밀번호 암호화에 실패하였습니다."),
    PASSWORD_DECRYPTION_ERROR(
            false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "비밀번호 복호화에 실패하였습니다."),

    MODIFY_FAIL_USERNAME(false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "유저네임 수정 실패"),
    DELETE_FAIL_USERNAME(false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "유저 삭제 실패"),
    MODIFY_FAIL_MEMO(false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "메모 수정 실패"),

    UNEXPECTED_ERROR(false, HttpStatus.INTERNAL_SERVER_ERROR.value(), "예상치 못한 에러가 발생했습니다."),

    /**
     * Global
     */
    EMPTY_JWT(false, HttpStatus.UNAUTHORIZED.value(), "JWT를 입력해주세요."),
    INVALID_JWT(false, HttpStatus.UNAUTHORIZED.value(), "유효하지 않은 JWT입니다."),
    EXPIRED_JWT(false, HttpStatus.UNAUTHORIZED.value(), "자동로그인이 만료되었어요."),
    DATE_FORMAT_ERROR(false, BAD_REQUEST.value(), "날짜 입력 형식을 맞춰주세요. (yyyy-MM-dd)"),
    DATE_MONTH_FORMAT_ERROR(false, BAD_REQUEST.value(), "날짜 입력 형식을 맞춰주세요. (yyyy-MM)"),
    S3_ERROR(false, BAD_REQUEST.value(), "이미지 저장 url 생성 과정에서 에러가 발생했습니다."),
    FAIL_TO_VERIFY_RECAPTCHA_TOKEN(false, HttpStatus.UNAUTHORIZED.value(), "리캡챠 토큰 인증에 실패하였습니다."),
    ERROR_TO_VERIFY_RECAPTCHA(false, HttpStatus.UNAUTHORIZED.value(), "리캡챠 실패 에러"),
    REQUEST_IS_ALREADY_BEING_PROCESSED_ERROR(false, BAD_REQUEST.value(), "해당 요청이 이미 처리중입니다."),
    GET_LOCKING_ERROR(false, BAD_REQUEST.value(), "락 획득 중 에러가 발생했습니다."),
    LOCK_NOT_FOUND_ERROR(false, BAD_REQUEST.value(), "락이 존재하지 않습니다."),
    RELEASE_LOCKING_ERROR(false, BAD_REQUEST.value(), "현재 쓰레드에서 획득한 잠금이 아니므로 락 해제가 불가능합니다."),
    FILE_FAIL_UPLOAD(false,BAD_REQUEST.value() ,"파일 저장에 실패하였습니다" );

    private final boolean isSuccess;
    private final int code;
    private final String message;

    private BaseResponseStatus(boolean isSuccess, int code, String message) {
        this.isSuccess = isSuccess;
        this.code = code;
        this.message = message;
    }
}
