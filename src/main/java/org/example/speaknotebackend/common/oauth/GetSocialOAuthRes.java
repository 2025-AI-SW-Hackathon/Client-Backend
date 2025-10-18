package org.example.speaknotebackend.common.oauth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(oneOf = {OauthRes.class})
public interface GetSocialOAuthRes {}
