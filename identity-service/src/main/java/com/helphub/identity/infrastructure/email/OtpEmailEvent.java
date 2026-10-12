
package com.helphub.identity.infrastructure.email;

import com.helphub.identity.otp.entity.OtpPurpose;

import java.time.OffsetDateTime;

public record OtpEmailEvent(
                String recipient,
                String otp,
                OffsetDateTime expiresAt,
                OtpPurpose purpose) {
}
