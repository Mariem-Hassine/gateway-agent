package org.example.serverconnectivity.service;

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

    public CompletableFuture<String> submitUserQuery(String requestedModel, String prompt) {
        String taskId = "TASK-" + UUID.randomUUID().toString().substring(0, 8);
        CompletableFuture<String> future = new CompletableFuture<>();

        pendingRequests.put(taskId, future);

        // Attach both requestedModel and prompt to the RabbitMQ message
        jmsTemplate.send("task.dispatch.queue", session -> {
            var msg = session.createTextMessage(prompt);
            msg.setStringProperty("taskId", taskId);
            msg.setStringProperty("requestedModel", requestedModel != null ? requestedModel : "default");
            return msg;
        });

        return future;
    }

    public String pollNextTask() {
        jmsTemplate.setReceiveTimeout(15000L);
        jakarta.jms.Message message = jmsTemplate.receive("task.dispatch.queue");

        if (message instanceof TextMessage textMessage) {
            try {
                String taskId = textMessage.getStringProperty("taskId");
                String requestedModel = textMessage.getStringProperty("requestedModel");
                String prompt = textMessage.getText();

                // Format sent to agent: taskId|||requestedModel|||prompt
                return taskId  + "|||" + prompt+ "|||" + requestedModel;
            } catch (Exception e) {
                System.err.println("[TaskDispatchService] Error reading message: " + e.getMessage());
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