package org.example.speaknotebackend.common.oauth;

public interface SocialUser {
    String getEmail();

    String getSocialId();

    String getName();

    Long getFolderId();

    SocialInfoRes toSocialInfoRes(SocialTermsAgreementResponse termsAgreements);
}
