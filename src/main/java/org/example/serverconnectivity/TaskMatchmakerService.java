package org.example.serverconnectivity;

import org.example.serverconnectivity.AgentTask;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

@Service
public class TaskMatchmakerService {

    // Queue 1: User Tasks waiting for execution (FIFO)
    private final ConcurrentLinkedQueue<AgentTask> taskQueue = new ConcurrentLinkedQueue<>();

    // Queue 2: Connected Agents waiting for work (FIFO)
    private final ConcurrentLinkedQueue<String> availableAgentQueue = new ConcurrentLinkedQueue<>();

    // Assigned tasks held for long-polling agents
    private final Map<String, AgentTask> assignedTasks = new ConcurrentHashMap<>();

    // Active user HTTP connection futures
    private final Map<String, CompletableFuture<String>> userFutures = new ConcurrentHashMap<>();

    // Backup cache for orphaned results (User disconnected before completion)
    private final Map<String, String> orphanedResults = new ConcurrentHashMap<>();

    // Automated cleaner for expired orphaned results
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    /**
     * Called by /api/ask when a user submits a query.
     */
    public CompletableFuture<String> submitTask(String taskId, String prompt) {
        AgentTask task = new AgentTask(taskId, prompt);
        CompletableFuture<String> future = new CompletableFuture<>();

        userFutures.put(taskId, future);
        taskQueue.add(task);

        tryMatch();
        return future;
    }

    /**
     * Called by /api/continue when an agent checks in.
     */
    public AgentTask registerAgentAndPoll(String connectionId) {
        if (!availableAgentQueue.contains(connectionId)) {
            availableAgentQueue.add(connectionId);
        }

        tryMatch();
        return assignedTasks.remove(connectionId);
    }

    /**
     * Dual-FIFO Matchmaker Engine.
     */
    private synchronized void tryMatch() {
        while (!taskQueue.isEmpty() && !availableAgentQueue.isEmpty()) {
            AgentTask task = taskQueue.poll();
            String agentId = availableAgentQueue.poll();

            if (agentId != null && task != null) {
                System.out.println("[Matchmaker] FIFO Paired: Task [" + task.getId() + "] -> Agent [" + agentId + "]");
                assignedTasks.put(agentId, task);
            }
        }
    }

    /**
     * Called when an agent returns a result.
     * Handles both active and disconnected (orphaned) user states.
     */
    public void completeTask(String taskId, String resultText) {
        CompletableFuture<String> future = userFutures.remove(taskId);

        if (future != null && !future.isCancelled()) {
            // Case A: User is still connected -> Deliver result immediately!
            future.complete(resultText);
            System.out.println("[Task Complete] Result delivered directly to connected user: " + taskId);
        } else {
            // Case B: User disconnected or cancelled connection -> Store in Orphaned Cache
            System.out.println("[Task Complete - Orphaned] User disconnected for task " + taskId + ". Caching temporarily.");
            storeOrphanedResult(taskId, resultText);
        }
    }

    /**
     * Caches orphaned results for a 10-minute TTL window before auto-purging.
     */
    private void storeOrphanedResult(String taskId, String resultText) {
        orphanedResults.put(taskId, resultText);

        // Schedule automated memory purge after 10 minutes to prevent memory leaks
        cleanupExecutor.schedule(() -> {
            if (orphanedResults.remove(taskId) != null) {
                System.out.println("[Memory Cleanup] Expired orphaned result purged from cache: " + taskId);
            }
        }, 10, TimeUnit.MINUTES);
    }

    /**
     * Optional lookup endpoint: Allows a re-connected user to fetch an orphaned result by taskId.
     */
    public String retrieveOrphanedResult(String taskId) {
        return orphanedResults.remove(taskId);
    }

    public void unregisterAgent(String connectionId) {
        availableAgentQueue.remove(connectionId);
    }
}