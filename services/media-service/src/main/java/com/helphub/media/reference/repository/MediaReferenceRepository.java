package com.helphub.media.reference.repository;

import com.helphub.media.reference.entity.MediaReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface MediaReferenceRepository
                extends JpaRepository<MediaReference, UUID> {

        // Check whether media is referenced
        boolean existsByMedia_Id(UUID mediaId);

        // Count references before physical deletion
        long countByMedia_Id(UUID mediaId);

        // List references of a media file
        List<MediaReference> findByMedia_Id(UUID mediaId);

        // Check duplicate association
        boolean existsByMedia_IdAndServiceNameAndResourceTypeAndResourceId(
                        UUID mediaId,
                        String serviceName,
                        String resourceType,
                        UUID resourceId);

        // Find one specific association
        Optional<MediaReference> findByMedia_IdAndServiceNameAndResourceTypeAndResourceId(
                        UUID mediaId,
                        String serviceName,
                        String resourceType,
                        UUID resourceId);

        @Modifying
        @Query(value = """
                        INSERT INTO media_references (
                            id,
                            media_id,
                            service_name,
                            resource_type,
                            resource_id,
                            created_at
                        )
                        VALUES (
                            :id,
                            :mediaId,
                            :serviceName,
                            :resourceType,
                            :resourceId,
                            CURRENT_TIMESTAMP
                        )
                        ON CONFLICT (
                            media_id,
                            service_name,
                            resource_type,
                            resource_id
                        ) DO NOTHING
                        """, nativeQuery = true)
        int insertIfAbsent(
                        @Param("id") UUID id,
                        @Param("mediaId") UUID mediaId,
                        @Param("serviceName") String serviceName,
                        @Param("resourceType") String resourceType,
                        @Param("resourceId") UUID resourceId);

        @Modifying
        @Query(value = """
                        DELETE FROM media_references
                        WHERE media_id = :mediaId
                          AND service_name = :serviceName
                          AND resource_type = :resourceType
                          AND resource_id = :resourceId
                        """, nativeQuery = true)
        int deleteReference(
                        @Param("mediaId") UUID mediaId,
                        @Param("serviceName") String serviceName,
                        @Param("resourceType") String resourceType,
                        @Param("resourceId") UUID resourceId);

        @Query(value = """
                        SELECT media_id
                        FROM media_references
                        WHERE service_name = :serviceName
                          AND resource_type = :resourceType
                          AND resource_id = :resourceId
                        ORDER BY created_at ASC, media_id ASC
                        """, nativeQuery = true)
        List<UUID> findMediaIdsForResource(
                        @Param("serviceName") String serviceName,
                        @Param("resourceType") String resourceType,
                        @Param("resourceId") UUID resourceId);

        @Query(value = """
                        SELECT EXISTS (
                            SELECT 1
                            FROM media_references
                            WHERE media_id = :mediaId
                              AND service_name = :serviceName
                              AND resource_type = :resourceType
                              AND resource_id = :resourceId
                        )
                        """, nativeQuery = true)
        boolean existsExactBinding(
                        @Param("mediaId") UUID mediaId,
                        @Param("serviceName") String serviceName,
                        @Param("resourceType") String resourceType,
                        @Param("resourceId") UUID resourceId);
}
