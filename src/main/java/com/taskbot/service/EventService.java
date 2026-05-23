package com.taskbot.service;

import com.taskbot.dto.EventDto;
import com.taskbot.entity.Event;
import com.taskbot.entity.User;
import com.taskbot.exception.ResourceNotFoundException;
import com.taskbot.exception.UnauthorizedException;
import com.taskbot.mapper.EventMapper;
import com.taskbot.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final UserService userService;

    @Transactional
    public EventDto createEvent(Long telegramId, String title, String description,
                                 LocalDate date, LocalTime time, String color,
                                 Integer reminderMinutesBefore) {
        User user = userService.getUserByTelegramId(telegramId);
        Event event = Event.builder()
                .user(user)
                .title(title)
                .description(description)
                .eventDate(date)
                .eventTime(time)
                .color(color)
                .reminderMinutesBefore(reminderMinutesBefore)
                .reminderSent(false)
                .build();
        Event saved = eventRepository.save(event);
        log.info("Event created: id={}, userId={}, date={}", saved.getId(), user.getId(), date);
        return eventMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public EventDto getEvent(Long eventId, Long telegramId) {
        Event event = findEventForUser(eventId, telegramId);
        return eventMapper.toDto(event);
    }

    @Transactional(readOnly = true)
    public List<EventDto> getEventsByDate(Long telegramId, LocalDate date) {
        User user = userService.getUserByTelegramId(telegramId);
        return eventRepository.findByUserIdAndEventDate(user.getId(), date)
                .stream()
                .map(eventMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventDto> getEventsInRange(Long telegramId, LocalDate start, LocalDate end) {
        User user = userService.getUserByTelegramId(telegramId);
        return eventRepository.findByUserIdAndEventDateBetweenOrderByEventDateAscEventTimeAsc(
                        user.getId(), start, end)
                .stream()
                .map(eventMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventDto> getUpcomingEvents(Long telegramId) {
        User user = userService.getUserByTelegramId(telegramId);
        return eventRepository.findByUserIdAndEventDateGreaterThanEqualOrderByEventDateAscEventTimeAsc(
                        user.getId(), LocalDate.now())
                .stream()
                .map(eventMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventDto> getPastEvents(Long telegramId) {
        User user = userService.getUserByTelegramId(telegramId);
        return eventRepository.findByUserIdAndEventDateLessThanOrderByEventDateDescEventTimeDesc(
                        user.getId(), LocalDate.now())
                .stream()
                .map(eventMapper::toDto)
                .toList();
    }

    @Transactional
    public int deleteAllPastEvents(Long telegramId) {
        User user = userService.getUserByTelegramId(telegramId);
        LocalDate today = LocalDate.now();
        long count = eventRepository.countByUserIdAndEventDateLessThan(user.getId(), today);
        eventRepository.deleteAllByUserIdAndEventDateLessThan(user.getId(), today);
        log.info("Deleted {} past events for user {}", count, user.getId());
        return (int) count;
    }

    @Transactional(readOnly = true)
    public Page<EventDto> getUserEvents(Long telegramId, int page, int size) {
        User user = userService.getUserByTelegramId(telegramId);
        Pageable pageable = PageRequest.of(page, size);
        return eventRepository.findByUserIdOrderByEventDateAscEventTimeAsc(user.getId(), pageable)
                .map(eventMapper::toDto);
    }

    @Transactional
    public EventDto updateEvent(Long eventId, Long telegramId, EventDto dto) {
        Event event = findEventForUser(eventId, telegramId);
        eventMapper.update(dto, event);
        Event saved = eventRepository.save(event);
        log.info("Event updated: id={}", saved.getId());
        return eventMapper.toDto(saved);
    }

    @Transactional
    public void deleteEvent(Long eventId, Long telegramId) {
        Event event = findEventForUser(eventId, telegramId);
        eventRepository.delete(event);
        log.info("Event deleted: id={}", eventId);
    }

    @Transactional(readOnly = true)
    public long getEventCount(Long telegramId) {
        User user = userService.getUserByTelegramId(telegramId);
        return eventRepository.countByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public List<Event> getDueReminders() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        return eventRepository.findDueReminders(today, now);
    }

    @Transactional(readOnly = true)
    public List<Event> getDueRemindersForUser(Long userId, LocalDate today, LocalDateTime now) {
        return eventRepository.findDueRemindersForUser(userId, today, now);
    }

    @Transactional
    public void markReminderSent(Long eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            event.setReminderSent(true);
            eventRepository.save(event);
        });
    }

    @Transactional
    public void markReminderSentReset(Long eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            event.setReminderSent(false);
            eventRepository.save(event);
        });
    }

    private Event findEventForUser(Long eventId, Long telegramId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
        User user = userService.getUserByTelegramId(telegramId);
        if (!event.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Access denied");
        }
        return event;
    }
}
