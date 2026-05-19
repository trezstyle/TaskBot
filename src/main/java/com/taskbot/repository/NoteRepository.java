package com.taskbot.repository;

import com.taskbot.entity.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    Page<Note> findByUserIdOrderByIsPinnedDescCreatedAtDesc(Long userId, Pageable pageable);

    List<Note> findByUserIdAndIsPinnedTrueOrderByCreatedAtDesc(Long userId);

    Page<Note> findByUserIdAndCategoryOrderByCreatedAtDesc(
            Long userId, String category, Pageable pageable);

    @Query("SELECT n FROM Note n WHERE n.user.id = :userId " +
           "AND (:query IS NULL OR LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(n.tags) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY n.isPinned DESC, n.createdAt DESC")
    Page<Note> searchByTitleOrTags(@Param("userId") Long userId,
                                    @Param("query") String query,
                                    Pageable pageable);

    @Query("SELECT DISTINCT n.category FROM Note n WHERE n.user.id = :userId AND n.category IS NOT NULL")
    List<String> findDistinctCategoriesByUserId(@Param("userId") Long userId);
}
