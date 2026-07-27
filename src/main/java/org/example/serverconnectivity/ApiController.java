package org.example.serverconnectivity;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@CrossOrigin(origins = "*")
@RestController
public class ApiController {

    // all maps :
    // one for received tasks
    // one for results sent from the agent waiting to get set
    // one agent's data

    // list for the received tasks form the user
    private final ConcurrentLinkedQueue<AgentTask> taskQueue = new ConcurrentLinkedQueue<>();
    // map tracking results for each unique task ID (ID -> Result String) that we get from the agent
    private final ConcurrentHashMap<String, String> resultsMap = new ConcurrentHashMap<>();

    // to save the agents (may delete later idk)
    //private final ConcurrentHashMap<String, CurrentState> agentRegistry = new ConcurrentHashMap<>();

    // Map to hold raw agent hardware metrics sent during initial connection from api/connect
    private final ConcurrentHashMap<String, AgentConnectRequest> agentHardwareRegistry = new ConcurrentHashMap<>();
    // Map linking connectionId -> AgentConnectRequest profile
    private final ConcurrentHashMap<String, AgentConnectRequest> activeSessions = new ConcurrentHashMap<>();


    private final Map<String, Boolean> needsFullSync = new ConcurrentHashMap<>();
    //private final ConcurrentHashMap<String, AgentSession> agentRegistry = new ConcurrentHashMap<>();
   // used before for init connectivity test , useless for now just kept for vibes
    @GetMapping("/api/hello")
    public String sayHello(@RequestParam(value = "name", defaultValue = "World") String name) {
        return String.format("Hello, %s! Your HTTP request successfully reached my TCP port!", name);
    }
    // receive the queries from the user
    @PostMapping("/api/ask")
    public String receiveUserQuery(@RequestBody String query) throws InterruptedException{

        System.out.println("[Gateway] Received query from User: " + query);
        // assign random long ahh id to the query
        String taskId = UUID.randomUUID().toString();

        // Add to queue and initialize the map with a pending status
        taskQueue.add(new AgentTask(taskId, query));
        resultsMap.put(taskId, "En attente de traitement...");

        // Wait only for THIS specific task ID's result to change ( this is for mono agent )
        int timeoutCounter = 0;
        while (resultsMap.get(taskId).equals("En attente de traitement...") && timeoutCounter < 30) {
            Thread.sleep(6000);
            timeoutCounter++;
        }

        // Retrieve the result, clean up the map, and return it to the user
        String finalResult = resultsMap.remove(taskId);
        return finalResult != null ? finalResult : "Request timed out."; // in case of a timeout we send a msg indicating so
    }

    // owo --------------------------------------------------------------------- owo //
    // owo ------------------------- connect API-------------------------------- owo //
    // owo --------------------------------------------------------------------- owo //

    @PostMapping("/api/connect")
    public ResponseEntity<Map<String, String>> handleAgentConnect(@RequestBody AgentConnectRequest connectData) {
        if (connectData.getIdAgent() == null || connectData.getIdAgent().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid Request: idAgent missing."));
        }
        String connectionId = "CONN-BWAHAHAHA";

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


        //String completedTaskId = agentData.getTaskId();
        //String agentResult = agentData.getResult();
        //String agentStatus = agentData.getStatus();

        System.out.println("--- Incoming Sync from Agent ---");
        System.out.println("[Gateway] Sync received from Connection [" + connectionId + "] " +
                "\nAgent: " + sessionData.getIdAgent() + "\nCPU: " + sessionData.getCpu() + "% \nGPU: " + sessionData.getGpu() + "%)");

        // when we receive a prompt response from the agent
        if (agentData.getTaskId() != null && agentData.getResult() != null) {
            System.out.println("[Gateway] Task result delivered for Task: " + agentData.getTaskId());
            resultsMap.put(agentData.getTaskId(), agentData.getResult());
        }



/*
        // 1. Process completed task result if delivered by agent
        if (agentResult != null && completedTaskId != null) {
            System.out.println("[Gateway] Agent [" + agentId + "] delivered result for Task: " + completedTaskId);
            System.out.println("[Gateway] Result content: " + agentResult);
            resultsMap.put(completedTaskId, agentResult);
        }




        // 2. Look up stored hardware profile from registry using idAgent
        AgentConnectRequest hardwareProfile = agentHardwareRegistry.get(agentId);

        if (hardwareProfile != null) {
            System.out.println("[Gateway] Agent checked in (" + agentId + "). " +
                    "VRAM: " + hardwareProfile.getVram() + " MB | CPU Load: " + hardwareProfile.getCpu() + "%");
        } else {
            System.out.println("[Gateway] Warning: Unregistered agent checked in: " + agentId);
        }

*/
        // 3. LONG POLLING WAIT LOOP (Wait up to 30s for a task in the queue)
        int serverWaitCounter = 0;
        while (taskQueue.isEmpty() && serverWaitCounter < 30) {
            Thread.sleep(1000);
            serverWaitCounter++;
        }

        // 4. Dispatch task or return timeout signal
        AgentTask nextTask = taskQueue.poll();

        if (nextTask != null) {
            System.out.println("[Server] Dispatching task [" + nextTask.id + "] to Agent (" + connectionId + "): " + nextTask.prompt);
            // Returns "taskId|||prompt" string as expected by client simulator
            return ResponseEntity.ok(nextTask.id + "|||" + nextTask.prompt);
        } else {
            System.out.println("[Server] Long poll timed out for Agent (" + connectionId + "). Queue empty.");
            return ResponseEntity.ok("WAIT_NO_TASKS_AVAILABLE");
        }
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

        // logging the notification event
        System.out.println("=== AGENT STATE INVALIDATED NOTIFICATION (/api/invalidate) ===");
        System.out.println("[Gateway] Invalidation event received for Connection [" + connectionId + "] (Agent: " + agentId + ").");

        // sends a notification to the agent to ack the invalidation
        return ResponseEntity.ok(Map.of("status", "ACKNOWLEDGED"));    }

    // owo --------------------------------------------------------------------- owo //
    // owo ----------------------- invalidate API ------------------------------ owo //
    // owo --------------------------------------------------------------------- owo //


}

