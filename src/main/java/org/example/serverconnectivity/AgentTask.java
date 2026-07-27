package org.example.serverconnectivity;

public class AgentTask {
    String id;
    String prompt;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

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
