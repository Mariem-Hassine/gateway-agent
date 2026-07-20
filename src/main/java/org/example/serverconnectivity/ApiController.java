package org.example.serverconnectivity;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@CrossOrigin(origins = "*")
@RestController
public class ApiController {


    private final ConcurrentLinkedQueue<AgentTask> taskQueue = new ConcurrentLinkedQueue<>();
    // map tracking results for each unique task ID (ID -> Result String)
    private final ConcurrentHashMap<String, String> resultsMap = new ConcurrentHashMap<>();
    String latestResult = "";
    // to save the agents
    private final ConcurrentHashMap<String, CurrentState> agentRegistry = new ConcurrentHashMap<>();


    public String getLatestResult() {
        return latestResult;
    }

    public void setLatestResult(String latestResult) {
        this.latestResult = latestResult;
    }



    @GetMapping("/api/hello")
    public String sayHello(@RequestParam(value = "name", defaultValue = "World") String name) {
        return String.format("Hello, %s! Your HTTP request successfully reached my TCP port!", name);
    }

    @PostMapping("/api/ask")
    public String receiveUserQuery(@RequestBody String query) throws InterruptedException{
    //public String receiveUserQuery(@RequestParam(value = "query")String query) throws InterruptedException
        System.out.println("[Gateway] Received query from User: " + query);

        String taskId = UUID.randomUUID().toString();

        // Add to queue and initialize the map with a pending status
        taskQueue.add(new AgentTask(taskId, query));
        resultsMap.put(taskId, "En attente de traitement...");

        // Wait only for THIS specific task ID's result to change
        int timeoutCounter = 0;
        while (resultsMap.get(taskId).equals("En attente de traitement...") && timeoutCounter < 30) {
            Thread.sleep(6000);
            timeoutCounter++;
        }

        // Retrieve the result, clean up the map, and return it to the user
        String finalResult = resultsMap.remove(taskId);
        return finalResult != null ? finalResult : "Request timed out."; // in case of a timeout we send a msg indicating so
    }
    // This method handles the POST simulation request
    @PostMapping("/api/hello")
    public String handleAgentSync(@RequestBody StatusRequest agentData) throws InterruptedException {
        String agentId = agentData.getAgentId();
        String agentStatus = agentData.getStatus();
        String ollamaResult = agentData.getResult();
        String completedTaskId = agentData.getTaskId();

        // add or update the agent
        // if already exists it will update the status
        if (agentId != null && agentData.getCurrentState() != null) {
            agentRegistry.put(agentId, agentData.getCurrentState());
            System.out.println("[Registry] Updated metrics for " + agentId
                    + " (Active Queries: " + agentData.getCurrentState().toString() + ")");
        }

        System.out.println("--- Incoming Sync from Agent ---");
        System.out.println("[Server] Agent Status: " + agentStatus);

        // If the agent brought back a completed result, store it under its specific ID
        if (ollamaResult != null && completedTaskId != null) {
            System.out.println("[Gateway] Agent delivered result for Task " + completedTaskId);
            System.out.println("[Gateway] Agent delivered the following result " + ollamaResult);
            resultsMap.put(completedTaskId, ollamaResult);
        }

        System.out.println("[Gateway] Agent checked in. Current status: " + agentStatus + "\t \n" + agentData.getCurrentState().toString());

        // ─── LONG POLLING WAIT LOOP (Checks the waiting list) ───
        int serverWaitCounter = 0;
        // Hold the agent's request open up to 30 seconds if the waiting list is emptyt
        while (taskQueue.isEmpty() && serverWaitCounter < 30) {
            Thread.sleep(1000);
            serverWaitCounter++;
        }

        // ─── TREAT THE NEXT PROMPT IN LINE ───
        // poll() automatically grabs and removes the OLDEST item at the front of the queue
        AgentTask nextTask = taskQueue.poll();

        if (nextTask != null) {
            System.out.println("[Server] Dispatching task [" + nextTask.id+ "] to Agent: " + nextTask.prompt);

            return  nextTask.id + "|||" + nextTask.prompt;
            // nextTask.id + "|||" + fazat il id raja3ha imbba3d
        } else {
            System.out.println("[Server] Long poll timed out. Waiting list is empty.");
            return "WAIT_NO_TASKS_AVAILABLE";
        }
    }
}
