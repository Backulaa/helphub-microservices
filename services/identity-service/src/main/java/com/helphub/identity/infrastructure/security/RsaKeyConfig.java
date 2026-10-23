package com.helphub.identity.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.*;

import java.nio.file.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.KeyFactory;
import java.security.spec.*;
import java.util.Base64;

@Configuration
public class RsaKeyConfig {

    @Bean
    public RSAKey signingJwk(
            @Value("${security.jwt.private-key-path}") String privatePath,
            @Value("${security.jwt.public-key-path}") String publicPath,
            @Value("${security.jwt.key-id}") String keyId) throws Exception {

        KeyFactory factory = KeyFactory.getInstance("RSA");

        RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(
                new PKCS8EncodedKeySpec(readPem(
                        privatePath,
                        "-----BEGIN PRIVATE KEY-----",
                        "-----END PRIVATE KEY-----")));

        RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                new X509EncodedKeySpec(readPem(
                        publicPath,
                        "-----BEGIN PUBLIC KEY-----",
                        "-----END PUBLIC KEY-----")));

        if (publicKey.getModulus().bitLength() < 2048
                || !privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException("Invalid RSA signing key pair");
        }

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(keyId)
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey signingJwk) {
        return new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(
                        new JWKSet(signingJwk)));
    }

    private static byte[] readPem(
            String path, String begin, String end) throws Exception {
        String pem = Files.readString(Path.of(path));

        if (!pem.contains(begin) || !pem.contains(end)) {
            throw new IllegalArgumentException("Invalid PEM format: " + path);
        }

        String encoded = pem.replace(begin, "")
                .replace(end, "")
                .replaceAll("\\s", "");

        return Base64.getDecoder().decode(encoded);
    }
}
