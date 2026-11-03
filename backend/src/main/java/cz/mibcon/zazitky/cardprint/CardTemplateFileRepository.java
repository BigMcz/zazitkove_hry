package cz.mibcon.zazitky.cardprint;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cz.mibcon.zazitky.cardprint.domain.CardTemplateFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CardTemplateFileRepository extends JpaRepository<CardTemplateFile, UUID> {

    /** Name, type and size of a file without loading its content. */
    interface FileInfo {
        UUID getId();

        String getFileName();

        String getContentType();

        int getSizeBytes();
    }

    @Query("select f.id as id, f.fileName as fileName, f.contentType as contentType, f.sizeBytes as sizeBytes"
            + " from CardTemplateFile f where f.sha256 = :sha256")
    Optional<FileInfo> findInfoBySha256(String sha256);

    @Query("select f.id as id, f.fileName as fileName, f.contentType as contentType, f.sizeBytes as sizeBytes"
            + " from CardTemplateFile f where f.id in :ids")
    List<FileInfo> findInfoByIdIn(Collection<UUID> ids);
}
