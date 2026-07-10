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
    @PostMapping("/api/status")
    public String receiveRequest(@RequestBody StatusRequest clientData) {
        // Grab the status string sent by the client simulation
        String currentStatus = clientData.getStatus();

        // Log it to your OpenSUSE terminal so you can see it arrive
        System.out.println("Received status from client: " + currentStatus);

        // Send a plain text confirmation back to your curl or client script
        return "Server received your status! Current state is now: " + currentStatus;
    }
}