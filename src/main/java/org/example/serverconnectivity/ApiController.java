package org.example.serverconnectivity;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@CrossOrigin(origins = "*")
@RestController
public class ApiController {
    private final TaskDispatchService taskDispatchService;

    // Registries for Agent Metrics and Active Sessions

    private final Map<String, Boolean> needsFullSync = new ConcurrentHashMap<>();

    // all maps :
    // one for received tasks
    // one for results sent from the agent waiting to get set
    // one agent's data

    // list for the received tasks form the user :  OLD CODE
    private final ConcurrentLinkedQueue<AgentTask> taskQueue = new ConcurrentLinkedQueue<>();
    // map tracking results for each unique task ID (ID -> Result String) that we get from the agent
    // OLD CODE
    private final ConcurrentHashMap<String, String> resultsMap = new ConcurrentHashMap<>();

    // to save the agents (may delete later idk)
    //private final ConcurrentHashMap<String, CurrentState> agentRegistry = new ConcurrentHashMap<>();

    // Map to hold raw agent hardware metrics sent during initial connection from api/connect
    private final ConcurrentHashMap<String, AgentConnectRequest> agentHardwareRegistry = new ConcurrentHashMap<>();
    // Map linking connectionId -> AgentConnectRequest profile
    private final ConcurrentHashMap<String, AgentConnectRequest> activeSessions = new ConcurrentHashMap<>();




    public ApiController(TaskDispatchService taskDispatchService) {
        this.taskDispatchService = taskDispatchService;
    }

    //private final ConcurrentHashMap<String, AgentSession> agentRegistry = new ConcurrentHashMap<>();
   // used before for init connectivity test , useless for now just kept for vibes
    @GetMapping("/api/hello")
    public String sayHello(@RequestParam(value = "name", defaultValue = "World") String name) {
        return String.format("Hello, %s! Your HTTP request successfully reached my TCP port!", name);
    }

    // owo --------------------------------------------------------------------- owo //
    // owo ------------------------ user query API------------------------------ owo //
    // owo --------------------------------------------------------------------- owo //
    // receive the queries from the user
    @PostMapping("/api/ask")
    public CompletableFuture<ResponseEntity<String>> receiveUserQuery(@RequestBody String query) throws InterruptedException{

        System.out.println("[Gateway] Received query from User: " + query);


        // Submit prompt to JMS Queue and handle asynchronous completion
        return taskDispatchService.submitUserQuery(query)
                .orTimeout(60, TimeUnit.SECONDS) // Return 504 if no agent responds within 60s
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.status(504).body("Request timed out waiting for an agent."));
    }
    // owo --------------------------------------------------------------------- owo //
    // owo ------------------------ user query API------------------------------ owo //
    // owo --------------------------------------------------------------------- owo //



    // owo --------------------------------------------------------------------- owo //
    // owo ------------------------- connect API-------------------------------- owo //
    // owo --------------------------------------------------------------------- owo //

    @PostMapping("/api/connect")
    public ResponseEntity<Map<String, String>> handleAgentConnect(@RequestBody AgentConnectRequest connectData) {
        if (connectData.getIdAgent() == null || connectData.getIdAgent().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid Request: idAgent missing."));
        }
        String connectionId = "CONN-" + connectData.getIdAgent(); // keeping it simple for no
        // TODO replace this with a token ya ta3 cyber ya kali linux

        // store agent specs in registry using idAgent
        // btw the connectData has agentId so this registory have the id in the key and the value
        // but idk unless there will be a probleme

        agentHardwareRegistry.put(connectData.getIdAgent(), connectData);
        // store the active sessions
        activeSessions.put(connectionId, connectData);

        // 3. Log updated specs with new attributes and collection formats
        System.out.println("=== AGENT REGISTERED (/api/connect) ===");
        System.out.println("[Registry] Agent ID: " + connectData.getIdAgent());
        System.out.println("[Registry] VRAM: " + connectData.getVram() + " MB");
        System.out.println("[Registry] CPU Current Load: " + connectData.getCpu() + "% / Max: " + connectData.getMaxCpu() + "%");
        System.out.println("[Registry] GPU Current Load: " + connectData.getGpu() + "% / Max: " + connectData.getMaxGpu() + "%");
        System.out.println("[Registry] Disk Usage: " + connectData.getDiskUsage() + "%");

        // Logging Collections (Lists & Map)
        if (connectData.getModelsInVRAM() != null) {
            System.out.println("[Registry] Models in VRAM: " + String.join(", ", connectData.getModelsInVRAM()));
        }

        if (connectData.getAvailableModels() != null) {
            System.out.println("[Registry] Available Models: " + connectData.getAvailableModels().size() + " total");
        }

        if (connectData.getDiskPartitions() != null) {
            System.out.println("[Registry] Partitions: " + connectData.getDiskPartitions());
        }
        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("status", "ok");
        responseBody.put("connectionId", connectionId);

        // Fixed: Returning responseBody map so Spring converts it to JSON!
        return ResponseEntity.ok(responseBody);
    }

    // owo --------------------------------------------------------------------- owo //
    // owo ------------------------- connect API-------------------------------- owo //
    // owo --------------------------------------------------------------------- owo //




    // owo --------------------------------------------------------------------- owo //
    // owo ------------------------ continue API ------------------------------- owo //
    // owo --------------------------------------------------------------------- owo //

    @PostMapping("/api/continue")
    public ResponseEntity<String> handleAgentSync(@RequestBody StatusRequest agentData) throws InterruptedException {
        String connectionId = agentData.getConnectionId();


        // validate that the conx id exist in registred connections
        if (connectionId == null || !activeSessions.containsKey(connectionId)) {
            System.err.println("[Gateway] Rejecting sync: Invalid or unassigned connectionId [" + connectionId + "].");
            return ResponseEntity.status(401).body("ERROR_INVALID_CONNECTION_ID");
        }


        AgentConnectRequest sessionData = activeSessions.get(connectionId);


        if (agentData.getCpu() != null) sessionData.setCpu(agentData.getCpu());
        if (agentData.getGpu() != null) sessionData.setGpu(agentData.getGpu());
        if (agentData.getVram() != null) sessionData.setVram(agentData.getVram());
        if (agentData.getDiskUsage() != null) sessionData.setDiskUsage(agentData.getDiskUsage());
        if (agentData.getModelsInVRAM() != null) sessionData.setModelsInVRAM(agentData.getModelsInVRAM());



        // checks if the invalidate flag has be raised
        if (Boolean.TRUE.equals(needsFullSync.get(connectionId))) {
            System.out.println("\n=== REFRESHING AGENT CHARACTERISTICS (/api/continue) ===");
            System.out.println("[Gateway] will make an update or smth idk " );
            if (agentData.getAvailableModels() != null) {
                sessionData.setAvailableModels(agentData.getAvailableModels());
                System.out.println("[Gateway] Updated Available Models (" + agentData.getAvailableModels().size() + " total): " + agentData.getAvailableModels());
            }
            if (agentData.getMaxCpu() != null) {
                sessionData.setMaxCpu(agentData.getMaxCpu());
                System.out.println("[Gateway] Updated Max CPU: " + agentData.getMaxCpu() + "%");
            }
            if (agentData.getMaxGpu() != null) {
                sessionData.setMaxGpu(agentData.getMaxGpu());
                System.out.println("[Gateway] Updated Max GPU: " + agentData.getMaxGpu() + "%");
            }
            if (agentData.getDiskPartitions() != null) {
                sessionData.setDiskPartitions(agentData.getDiskPartitions());
                System.out.println("[Gateway] Updated Disk Partitions: " + agentData.getDiskPartitions());
            }

            // Sync changes back to hardware registry
            agentHardwareRegistry.put(sessionData.getIdAgent(), sessionData);

            // Reset full sync flag
            needsFullSync.remove(connectionId);
            System.out.println("[Gateway] Full characteristics refresh completed successfully.\n");

        }

        System.out.println("--- Incoming Sync from Agent ---");
        System.out.println("[Gateway] Sync received from Connection [" + connectionId + "] " +
                "\nAgent: " + sessionData.getIdAgent() + "\nCPU: " + sessionData.getCpu() + "% \nGPU: " + sessionData.getGpu() + "%)");





        System.out.println("[Gateway] Sync acknowledged for Agent [" + sessionData.getIdAgent() + "]");

        // Return instant response — tasks are received asynchronously via RabbitMQ/JMS
        return ResponseEntity.ok("SYNC_ACKNOWLEDGED");
    }
    // owo --------------------------------------------------------------------- owo //
    // owo ----------------------- invalidate API ------------------------------ owo //
    // owo --------------------------------------------------------------------- owo //
    @PostMapping("/api/invalidate")
    public ResponseEntity<Map<String, String>> handleAgentInvalidate(@RequestBody Map<String, String> request) {
        String connectionId = request.get("connectionId");

        // 1. Validate that the connection ID exists in active sessions
        if (connectionId == null || !activeSessions.containsKey(connectionId)) {
            System.err.println("[Gateway] Rejecting invalidation: Invalid or unassigned connectionId [" + connectionId + "].");
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "ERROR_INVALID_CONNECTION_ID"));
        }
        // get the agent id through the conx id from the active sessions map
        String agentId = activeSessions.get(connectionId).getIdAgent();
        needsFullSync.put(connectionId, true);

        // logging the notification event
        System.out.println("=== AGENT STATE INVALIDATED NOTIFICATION (/api/invalidate) ===");
        System.out.println("[Gateway] Invalidation event received for Connection [" + connectionId + "] (Agent: " + agentId + ").");

        // sends a notification to the agent to ack the invalidation
        return ResponseEntity.ok(Map.of("status", "ACKNOWLEDGED"));    }

  /*  @PostMapping("/api/invalidate")
    public ResponseEntity<Map<String, String>> handleAgentInvalidate(@RequestBody Map<String, String> request) {
        String connectionId = request.get("connectionId");

        // 1. Validate that the connection ID exists in active sessions
        if (connectionId == null || !activeSessions.containsKey(connectionId)) {
            System.err.println("[Gateway] Rejecting invalidation: Invalid or unassigned connectionId [" + connectionId + "].");
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "ERROR_INVALID_CONNECTION_ID"));
        }
        // get the agent id through the conx id from the active sessions map
        String agentId = activeSessions.get(connectionId).getIdAgent();
        needsFullSync.put(connectionId, true);

        // logging the notification event
        System.out.println("=== AGENT STATE INVALIDATED NOTIFICATION (/api/invalidate) ===");
        System.out.println("[Gateway] Invalidation event received for Connection [" + connectionId + "] (Agent: " + agentId + ").");

        // sends a notification to the agent to ack the invalidation
        return ResponseEntity.ok(Map.of("status", "ACKNOWLEDGED"));    }*/

    // owo --------------------------------------------------------------------- owo //
    // owo ----------------------- invalidate API ------------------------------ owo //
    // owo --------------------------------------------------------------------- owo //


}

