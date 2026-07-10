package org.example.serverconnectivity;

public class StatusRequest {
    private String status;

    // Default constructor is required by Jackson for JSON deserialization
    public StatusRequest() {
    }

    public StatusRequest(String status) {
        this.status = status;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}