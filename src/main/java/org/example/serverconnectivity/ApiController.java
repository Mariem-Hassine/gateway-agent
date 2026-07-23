package org.example.serverconnectivity;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    //
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
    public ResponseEntity<String> handleAgentConnect(@RequestBody AgentConnectRequest connectData) {
        if (connectData.getIdAgent() == null || connectData.getIdAgent().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid Request: idAgent missing.");
        }

        // 2. Store agent specs in registry using idAgent
        agentHardwareRegistry.put(connectData.getIdAgent(), connectData);

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

        return ResponseEntity.ok("Connexion enregistrée avec succès.");
    }

    // owo --------------------------------------------------------------------- owo //
    // owo ------------------------- connect API-------------------------------- owo //
    // owo --------------------------------------------------------------------- owo //


    @PostMapping("/api/hello")
    public ResponseEntity<String> handleAgentSync(@RequestBody StatusRequest agentData) throws InterruptedException {

        String agentId = agentData.getIdAgent();
        String completedTaskId = agentData.getTaskId();
        String agentResult = agentData.getResult();
        //String agentStatus = agentData.getStatus();

        System.out.println("--- Incoming Sync from Agent ---");

        // Guard Clause: Ensure agentId is present
        if (agentId == null || agentId.trim().isEmpty()) {
            System.err.println("[Gateway] Rejecting sync: Missing idAgent in request payload.");
            return ResponseEntity.badRequest().body("ERROR_MISSING_ID_AGENT");
        }

        System.out.println("[Server] Agent ID: " + agentId );

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

        // 3. LONG POLLING WAIT LOOP (Wait up to 30s for a task in the queue)
        int serverWaitCounter = 0;
        while (taskQueue.isEmpty() && serverWaitCounter < 30) {
            Thread.sleep(1000);
            serverWaitCounter++;
        }

        // 4. Dispatch task or return timeout signal
        AgentTask nextTask = taskQueue.poll();

        if (nextTask != null) {
            System.out.println("[Server] Dispatching task [" + nextTask.id + "] to Agent (" + agentId + "): " + nextTask.prompt);
            // Returns "taskId|||prompt" string as expected by client simulator
            return ResponseEntity.ok(nextTask.id + "|||" + nextTask.prompt);
        } else {
            System.out.println("[Server] Long poll timed out for Agent (" + agentId + "). Queue empty.");
            return ResponseEntity.ok("WAIT_NO_TASKS_AVAILABLE");
        }
    }
}

