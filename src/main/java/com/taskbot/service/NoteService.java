package com.taskbot.service;

import com.taskbot.dto.NoteDto;
import com.taskbot.dto.request.CreateNoteRequest;
import com.taskbot.entity.Note;
import com.taskbot.entity.User;
import com.taskbot.exception.ResourceNotFoundException;
import com.taskbot.exception.UnauthorizedException;
import com.taskbot.mapper.NoteMapper;
import com.taskbot.repository.NoteRepository;
import com.taskbot.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final NoteMapper noteMapper;
    private final EncryptionService encryptionService;
    private final UserService userService;

    @Transactional
    public NoteDto createNote(Long telegramId, CreateNoteRequest request) {
        User user = userService.getUserByTelegramId(telegramId);
        Note note = noteMapper.toEntity(request, user);

        String encryptedContent = encryptionService.encrypt(request.getContent());
        note.setContentEncrypted(encryptedContent);

        if (request.getTags() != null) {
            note.setTags(String.join(",", request.getTags()));
        }
        if (request.getIsPinned() != null) {
            note.setIsPinned(request.getIsPinned());
        }

        Note saved = noteRepository.save(note);
        log.info("Note created: id={}, userId={}", saved.getId(), user.getId());
        return noteMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<NoteDto> getUserNotes(Long telegramId, int page, int size) {
        User user = userService.getUserByTelegramId(telegramId);
        return noteRepository
                .findByUserIdOrderByIsPinnedDescCreatedAtDesc(user.getId(), PageRequest.of(page, size))
                .map(noteMapper::toDto);
    }

    @Transactional(readOnly = true)
    public String getDecryptedContent(Long noteId, Long telegramId) {
        Note note = findNoteForUser(noteId, telegramId);
        return encryptionService.decrypt(note.getContentEncrypted());
    }

    @Transactional(readOnly = true)
    public Page<NoteDto> searchNotes(Long telegramId, String query, int page, int size) {
        User user = userService.getUserByTelegramId(telegramId);
        return noteRepository
                .searchByTitleOrTags(user.getId(), query, PageRequest.of(page, size))
                .map(noteMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<String> getCategories(Long telegramId) {
        User user = userService.getUserByTelegramId(telegramId);
        return noteRepository.findDistinctCategoriesByUserId(user.getId());
    }

    @Transactional
    public void deleteNote(Long noteId, Long telegramId) {
        Note note = findNoteForUser(noteId, telegramId);
        noteRepository.delete(note);
        log.info("Note deleted: id={}", noteId);
    }

    @Transactional
    public NoteDto togglePin(Long noteId, Long telegramId) {
        Note note = findNoteForUser(noteId, telegramId);
        note.setIsPinned(!note.getIsPinned());
        Note saved = noteRepository.save(note);
        return noteMapper.toDto(saved);
    }

    private Note findNoteForUser(Long noteId, Long telegramId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note", noteId));
        User user = userService.getUserByTelegramId(telegramId);
        if (!note.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Access denied");
        }
        return note;
    }
}
