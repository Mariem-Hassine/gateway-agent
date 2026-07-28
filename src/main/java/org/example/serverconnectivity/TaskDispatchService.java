package org.example.serverconnectivity;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
@Service
// asynchronous bridge between incoming HTTP user
// requests and agents.

// It receives a prompt from a user
// generates a unique tracking ID
// sends the request to RabbitMQ via JMS
// and holds the HTTP thread open asynchronously
// until an agent finishes the job and posts the answer back to the result queue

public class TaskDispatchService {
    // Spring’s core helper class for producing and sending JMS messages
    // It handles opening/closing connections automatically.
    private final JmsTemplate jmsTemplate;
    // stores the pending requests: waiting for the agent's response
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();

    public TaskDispatchService(JmsTemplate jmsTemplate) {

        this.jmsTemplate = jmsTemplate;
    }

    // DISPATCH TASK TO AGENTS
    public CompletableFuture<String> submitUserQuery(String prompt) {
        String taskId = "TASK-" + UUID.randomUUID().toString().substring(0, 8);
        // creates an unfulfilled CompletableFuture
        CompletableFuture<String> future = new CompletableFuture<>();

        pendingRequests.put(taskId, future);

        // Send via pure JMS API — JmsTemplate automatically routes through RabbitMQ driver
        // task.dispatch.queue is the name of the queue (yeah ns)
        // will get sended to the msg broker
        jmsTemplate.send("task.dispatch.queue", session -> {
            // Payload
            var msg = session.createTextMessage(prompt);
            // attaches the tracking ID directly to the message headers
            msg.setStringProperty("taskId", taskId);
            return msg;
        });

        return future;
    }

    // RECEIVE RESULT FROM AGENTS
    // atomatically triggers whenever an agent finishes processing
    // and publishes a payload to "task.result.queue"
    @JmsListener(destination = "task.result.queue")
    public void onResultReceived(jakarta.jms.Message message) throws Exception {
        String taskId = message.getStringProperty("taskId");
        String resultPayload = ((jakarta.jms.TextMessage) message).getText();

        CompletableFuture<String> future = pendingRequests.remove(taskId);
        if (future != null) {
            future.complete(resultPayload);
        }
    }
}
