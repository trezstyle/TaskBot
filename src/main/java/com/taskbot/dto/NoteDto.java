package com.taskbot.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteDto {
    private Long id;
    private Long userId;
    private String title;
    private String category;
    private String tags;
    private Boolean isPinned;
    private String contentPreview;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
