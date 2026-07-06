package com.example.demo.service;

import com.example.demo.engine.PrioritizationEngine;
import com.example.demo.model.Task;
import com.example.demo.model.TaskStatus;
import com.example.demo.repository.TaskRepository;
import com.example.demo.service.kafka.KafkaProducerService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final KafkaProducerService kafkaProducerService;
    private final PrioritizationEngine prioritizationEngine;

    public TaskService(TaskRepository taskRepository,
                       KafkaProducerService kafkaProducerService,
                       PrioritizationEngine prioritizationEngine) {
        this.taskRepository = taskRepository;
        this.kafkaProducerService = kafkaProducerService;
        this.prioritizationEngine = prioritizationEngine;
    }

    public Task createTask(Task task) {
        Task savedTask = taskRepository.save(task);
        kafkaProducerService.sendTaskEvent("TASK_CREATED: ID " + savedTask.getId() + " - " + savedTask.getTitle());
        return savedTask;
    }

    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    public Task getTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));
    }

    public Task updateTask(Long id, Task updatedTask) {
        Task existingTask = getTaskById(id);

        existingTask.setTitle(updatedTask.getTitle());
        existingTask.setDescription(updatedTask.getDescription());
        existingTask.setPriority(updatedTask.getPriority());
        existingTask.setStatus(updatedTask.getStatus());
        existingTask.setDueDate(updatedTask.getDueDate());

        Task savedTask = taskRepository.save(existingTask);
        kafkaProducerService.sendTaskEvent("TASK_UPDATED: ID " + savedTask.getId() + " - " + savedTask.getTitle());
        return savedTask;
    }

    public void deleteTask(Long id) {
        Task task = getTaskById(id);
        taskRepository.delete(task);
        kafkaProducerService.sendTaskEvent("TASK_DELETED: ID " + id);
    }

    public double getTaskUrgency(Long id) {
        Task task = getTaskById(id);
        return prioritizationEngine.calculateUrgencyScore(task);
    }

    public Task addDependency(Long taskId, Long dependencyId) {
        Task task = getTaskById(taskId);
        Task dependency = getTaskById(dependencyId);

        if (taskId.equals(dependencyId)) {
            throw new IllegalArgumentException("A task cannot depend on itself");
        }

        task.getDependencies().add(dependency);
        return taskRepository.save(task);
    }

    public Task updateTaskStatus(Long id, TaskStatus newStatus) {
        Task task = getTaskById(id);

        // Block progression if parent dependencies are incomplete
        if (newStatus == TaskStatus.IN_PROGRESS || newStatus == TaskStatus.COMPLETED) {
            for (Task dep : task.getDependencies()) {
                if (dep.getStatus() != TaskStatus.COMPLETED) {
                    throw new IllegalStateException("Cannot start/complete task. Dependency ID " + dep.getId() + " is not COMPLETED.");
                }
            }
        }

        task.setStatus(newStatus);
        Task savedTask = taskRepository.save(task);
        kafkaProducerService.sendTaskEvent("TASK_STATUS_CHANGED: ID " + savedTask.getId() + " to " + newStatus);
        return savedTask;
    }
}