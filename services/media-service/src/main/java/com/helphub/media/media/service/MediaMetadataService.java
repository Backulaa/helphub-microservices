package com.helphub.media.media.service;

import com.helphub.media.common.exception.MediaApiException;
import com.helphub.media.media.dto.MediaMetadataResponse;
import com.helphub.media.media.entity.*;
import com.helphub.media.media.repository.MediaRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MediaMetadataService {

    private final MediaRepository mediaRepository;

    public MediaMetadataService(
            MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @Transactional(readOnly = true)
    public MediaMetadataResponse get(
            UUID mediaId,
            UUID requesterId) {

        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(this::notFound);

        if (!canView(media, requesterId)) {
            throw notFound();
        }

        return MediaMetadataResponse.from(media);
    }

    @Transactional(readOnly = true)
    public List<MediaMetadataResponse> getBatch(
            List<UUID> mediaIds,
            UUID requesterId) {

        if (mediaIds == null
                || mediaIds.isEmpty()
                || mediaIds.size() > 100
                || mediaIds.stream().anyMatch(Objects::isNull)) {

            throw new MediaApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_MEDIA_BATCH",
                    "Provide between 1 and 100 valid media IDs");
        }

        // Preserve request order and remove duplicates.
        Set<UUID> uniqueIds = new LinkedHashSet<>(mediaIds);

        Map<UUID, Media> found = mediaRepository.findAllById(uniqueIds)
                .stream()
                .collect(Collectors.toMap(
                        Media::getId,
                        Function.identity()));

        List<MediaMetadataResponse> result = new ArrayList<>();

        for (UUID id : uniqueIds) {
            Media media = found.get(id);

            // Never expose unauthorized PRIVATE media.
            if (media != null && canView(media, requesterId)) {
                result.add(
                        MediaMetadataResponse.from(media));
            }
        }

        return result;
    }

    private boolean canView(
            Media media,
            UUID requesterId) {

        // Only completed media can be retrieved.
        if (media.getStatus() != MediaStatus.READY
                || media.getDeletedAt() != null) {
            return false;
        }

        // PUBLIC metadata is available to any
        // authenticated HELPhub user.
        if (media.getVisibility() == MediaVisibility.PUBLIC) {
            return true;
        }

        // PRIVATE metadata is owner-only in this step.
        return media.getVisibility() == MediaVisibility.PRIVATE
                && requesterId != null
                && requesterId.equals(media.getOwnerId());
    }

    private MediaApiException notFound() {
        return new MediaApiException(
                HttpStatus.NOT_FOUND,
                "MEDIA_NOT_FOUND",
                "Media not found");
    }
}
