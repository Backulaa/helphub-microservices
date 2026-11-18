package com.helphub.media.media.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.media.recovery", name = "simulate-crash-after-upload", havingValue = "true")
public class DevPrivateUploadFaultInjector {

    public void afterCloudinaryUpload(UUID mediaId) {
        throw new IllegalStateException(
                "DEV_SIMULATED_CRASH_AFTER_CLOUDINARY: "
                        + mediaId);
    }
}
