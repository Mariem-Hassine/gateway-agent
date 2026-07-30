package org.example.serverconnectivity;

import jakarta.jms.TextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskDispatchService {

    @Autowired
    private JmsTemplate jmsTemplate;

    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();

    public TaskDispatchService(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public CompletableFuture<String> submitUserQuery(String prompt) {
        String taskId = "TASK-" + UUID.randomUUID().toString().substring(0, 8);
        CompletableFuture<String> future = new CompletableFuture<>();

        pendingRequests.put(taskId, future);

        // Clean up the map when the future completes or times out
        future.whenComplete((result, ex) -> pendingRequests.remove(taskId));

        jmsTemplate.send("task.dispatch.queue", session -> {
            var msg = session.createTextMessage(prompt);
            msg.setStringProperty("taskId", taskId);
            return msg;
        });

        System.out.println("[Gateway] Query [ " + prompt + " ] dispatched with id : " + taskId);
        return future;
    }

    // --------------------------------------------------------------------- //
    // Helper Methods Called by Controller for /api/continue HTTP Agents
    // --------------------------------------------------------------------- //

    public String pollNextTask() {
        jmsTemplate.setReceiveTimeout(300000L); // 200ms non-blocking check
        jakarta.jms.Message message = jmsTemplate.receive("task.dispatch.queue");

        if (message instanceof TextMessage textMessage) {
            try {
                String taskId = textMessage.getStringProperty("taskId");
                String prompt = textMessage.getText();
                return taskId + "|||" + prompt; // Matches OpenAPI format
            } catch (Exception e) {
                System.err.println("[TaskDispatchService] Failed to parse message: " + e.getMessage());
            }
        }
        return null;
    }

    public void completeTask(String taskId, String result) {
        System.out.println("[TaskDispatchService] Attempting to complete taskId: " + taskId);
        CompletableFuture<String> future = pendingRequests.remove(taskId);
        if (future != null) {
            boolean completed = future.complete(result);
            System.out.println("[TaskDispatchService] Task " + taskId + " resolved successfully? " + completed);
            System.out.println("[TaskDispatchService] Task " + taskId + " result " + result);
        } else {
            System.out.println("[TaskDispatchService] Received result for " + taskId +
                    ", but the user HTTP session already timed out or expired.");
        }
    }

    @JmsListener(destination = "task.result.queue")
    public void onResultReceived(jakarta.jms.Message message) throws Exception {
        String taskId = message.getStringProperty("taskId");
        String resultPayload = ((jakarta.jms.TextMessage) message).getText();
        System.out.println("received result :)");

        CompletableFuture<String> future = pendingRequests.remove(taskId);
        if (future != null) {
            future.complete(resultPayload);
        }
    }
}