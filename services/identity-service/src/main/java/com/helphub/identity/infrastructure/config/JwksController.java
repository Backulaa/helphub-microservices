package com.helphub.identity.infrastructure.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
public class JwksController {

    private final RSAKey signingJwk;

    public JwksController(RSAKey signingJwk) {
        this.signingJwk = signingJwk;
    }

    @GetMapping("/internal/jwks")
    public ResponseEntity<Map<String, Object>> getPublicKeys() {

        Map<String, Object> json = new JWKSet(signingJwk.toPublicJWK()).toJSONObject();

        return ResponseEntity.ok()
                .cacheControl(
                        CacheControl.maxAge(Duration.ofMinutes(5))
                                .cachePublic())
                .body(json);
    }
}
