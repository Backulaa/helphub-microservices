package com.helphub.identity.otp.service;

import com.helphub.identity.otp.entity.OtpPurpose;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class OtpHasher {

    private final SecretKeySpec secretKey;

    public OtpHasher(
            @Value("${security.otp.hmac-key-base64}") String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key);

        if (key.length < 32) {
            throw new IllegalArgumentException(
                    "OTP HMAC key must be at least 32 bytes");
        }

        this.secretKey = new SecretKeySpec(
                key,
                "HmacSHA256");
    }

    public String hash(
            UUID userId,
            OtpPurpose purpose,
            UUID challengeId,
            String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);

            String payload = userId + ":" +
                    purpose.name() + ":" +
                    challengeId + ":" +
                    otp;

            byte[] digest = mac.doFinal(
                    payload.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to calculate OTP hash",
                    ex);
        }
    }

    public boolean matches(
            String storedHash,
            UUID userId,
            OtpPurpose purpose,
            UUID challengeId,
            String providedOtp) {
        String calculatedHash = hash(
                userId,
                purpose,
                challengeId,
                providedOtp);

        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                calculatedHash.getBytes(StandardCharsets.UTF_8));
    }
}