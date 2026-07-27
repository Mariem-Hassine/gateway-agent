package org.example.serverconnectivity;

import org.example.serverconnectivity.AgentTask;
import org.example.serverconnectivity.TaskMatchmakerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@CrossOrigin(origins = "*")
@RestController
public class ApiController {

    // Registries for Agent Metadata and Session tracking
    private final ConcurrentHashMap<String, AgentConnectRequest> agentHardwareRegistry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentConnectRequest> activeSessions = new ConcurrentHashMap<>();
    // Keeps track of which agents have been flagged for state invalidation
    private final Map<String, Boolean> needsFullSync = new ConcurrentHashMap<>();

    // Inject the Matchmaker Service
    private final TaskMatchmakerService matchmakerService;

    public ApiController(TaskMatchmakerService matchmakerService) {
        this.matchmakerService = matchmakerService;
    }

    @GetMapping("/api/hello")
    public String sayHello(@RequestParam(value = "name", defaultValue = "World") String name) {
        return String.format("Hello, %s! Your HTTP request successfully reached my TCP port!", name);
    }

    // 1. Asynchronous User Query Endpoint (Non-blocking)
    @PostMapping("/api/ask")
    // tells Spring Boot to handle this request asynchronously without blocking server threads
    public CompletableFuture<ResponseEntity<String>> receiveUserQuery(@RequestBody String query) {
        System.out.println("[Gateway] Received query from User: " + query);
        String taskId = "TASK-" + UUID.randomUUID().toString().substring(0, 8);


        //Hands the task over to the matchmaker service,
        // placing it in the FIFO Task Queue,
        // and returns an unfulfilled CompletableFuture
        return matchmakerService.submitTask(taskId, query)
                .orTimeout(60, TimeUnit.SECONDS)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.status(504).body("Error: Task execution timed out or agent failed."));
    }

    // 2. Agent Onboarding Endpoint
    @PostMapping("/api/connect")
    public ResponseEntity<Map<String, String>> handleAgentConnect(@RequestBody AgentConnectRequest connectData) {
        if (connectData.getIdAgent() == null || connectData.getIdAgent().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid Request: idAgent missing."));
        }

        // Dynamic Connection ID Generation
        String connectionId = "CONN-" + UUID.randomUUID().toString().substring(0, 8);

        agentHardwareRegistry.put(connectData.getIdAgent(), connectData);
        activeSessions.put(connectionId, connectData);

        System.out.println("=== AGENT REGISTERED (/api/connect) ===");
        System.out.println("[Registry] Agent ID: " + connectData.getIdAgent() + " | Session: " + connectionId);

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("status", "ok");
        responseBody.put("connectionId", connectionId);

        return ResponseEntity.ok(responseBody);
    }

    // 3. Agent Telemetry Sync & Long Polling Endpoint
    @PostMapping("/api/continue")
    public ResponseEntity<String> handleAgentSync(@RequestBody StatusRequest agentData) throws InterruptedException {
        String connectionId = agentData.getConnectionId();

        // Validate Connection ID
        if (connectionId == null || !activeSessions.containsKey(connectionId)) {
            System.err.println("[Gateway] Rejecting sync: Invalid or unassigned connectionId [" + connectionId + "].");
            return ResponseEntity.status(401).body("ERROR_INVALID_CONNECTION_ID");
        }

        AgentConnectRequest sessionData = activeSessions.get(connectionId);

        // Update Dynamic Hardware Metrics
        if (agentData.getCpu() != null) sessionData.setCpu(agentData.getCpu());
        if (agentData.getGpu() != null) sessionData.setGpu(agentData.getGpu());
        if (agentData.getVram() != null) sessionData.setVram(agentData.getVram());
        if (agentData.getDiskUsage() != null) sessionData.setDiskUsage(agentData.getDiskUsage());
        if (agentData.getModelsInVRAM() != null) sessionData.setModelsInVRAM(agentData.getModelsInVRAM());

        // Handle State Invalidation Refresh Trigger
        if (Boolean.TRUE.equals(needsFullSync.get(connectionId))) {
            System.out.println("\n=== REFRESHING AGENT CHARACTERISTICS (/api/continue) ===");
            if (agentData.getAvailableModels() != null) sessionData.setAvailableModels(agentData.getAvailableModels());
            if (agentData.getMaxCpu() != null) sessionData.setMaxCpu(agentData.getMaxCpu());
            if (agentData.getMaxGpu() != null) sessionData.setMaxGpu(agentData.getMaxGpu());
            if (agentData.getDiskPartitions() != null) sessionData.setDiskPartitions(agentData.getDiskPartitions());

            agentHardwareRegistry.put(sessionData.getIdAgent(), sessionData);
            needsFullSync.remove(connectionId);
        }

        // Deliver Task Result if Agent completed a previous execution
        if (agentData.getTaskId() != null && agentData.getResult() != null) {
            System.out.println("[Gateway] Task result delivered for Task: " + agentData.getTaskId());
            // Hand off result to Matchmaker Service (Handles live user vs. orphaned user connection)
            matchmakerService.completeTask(agentData.getTaskId(), agentData.getResult());
        }

        // Long Polling Loop via TaskMatchmakerService (120 Seconds)
        long startTime = System.currentTimeMillis();
        long pollTimeout = 120000;
        //Loops for up to 30 seconds to hold the outbound HTTP request ope

        while (System.currentTimeMillis() - startTime < pollTimeout) {
            // Register agent in FIFO queue & check for paired task
            AgentTask assignedTask = matchmakerService.registerAgentAndPoll(connectionId);

            if (assignedTask != null) {
                System.out.println("[Server] Dispatching task [" + assignedTask.getId() + "] to Agent (" + connectionId + ")");
                return ResponseEntity.ok(assignedTask.getId() + "|||" + assignedTask.getPrompt());
            }

            Thread.sleep(500); // Internal memory poll frequency
        }

        // Timeout: Unregister agent from current poll cycle
        matchmakerService.unregisterAgent(connectionId);
        return ResponseEntity.ok("WAIT_NO_TASKS_AVAILABLE");
    }

    // 4. Invalidation Endpoint
    @PostMapping("/api/invalidate")
    public ResponseEntity<Map<String, String>> handleAgentInvalidate(@RequestBody Map<String, String> request) {
        String connectionId = request.get("connectionId");

        if (connectionId == null || !activeSessions.containsKey(connectionId)) {
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "ERROR_INVALID_CONNECTION_ID"));
        }

        needsFullSync.put(connectionId, true); // Flag for full spec update on next /api/continue
        return ResponseEntity.ok(Map.of("status", "ACKNOWLEDGED"));
    }

    // 5. Retrieve Orphaned Result Endpoint
    @GetMapping("/result/{taskId}")
    public ResponseEntity<String> getOrphanedResult(@PathVariable String taskId) {
        String result = matchmakerService.retrieveOrphanedResult(taskId);

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(404).body("Result not found or expired from cache.");
        }
    }
}