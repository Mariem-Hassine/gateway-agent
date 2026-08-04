package org.example.serverconnectivity.service;

import org.example.serverconnectivity.model.AgentConnectRequest;
import org.example.serverconnectivity.model.UserQueryRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AgentCommunication {

    private final Map<String, Boolean> needsFullSync = new ConcurrentHashMap<>();

    // Map to hold raw agent hardware metrics sent during initial connection from api/connect
    private final ConcurrentHashMap<String, AgentConnectRequest> agentHardwareRegistry = new ConcurrentHashMap<>();
    // Map linking connectionId -> AgentConnectRequest profile
    private final ConcurrentHashMap<String, AgentConnectRequest> activeSessions = new ConcurrentHashMap<>();

    public String sayHello(@RequestParam(value = "name", defaultValue = "World") String name) {
        return String.format("Hello, %s! Your HTTP request successfully reached my TCP port!", name);
    }

    public CompletableFuture<ResponseEntity<String>> receiveUserQuery(@RequestBody UserQueryRequest query) throws InterruptedException{

        System.out.println("[Gateway] Received query from User: " + query.getQuery()+ "' for Model: " + query.getModel());


        // Submit prompt to JMS Queue and handle asynchrono
        return taskDispatchService.submitUserQuery(query.getModel(), query.getQuery())
                .orTimeout(180, TimeUnit.SECONDS)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> ResponseEntity.status(504).body("Request timed out waiting for an agent."));
    }



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
        System.out.println("[Registry] CPU Current Load: " + connectData.getCpu() );
        System.out.println("[Registry] GPU Current Load: " + connectData.getGpu());
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

}
