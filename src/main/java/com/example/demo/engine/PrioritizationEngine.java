package com.example.demo.engine;

import com.example.demo.model.Task;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class PrioritizationEngine {

    public double calculateUrgencyScore(Task task) {
        double score = 0.0;

        // 1. Weight based on Base Priority Enum
        switch (task.getPriority()) {
            case CRITICAL -> score += 50.0;
            case HIGH     -> score += 35.0;
            case MEDIUM   -> score += 20.0;
            case LOW      -> score += 5.0;
        }

        // 2. Weight based on Proximity of Due Date
        if (task.getDueDate() != null) {
            long hoursLeft = ChronoUnit.HOURS.between(LocalDateTime.now(), task.getDueDate());
            if (hoursLeft <= 0) {
                score += 50.0; // Overdue items get max urgency points
            } else if (hoursLeft <= 24) {
                score += 40.0; // Due within a day
            } else if (hoursLeft <= 72) {
                score += 20.0; // Due within 3 days
            } else {
                score += 5.0;
            }
        }
        return score;
    }
}