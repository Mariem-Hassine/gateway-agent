package org.example.serverconnectivity;

public class AgentTask {
    String id;
    String prompt;

    AgentTask(String id, String prompt) {
        this.id = id;
        this.prompt = prompt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
