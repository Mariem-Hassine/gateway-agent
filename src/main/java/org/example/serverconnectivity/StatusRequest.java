package org.example.serverconnectivity;

public class StatusRequest {
    private String status;
    private String result;
    private String taskId;// Added to match the Agent's loop data

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }



    public StatusRequest() {}

    public String getStatus() { return this.status; }
    public void setStatus(String status) { this.status = status; }

    public String getResult() { return this.result; }
    public void setResult(String result) { this.result = result; }
}