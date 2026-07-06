package com.example.demo.service;

import com.example.demo.service.kafka.KafkaProducerService;
import com.example.demo.model.Task;
import com.example.demo.repository.TaskRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.lang.RuntimeException;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final KafkaProducerService kafkaProducerService;

    public TaskService(TaskRepository taskRepository, KafkaProducerService kafkaProducerService) {
        this.taskRepository = taskRepository;
        this.kafkaProducerService = kafkaProducerService;
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
        kafkaProducerService.sendTaskEvent("TASK_UPDATED: ID " + savedTask.getId() + " - status changed to " + savedTask.getStatus());
        return savedTask;
    }

    public void deleteTask(Long id) {
        Task task = getTaskById(id);
        taskRepository.delete(task);
    }
}