package org.example.speaknotebackend.domain.oauth;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.speaknotebackend.common.exceptions.BaseException;
import org.example.speaknotebackend.domain.repository.FolderRepository;
import org.example.speaknotebackend.domain.user.UserService;
import org.example.speaknotebackend.entity.Folder;
import org.example.speaknotebackend.entity.SocialType;
import org.example.speaknotebackend.entity.User;
import org.example.speaknotebackend.global.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {
    private final UserService userService;
    private final JwtService jwtService;

    @Transactional
    public GetSocialOAuthRes oAuthLogin(
            SocialLoginType socialLoginType, String code, String accessToken)
            throws IOException {

        final long start = System.currentTimeMillis();
        log.info("[oAuthLogin] start socialLoginType={}, codePresent={}, accessTokenPresent={}",
                socialLoginType, code != null, accessToken != null);

        SocialOauth socialOauth = socialLoginType.getSocialOauth();
        log.debug("[oAuthLogin] resolved SocialOauth impl={}", socialOauth.getClass().getSimpleName());

        // 1) 액세스 토큰 확보
        if (accessToken == null) {
            log.info("[oAuthLogin] accessToken is null → fetching from provider (codePresent={})", code != null);
            accessToken = socialOauth.getAccessToken(code);
            log.info("[oAuthLogin] received accessToken (len={})", safeLen(accessToken));
        } else {
            log.info("[oAuthLogin] client provided accessToken (len={})", safeLen(accessToken));
        }

        // 2) 소셜 사용자 정보 조회
        log.info("[oAuthLogin] requesting userInfo from provider…");
        SocialUser socialUser = socialOauth.getUserInfo(accessToken);
        log.info("[oAuthLogin] provider userInfo email={}, socialId={}",
                maskEmail(socialUser.getEmail()), maskId(socialUser.getSocialId()));

        User user;
        try {
            // 3) 기존 유저 조회
            log.info("[oAuthLogin] find local user by email+socialId");
            user = userService.findByEmailAndSocialId(socialUser.getEmail(), socialUser.getSocialId());
            log.info("[oAuthLogin] existing user found id={}", user.getId());
        } catch (Exception e) {
            // 4) 없으면 생성
            log.warn("[oAuthLogin] user not found or lookup error → creating new user. reason={}", e.getMessage(), e);
            SocialType socialType = convertToSocialType(socialLoginType);
            log.info("[oAuthLogin] createUser email={}, name={}, socialType={}",
                    maskEmail(socialUser.getEmail()), socialUser.getName(), socialType);
            user = userService.createUser(
                    socialUser.getEmail(),
                    socialUser.getName(),
                    socialUser.getSocialId(),
                    socialType
            );
            log.info("[oAuthLogin] new user created id={}", user.getId());
        }

        try {
            // 5) JWT 발급
            log.info("[oAuthLogin] issuing JWTs for userId={}", user.getId());
            String accessJwtToken = jwtService.createUserJwt(user.getId());
            log.debug("[oAuthLogin] accessJwt issued len={}, suffix={}", safeLen(accessJwtToken), lastN(accessJwtToken, 6));

            String refreshJwtToken = jwtService.createUserRefreshJwt(user.getId());
            log.debug("[oAuthLogin] refreshJwt issued len={}, suffix={}", safeLen(refreshJwtToken), lastN(refreshJwtToken, 6));

            // 6) 리프레시 토큰 저장
            user.updateRefreshToken(refreshJwtToken);
            log.info("[oAuthLogin] refresh token saved for userId={}", user.getId());

            // 7) 응답 생성
            OauthRes res = new OauthRes(user.getId(), accessJwtToken, refreshJwtToken);
            log.info("[oAuthLogin] success userId={}, elapsedMs={}", user.getId(), (System.currentTimeMillis() - start));
            return res;
        } catch (BaseException e) {
            log.error("[oAuthLogin] failed to issue JWTs for userId={}: {}", (user != null ? user.getId() : null), e.getMessage(), e);
            throw e;
        }
    }

    /* ===================== 도움 메서드들(민감정보 마스킹) ===================== */

    private int safeLen(String s) {
        return (s == null) ? 0 : s.length();
    }

    private String lastN(String s, int n) {
        if (s == null) return "null";
        return s.length() <= n ? s : s.substring(s.length() - n);
    }

    private String maskEmail(String email) {
        if (email == null) return "null";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        String user = email.substring(0, Math.min(2, at));
        String domain = email.substring(at + 1);
        String[] parts = domain.split("\\.", 2);
        String domHead = parts[0];
        String domMasked = (domHead.length() <= 2) ? "**" : domHead.substring(0, 2) + "***";
        String tail = (parts.length > 1) ? "." + parts[1] : "";
        return user + "***@" + domMasked + tail;
    }

    private String maskId(String id) {
        if (id == null) return "null";
        int show = Math.min(3, id.length());
        return id.substring(0, show) + "***";
    }

    private SocialType convertToSocialType(SocialLoginType socialLoginType) {
        switch (socialLoginType) {
            case GOOGLE:
                return SocialType.GOOGLE;
            case KAKAO:
                return SocialType.KAKAO;
            case APPLE:
                return SocialType.APPLE;
            default:
                throw new IllegalArgumentException("지원하지 않는 소셜 로그인 타입: " + socialLoginType);
        }
    }

    private final FolderRepository folderRepository;
}
