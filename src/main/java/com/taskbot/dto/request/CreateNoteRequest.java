package com.taskbot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateNoteRequest {

    @NotBlank(message = "Note title is required")
    @Size(max = 255)
    private String title;

    @NotBlank(message = "Note content is required")
    @Size(max = 10000)
    private String content;

    @Size(max = 100)
    private String category;

    private List<String> tags;

    private Boolean isPinned;
}
