package com.taskbot.repository;

import com.taskbot.entity.DoctorAppointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DoctorAppointmentRepository extends JpaRepository<DoctorAppointment, Long> {

    Page<DoctorAppointment> findByUserIdOrderByAppointmentDateAscAppointmentTimeAsc(
            Long userId, Pageable pageable);

    List<DoctorAppointment> findByUserIdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
            Long userId, LocalDate date);

    List<DoctorAppointment> findByReminderSentFalseAndAppointmentDateLessThanEqual(LocalDate date);
}
