package com.helphub.identity.auth.service;

import com.helphub.identity.mfa.dto.MfaChallengeResponse;

public sealed interface LoginResult {

    record Authenticated(IssuedTokens tokens)
            implements LoginResult {
    }

    record RequiresMfa(MfaChallengeResponse challenge)
            implements LoginResult {
    }
}