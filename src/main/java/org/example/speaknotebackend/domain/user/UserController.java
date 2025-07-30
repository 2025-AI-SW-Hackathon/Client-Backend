package org.example.speaknotebackend.domain.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.speaknotebackend.common.response.BaseResponse;
import org.example.speaknotebackend.domain.oauth.GetSocialOAuthRes;
import org.example.speaknotebackend.domain.oauth.OAuthService;
import org.example.speaknotebackend.domain.oauth.SocialLoginType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Tag(name = "Users", description = "유저")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/users")
public class UserController {

    private final OAuthService oAuthService;

    /**
     * Social Login
     *
     * @param socialLoginType (GOOGLE, APPLE, NAVER, KAKAO)
     * @param code API Server 로부터 넘어오는 code
     * @return SNS Login 요청 결과로 받은 Json 형태의 java 객체 (access_token, jwt_token, user_num 등)
     */
    @Operation(summary = "소셜 로그인 합니다.")
    @GetMapping(value = "/auth/{socialLoginType}/login")
    public BaseResponse<GetSocialOAuthRes> socialLogin(
            @PathVariable(name = "socialLoginType") String socialLoginType,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "accessToken", required = false) String accessToken)
            throws Exception {

        SocialLoginType loginType = SocialLoginType.fromString(socialLoginType);
        String ip = getUserIp();
        GetSocialOAuthRes getSocialOAuthRes =
                oAuthService.oAuthLogin(loginType, code, accessToken, ip);

        return new BaseResponse<>(getSocialOAuthRes);
    }

    public String getUserIp() throws Exception {

        String ip = null;
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                        .getRequest();

        ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-RealIP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("REMOTE_ADDR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
