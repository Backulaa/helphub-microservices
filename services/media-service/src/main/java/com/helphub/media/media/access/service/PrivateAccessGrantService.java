package com.helphub.media.media.access.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.access.dto.*;
import com.helphub.media.media.entity.Media;
import com.helphub.media.media.entity.MediaStatus;
import com.helphub.media.media.entity.MediaVisibility;
import com.helphub.media.media.repository.MediaRepository;
import com.helphub.media.reference.repository.MediaReferenceRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class PrivateAccessGrantService {

    private static final Logger log = LoggerFactory.getLogger(
            PrivateAccessGrantService.class);

    private final MediaRepository mediaRepository;
    private final MediaReferenceRepository referenceRepository;
    private final CloudinaryTimedDownloadService timedDownload;

    public PrivateAccessGrantService(
            MediaRepository mediaRepository,
            MediaReferenceRepository referenceRepository,
            CloudinaryTimedDownloadService timedDownload) {

        this.mediaRepository = mediaRepository;
        this.referenceRepository = referenceRepository;
        this.timedDownload = timedDownload;
    }

    @Transactional(readOnly = true)
    public PrivateAccessGrantResponse issue(
            String serviceName,
            PrivateAccessGrantRequest request) {

        requireAllowedScope(
                serviceName,
                request.resourceType());

        Media media = mediaRepository
                .findById(request.mediaId())
                .orElseThrow(this::notFound);

        // Never issue grants for PUBLIC, incomplete,
        // deleting or deleted media.
        if (media.getStatus() != MediaStatus.READY
                || media.getVisibility() != MediaVisibility.PRIVATE) {

            throw notFound();
        }

        // The caller must have an existing binding
        // to exactly this media and business resource.
        boolean referenceExists = referenceRepository.existsExactBinding(
                media.getId(),
                serviceName,
                request.resourceType(),
                request.resourceId());

        if (!referenceExists) {
            throw notFound();
        }

        // Trusted service asserts that it already
        // authenticated viewerId and checked the
        // user's permission on the business resource.
        CloudinaryTimedDownloadService.SignedLink link = timedDownload.issue(media);

        // Record identifiers only.
        // Never log link.url().
        log.info(
                "PRIVATE_GRANT_ISSUED mediaId={} viewerId={} "
                        + "service={} resourceType={} resourceId={} "
                        + "expiresAt={}",
                media.getId(),
                request.viewerId(),
                serviceName,
                request.resourceType(),
                request.resourceId(),
                link.expiresAt());

        return new PrivateAccessGrantResponse(
                media.getId(),
                link.url(),
                link.expiresAt(),
                link.expiresInSeconds());
    }

    private void requireAllowedScope(
            String serviceName,
            String resourceType) {

        boolean allowed = switch (serviceName) {

            case "community" ->
                Set.of("POST", "COMMENT")
                        .contains(resourceType);

            case "support" ->
                Set.of(
                        "SUPPORT_REQUEST",
                        "SUPPORT_MESSAGE").contains(resourceType);

            default -> false;
        };

        if (!allowed) {
            throw new MediaApiException(
                    HttpStatus.FORBIDDEN,
                    "INVALID_SERVICE_RESOURCE_SCOPE",
                    "Service cannot grant access to this resource type");
        }
    }

    private MediaApiException notFound() {
        return new MediaApiException(
                HttpStatus.NOT_FOUND,
                "MEDIA_NOT_FOUND",
                "Media not found");
    }
}
