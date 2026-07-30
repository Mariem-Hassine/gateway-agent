package com.cts.protos.aigateway.model;

public class AgentTask {
    String id;
    String prompt;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public AgentTask(String id, String prompt) {
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
