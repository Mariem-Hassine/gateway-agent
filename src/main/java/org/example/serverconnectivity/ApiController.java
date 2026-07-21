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
    // one

    // list for the received tasks form the user
    private final ConcurrentLinkedQueue<AgentTask> taskQueue = new ConcurrentLinkedQueue<>();
    // map tracking results for each unique task ID (ID -> Result String) that we get from the agent
    private final ConcurrentHashMap<String, String> resultsMap = new ConcurrentHashMap<>();

    // to save the agents (may delete later idk)
    //private final ConcurrentHashMap<String, CurrentState> agentRegistry = new ConcurrentHashMap<>();

    // Map to hold raw agent hardware metrics sent during initial connection from api/connect
    private final ConcurrentHashMap<String, AgentConnectRequest> agentHardwareRegistry = new ConcurrentHashMap<>();


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

    @PostMapping("/api/connect")
    public ResponseEntity<String> handleAgentConnect(@RequestBody AgentConnectRequest connectData) {
        if (connectData.getPcId() == null || connectData.getPcId().isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid Request: pc_id missing.");
        }

        // Store agent specs in registry
        agentHardwareRegistry.put(connectData.getPcId(), connectData);

        System.out.println("=== AGENT REGISTERED (/api/connect) ===");
        System.out.println("[Registry] Agent: " + connectData.getPcId());
        System.out.println("[Registry] CPU Cores: " + connectData.getCpuCores());
        System.out.println("[Registry] Free VRAM: " + connectData.getVramAvailable() + " / " + connectData.getTotalVramCapacity());

        return ResponseEntity.ok("Connexion enregistrée avec succès.");
    }

    @PostMapping("/api/hello")
    public ResponseEntity<String> handleAgentSync(@RequestBody StatusRequest agentData) throws InterruptedException {

        String completedTaskId = agentData.getTaskId();
        String agentResult = agentData.getResult();
        String agentStatus = agentData.getStatus();

        System.out.println("--- Incoming Sync from Agent ---");
        System.out.println("[Server] Agent Status: " + agentStatus);

        // 1. Process completed task result from agent if present
        if (agentResult != null && completedTaskId != null) {
            System.out.println("[Gateway] Agent delivered result for Task " + completedTaskId);
            System.out.println("[Gateway] Result content: " + agentResult);
            resultsMap.put(completedTaskId, agentResult);
        }

        // Look up stored hardware details from the registry using pc_id
        AgentConnectRequest hardwareProfile = agentHardwareRegistry.get(agentData.getAgentId());

        // checks if the agent connected before or not
        if (hardwareProfile != null) {
            System.out.println("[Gateway] Agent checked in (" + agentData.getAgentId() + "). " +
                    "VRAM Available: " + hardwareProfile.getVramAvailable() + " bytes");
        } else {
            System.out.println("[Gateway] Warning: Unregistered agent checked in: " + agentData.getAgentId());
        }

        // 2. LONG POLLING WAIT LOOP (Wait up to 30s for a task in the queue) only if there is no queue is empty
        int serverWaitCounter = 0;
        while (taskQueue.isEmpty() && serverWaitCounter < 30) {
            Thread.sleep(1000);
            serverWaitCounter++;
        }

        // 3. Dispatch task or return timeout signal
        AgentTask nextTask = taskQueue.poll();

        if (nextTask != null) {
            System.out.println("[Server] Dispatching task [" + nextTask.id + "] to Agent: " + nextTask.prompt);
            // Returns "taskId|||prompt" string as expected by your current setup
            return ResponseEntity.ok(nextTask.id + "|||" + nextTask.prompt);
        } else {
            System.out.println("[Server] Long poll timed out. Waiting list is empty.");
            return ResponseEntity.ok("WAIT_NO_TASKS_AVAILABLE");
        }
    }
}

