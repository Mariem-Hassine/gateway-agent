package org.example.serverconnectivity;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import jakarta.jms.TextMessage;

//@Component
public class AgentWorkerListener {

    private final JmsTemplate jmsTemplate;

    public AgentWorkerListener(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    //@JmsListener(destination = "task.dispatch.queue")
    public void processTask(TextMessage message) throws Exception {
        String taskId = message.getStringProperty("taskId");
        String prompt = message.getText();

        System.out.println("[Agent Node] Executing Task ID: " + taskId + " with prompt: " + prompt);

        // Simulate agent processing output
        String resultText = "Executed agent response for prompt: " + prompt;

        // Send completed answer back through JMS
        jmsTemplate.send("task.result.queue", session -> {
            var reply = session.createTextMessage(resultText);
            reply.setStringProperty("taskId", taskId);
            return reply;
        });
    }
}