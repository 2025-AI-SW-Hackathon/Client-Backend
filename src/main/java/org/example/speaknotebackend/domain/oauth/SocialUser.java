package org.example.speaknotebackend.domain.oauth;

public interface SocialUser {
    String getEmail();

    String getSocialId();

    String getName();

    SocialInfoRes toSocialInfoRes(SocialTermsAgreementResponse termsAgreements);
}
