package com.taskbot.mapper;

import com.taskbot.dto.NoteDto;
import com.taskbot.dto.request.CreateNoteRequest;
import com.taskbot.entity.Note;
import com.taskbot.entity.User;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NoteMapper {

    @Mapping(target = "contentPreview", expression = "java(note.getContentEncrypted() != null ? \"[encrypted]\" : null)")
    NoteDto toDto(Note note);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "contentEncrypted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Note toEntity(CreateNoteRequest request, User user);

    default String mapTags(List<String> tags) {
        return tags != null ? String.join(",", tags) : null;
    }
}
