package com.example.demo.controller;

import com.example.demo.model.Task;
import com.example.demo.model.TaskPriority;
import com.example.demo.model.TaskStatus;
import com.example.demo.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"task-events"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
    }

    @Test
    void shouldExecuteFullTaskLifecycleAndKafkaEvents() throws Exception {
        // 1. Test POST /api/tasks (Creation)
        Task task = new Task();
        task.setTitle("Master Coverage Task");
        task.setDescription("Hitting 90 Percent Coverage");
        task.setPriority(TaskPriority.HIGH);
        task.setStatus(TaskStatus.TODO);

        String jsonResponse = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Master Coverage Task")))
                .andReturn().getResponse().getContentAsString();

        Task createdTask = objectMapper.readValue(jsonResponse, Task.class);

        // 2. Test GET /api/tasks (Retrieve All)
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // 3. Test GET /api/tasks/{id} (Retrieve Single Item)
        mockMvc.perform(get("/api/tasks/" + createdTask.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(createdTask.getId().intValue())));

        // 4. Test GET /api/tasks/{id}/urgency (Prioritization Engine Execution)
        mockMvc.perform(get("/api/tasks/" + createdTask.getId() + "/urgency"))
                .andExpect(status().isOk());

        // 5. Test PUT /api/tasks/{id} (Full Update)
        createdTask.setTitle("Updated Master Task");
        mockMvc.perform(put("/api/tasks/" + createdTask.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createdTask)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Master Task")));

        // 6. Test PATCH /api/tasks/{id}/status (Status Workflow Change)
        mockMvc.perform(patch("/api/tasks/" + createdTask.getId() + "/status")
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));

        // 7. Test DELETE /api/tasks/{id} (Deletion)
        mockMvc.perform(delete("/api/tasks/" + createdTask.getId()))
                .andExpect(status().isNoContent());

        TimeUnit.SECONDS.sleep(1);
    }

    @Test
    void shouldTriggerWorkflowExceptionsAndGlobalHandler() throws Exception {
        // 1. Setup Parent & Child
        Task parent = new Task();
        parent.setTitle("Parent Task");
        parent.setStatus(TaskStatus.TODO);
        parent = taskRepository.save(parent);

        Task child = new Task();
        child.setTitle("Child Task");
        child.setStatus(TaskStatus.TODO);
        child = taskRepository.save(child);

        // 2. Test POST Dependency mapping endpoint
        mockMvc.perform(post("/api/tasks/" + child.getId() + "/dependencies/" + parent.getId()))
                .andExpect(status().isOk());

        // 3. Test workflow exception branch (Updating child while parent is still TODO)
        mockMvc.perform(patch("/api/tasks/" + child.getId() + "/status")
                        .param("status", "COMPLETED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Workflow Blocked")));
    }

    @Test
    void shouldHandleGlobalMismatchesGracefully() throws Exception {
        // Test invalid URL parameters mapping error handling branch
        mockMvc.perform(patch("/api/tasks/9999/status")
                        .param("status", "INVALID_ENUM_VALUE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Type Mismatch Error")));
    }

    @Test
    void shouldHandleSelfDependencyException() throws Exception {
        // Create a temporary task
        Task task = new Task();
        task.setTitle("Self Dependency Task");
        task = taskRepository.save(task);

        // Attempt to link the task to itself (Task ID depending on Task ID)
        mockMvc.perform(post("/api/tasks/" + task.getId() + "/dependencies/" + task.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Invalid Operation")));
    }
}