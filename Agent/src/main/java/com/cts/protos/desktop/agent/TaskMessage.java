package com.cts.protos.desktop.agent;

public class TaskMessage {
    private String idPrompt;
    private String prompt;
    private String connectionId;
    private String nameModel;
    private String result;

    public String getIdPrompt() { return idPrompt; }
    public void setIdPrompt(String idPrompt) { this.idPrompt = idPrompt; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getNameModel() { return nameModel; }
    public void setNameModel(String nameModel) { this.nameModel = nameModel; }
    public void setAgentId(String agentId) { this.connectionId = agentId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
}
