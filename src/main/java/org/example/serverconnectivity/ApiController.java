package org.example.serverconnectivity;

import org.springframework.web.bind.annotation.*;

@RestController
public class ApiController {

    // This exposes a GET endpoint at http://localhost:8080/api/hello
    @GetMapping("/api/hello")
    public String sayHello(@RequestParam(value = "name", defaultValue = "World") String name) {
        return String.format("Hello, %s! Your HTTP request successfully reached my TCP port!", name);
    }
    // This method handles the POST simulation request
    @PostMapping("/api/hello")
    public String receiveRequest(@RequestBody StatusRequest clientData) {
        String currentStatus = clientData.getStatus();
        String ollamaResult = clientData.getResult();

        System.out.println("--- Incoming Sync from Agent ---");
        System.out.println("[Server] Agent Status: " + currentStatus);

        if (ollamaResult != null) {
            System.out.println("[Server] Agent sent Ollama Result: " + ollamaResult);
        }

        // Decide what task to give to the Llama3 model next
        // This return string becomes the 'task' variable inside your Agent's code!
        String nextTaskForOllama = "what is the capital of Tunisia?";

        System.out.println("[Server] Dispatching next task to Agent: " + nextTaskForOllama);
        return nextTaskForOllama;
    }
}