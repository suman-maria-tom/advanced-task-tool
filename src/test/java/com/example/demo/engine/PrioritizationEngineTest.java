package com.example.demo.engine;

import com.example.demo.model.Task;
import com.example.demo.model.TaskPriority;
import com.example.demo.model.TaskStatus;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrioritizationEngineTest {

    private final PrioritizationEngine engine = new PrioritizationEngine();

    @Test
    void testAllPriorityAndDueDateCombinations() {
        // 1. Test CRITICAL and Overdue
        Task task1 = new Task();
        task1.setPriority(TaskPriority.CRITICAL);
        task1.setDueDate(LocalDateTime.now().minusHours(2)); // Overdue
        double score1 = engine.calculateUrgencyScore(task1);

        // 2. Test HIGH and due within 24 hours
        Task task2 = new Task();
        task2.setPriority(TaskPriority.HIGH);
        task2.setDueDate(LocalDateTime.now().plusHours(5)); // < 24h
        double score2 = engine.calculateUrgencyScore(task2);

        // 3. Test MEDIUM and due within 72 hours
        Task task3 = new Task();
        task3.setPriority(TaskPriority.MEDIUM);
        task3.setDueDate(LocalDateTime.now().plusHours(50)); // < 72h
        double score3 = engine.calculateUrgencyScore(task3);

        // 4. Test LOW and due far out
        Task task4 = new Task();
        task4.setPriority(TaskPriority.LOW);
        task4.setDueDate(LocalDateTime.now().plusHours(200)); // > 72h
        double score4 = engine.calculateUrgencyScore(task4);

        // Assert scores are tiered correctly
        assertTrue(score1 > score2);
        assertTrue(score2 > score3);
        assertTrue(score3 > score4);
    }
}
