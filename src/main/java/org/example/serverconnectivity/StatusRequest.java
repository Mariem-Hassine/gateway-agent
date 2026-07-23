package org.example.serverconnectivity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class StatusRequest {
    //private String status;
    private String result;
    @JsonProperty("task_id")
    private String taskId;// Added to match the Agent's loop data
    @JsonAlias({"agentId", "pc_id", "agent_id", "pcId","idAgent"})
    private String idAgent;

    public String getIdAgent() {
        return idAgent;
    }

    public void setAgentId(String agentId) {
        this.idAgent = agentId;
    }
/*
    public CurrentState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(CurrentState currentState) {
        this.currentState = currentState;
    }

    private CurrentState currentState;
*/
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }



    public StatusRequest() {}

   // public String getStatus() { return this.status; }
    //public void setStatus(String status) { this.status = status; }

    public String getResult() { return this.result; }
    public void setResult(String result) { this.result = result; }
}