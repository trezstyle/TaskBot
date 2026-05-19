package com.taskbot.service;

import com.taskbot.dto.MeetingDto;
import com.taskbot.dto.request.CreateMeetingRequest;
import com.taskbot.entity.Meeting;
import com.taskbot.entity.User;
import com.taskbot.exception.ResourceNotFoundException;
import com.taskbot.exception.UnauthorizedException;
import com.taskbot.mapper.MeetingMapper;
import com.taskbot.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingMapper meetingMapper;
    private final UserService userService;
    private final ReminderService reminderService;

    @Transactional
    public MeetingDto createMeeting(Long telegramId, CreateMeetingRequest request) {
        User user = userService.getUserByTelegramId(telegramId);
        Meeting meeting = meetingMapper.toEntity(request, user);
        Meeting saved = meetingRepository.save(meeting);
        reminderService.createMeetingReminder(saved);
        log.info("Meeting created: id={}, userId={}", saved.getId(), user.getId());
        return meetingMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<MeetingDto> getUserMeetings(Long telegramId, int page, int size) {
        User user = userService.getUserByTelegramId(telegramId);
        return meetingRepository
                .findByUserIdOrderByMeetingDateAscMeetingTimeAsc(user.getId(), PageRequest.of(page, size))
                .map(meetingMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<MeetingDto> getUpcomingMeetings(Long telegramId) {
        User user = userService.getUserByTelegramId(telegramId);
        return meetingRepository
                .findByUserIdAndMeetingDateGreaterThanEqualOrderByMeetingDateAsc(user.getId(), LocalDate.now())
                .stream()
                .map(meetingMapper::toDto)
                .toList();
    }

    @Transactional
    public void deleteMeeting(Long meetingId, Long telegramId) {
        Meeting meeting = findMeetingForUser(meetingId, telegramId);
        meetingRepository.delete(meeting);
        log.info("Meeting deleted: id={}", meetingId);
    }

    private Meeting findMeetingForUser(Long meetingId, Long telegramId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting", meetingId));
        User user = userService.getUserByTelegramId(telegramId);
        if (!meeting.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Access denied");
        }
        return meeting;
    }
}
