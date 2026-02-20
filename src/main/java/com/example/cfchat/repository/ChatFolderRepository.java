package com.example.cfchat.repository;

import com.example.cfchat.model.ChatFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ChatFolderRepository extends JpaRepository<ChatFolder, UUID> {
    List<ChatFolder> findByUserIdOrderBySortOrderAsc(UUID userId);
    List<ChatFolder> findByUserIdAndParentFolderIdIsNullOrderBySortOrderAsc(UUID userId);
    List<ChatFolder> findByParentFolderIdOrderBySortOrderAsc(UUID parentFolderId);
}
