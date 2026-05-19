package com.taskbot.service;

import com.taskbot.dto.TaskDto;
import com.taskbot.dto.request.CreateTaskRequest;
import com.taskbot.dto.request.UpdateTaskRequest;
import com.taskbot.entity.Task;
import com.taskbot.entity.User;
import com.taskbot.exception.ResourceNotFoundException;
import com.taskbot.mapper.TaskMapper;
import com.taskbot.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final UserService userService;

    @Transactional
    public TaskDto createTask(Long telegramId, CreateTaskRequest request) {
        User user = userService.getUserByTelegramId(telegramId);
        Task task = taskMapper.toEntity(request, user);

        if (task.getPriority() == null) {
            task.setPriority(Task.Priority.MEDIUM);
        }
        if (task.getStatus() == null) {
            task.setStatus(Task.Status.TODO);
        }

        Task saved = taskRepository.save(task);
        log.info("Task created: id={}, userId={}", saved.getId(), user.getId());
        return taskMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public TaskDto getTask(Long taskId, Long telegramId) {
        Task task = findTaskForUser(taskId, telegramId);
        return taskMapper.toDto(task);
    }

    @Transactional(readOnly = true)
    public Page<TaskDto> getUserTasks(Long telegramId, int page, int size) {
        User user = userService.getUserByTelegramId(telegramId);
        Pageable pageable = PageRequest.of(page, size);
        return taskRepository.findByUserIdOrderBySortOrderAscCreatedAtDesc(user.getId(), pageable)
                .map(taskMapper::toDto);
    }

    @Transactional
    public TaskDto updateTask(Long taskId, Long telegramId, UpdateTaskRequest request) {
        Task task = findTaskForUser(taskId, telegramId);
        taskMapper.updateTask(request, task);
        Task saved = taskRepository.save(task);
        log.info("Task updated: id={}", saved.getId());
        return taskMapper.toDto(saved);
    }

    @Transactional
    public void deleteTask(Long taskId, Long telegramId) {
        Task task = findTaskForUser(taskId, telegramId);
        taskRepository.delete(task);
        log.info("Task deleted: id={}", taskId);
    }

    @Transactional(readOnly = true)
    public List<TaskDto> getTasksByStatus(Long telegramId, Task.Status status) {
        User user = userService.getUserByTelegramId(telegramId);
        return taskRepository
                .findByUserIdAndStatusOrderByPriorityDescDueDateAsc(user.getId(), status)
                .stream()
                .map(taskMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskDto> getOverdueTasks(Long telegramId) {
        User user = userService.getUserByTelegramId(telegramId);
        return taskRepository
                .findByUserIdAndDueDateBeforeAndStatusNot(user.getId(), LocalDate.now(), Task.Status.DONE)
                .stream()
                .map(taskMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getIncompleteTaskCount(Long telegramId) {
        User user = userService.getUserByTelegramId(telegramId);
        return taskRepository.countByUserIdAndStatusNot(user.getId(), Task.Status.DONE);
    }

    private Task findTaskForUser(Long taskId, Long telegramId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        User user = userService.getUserByTelegramId(telegramId);
        if (!task.getUser().getId().equals(user.getId())) {
            throw new com.taskbot.exception.UnauthorizedException("Access denied");
        }
        return task;
    }
}
