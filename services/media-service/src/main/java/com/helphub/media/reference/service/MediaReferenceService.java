package com.helphub.media.reference.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.entity.Media;
import com.helphub.media.media.entity.MediaStatus;
import com.helphub.media.media.repository.MediaRepository;
import com.helphub.media.reference.dto.*;
import com.helphub.media.reference.repository.MediaReferenceRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaReferenceService {

        private final MediaRepository mediaRepository;
        private final MediaReferenceRepository referenceRepository;

        public MediaReferenceService(
                        MediaRepository mediaRepository,
                        MediaReferenceRepository referenceRepository) {

                this.mediaRepository = mediaRepository;
                this.referenceRepository = referenceRepository;
        }

        @Transactional
        public MediaReferenceResponse attach(
                        String serviceName,
                        MediaReferenceRequest request) {

                validateScope(serviceName, request.resourceType());

                // Pessimistic lock protects READY check
                // from concurrent state transitions.
                Media media = mediaRepository
                                .findByIdForUpdate(request.mediaId())
                                .orElseThrow(this::notFound);

                if (media.getStatus() != MediaStatus.READY
                                || media.getDeletedAt() != null) {

                        throw conflict(
                                        "MEDIA_NOT_READY",
                                        "Only active READY media may be referenced");
                }

                if (!media.getOwnerId().equals(request.ownerId())) {
                        throw conflict(
                                        "MEDIA_OWNER_MISMATCH",
                                        "Media owner does not match resource owner");
                }

                int inserted = referenceRepository.insertIfAbsent(
                                UUID.randomUUID(),
                                media.getId(),
                                serviceName,
                                request.resourceType(),
                                request.resourceId());

                return new MediaReferenceResponse(
                                media.getId(),
                                serviceName,
                                request.resourceType(),
                                request.resourceId(),
                                inserted == 1);
        }

        @Transactional
        public boolean detach(
                        String serviceName,
                        MediaReferenceDeleteRequest request) {

                validateScope(serviceName, request.resourceType());

                // Lock media to coordinate with future
                // cleanup and reference-attachment operations.
                if (mediaRepository
                                .findByIdForUpdate(request.mediaId())
                                .isEmpty()) {
                        return false;
                }

                int deleted = referenceRepository.deleteReference(
                                request.mediaId(),
                                serviceName,
                                request.resourceType(),
                                request.resourceId());

                return deleted > 0;
        }

        @Transactional(readOnly = true)
        public List<UUID> getByResource(
                        String serviceName,
                        String resourceType,
                        UUID resourceId) {

                validateScope(serviceName, resourceType);

                return referenceRepository.findMediaIdsForResource(
                                serviceName,
                                resourceType,
                                resourceId);
        }

        private void validateScope(
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
                                        "Service cannot manage this resource type");
                }
        }

        private MediaApiException notFound() {
                return new MediaApiException(
                                HttpStatus.NOT_FOUND,
                                "MEDIA_NOT_FOUND",
                                "Media not found");
        }

        private MediaApiException conflict(
                        String code,
                        String message) {

                return new MediaApiException(
                                HttpStatus.CONFLICT,
                                code,
                                message);
        }
}
