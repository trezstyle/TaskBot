package com.taskbot.service;

import com.taskbot.dto.DoctorAppointmentDto;
import com.taskbot.dto.request.CreateDoctorAppointmentRequest;
import com.taskbot.entity.DoctorAppointment;
import com.taskbot.entity.User;
import com.taskbot.exception.ResourceNotFoundException;
import com.taskbot.exception.UnauthorizedException;
import com.taskbot.mapper.DoctorAppointmentMapper;
import com.taskbot.repository.DoctorAppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorAppointmentService {

    private final DoctorAppointmentRepository appointmentRepository;
    private final DoctorAppointmentMapper appointmentMapper;
    private final UserService userService;

    @Transactional
    public DoctorAppointmentDto createAppointment(Long telegramId, CreateDoctorAppointmentRequest request) {
        User user = userService.getUserByTelegramId(telegramId);
        DoctorAppointment appointment = appointmentMapper.toEntity(request, user);
        DoctorAppointment saved = appointmentRepository.save(appointment);
        log.info("Doctor appointment created: id={}, userId={}", saved.getId(), user.getId());
        return appointmentMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<DoctorAppointmentDto> getUserAppointments(Long telegramId, int page, int size) {
        User user = userService.getUserByTelegramId(telegramId);
        return appointmentRepository
                .findByUserIdOrderByAppointmentDateAscAppointmentTimeAsc(user.getId(), PageRequest.of(page, size))
                .map(appointmentMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<DoctorAppointmentDto> getUpcomingAppointments(Long telegramId) {
        User user = userService.getUserByTelegramId(telegramId);
        return appointmentRepository
                .findByUserIdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(user.getId(), LocalDate.now())
                .stream()
                .map(appointmentMapper::toDto)
                .toList();
    }

    @Transactional
    public void deleteAppointment(Long appointmentId, Long telegramId) {
        DoctorAppointment appointment = findAppointmentForUser(appointmentId, telegramId);
        appointmentRepository.delete(appointment);
        log.info("Doctor appointment deleted: id={}", appointmentId);
    }

    private DoctorAppointment findAppointmentForUser(Long appointmentId, Long telegramId) {
        DoctorAppointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("DoctorAppointment", appointmentId));
        User user = userService.getUserByTelegramId(telegramId);
        if (!appointment.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("Access denied");
        }
        return appointment;
    }
}
