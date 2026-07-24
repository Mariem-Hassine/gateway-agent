package org.example.serverconnectivity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class StatusRequest {
    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public StatusRequest() {
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getVram() {
        return vram;
    }

    public void setVram(Integer vram) {
        this.vram = vram;
    }

    public Double getCpu() {
        return cpu;
    }

    public void setCpu(Double cpu) {
        this.cpu = cpu;
    }

    public Double getMaxCpu() {
        return maxCpu;
    }

    public void setMaxCpu(Double maxCpu) {
        this.maxCpu = maxCpu;
    }

    public Double getGpu() {
        return gpu;
    }

    public void setGpu(Double gpu) {
        this.gpu = gpu;
    }

    public Double getMaxGpu() {
        return maxGpu;
    }

    public void setMaxGpu(Double maxGpu) {
        this.maxGpu = maxGpu;
    }

    public Double getDiskUsage() {
        return diskUsage;
    }

    public void setDiskUsage(Double diskUsage) {
        this.diskUsage = diskUsage;
    }

    public List<String> getModelsInVRAM() {
        return modelsInVRAM;
    }

    public void setModelsInVRAM(List<String> modelsInVRAM) {
        this.modelsInVRAM = modelsInVRAM;
    }

    public List<String> getAvailableModels() {
        return availableModels;
    }

    public void setAvailableModels(List<String> availableModels) {
        this.availableModels = availableModels;
    }

    //private String status;
    private String connectionId;
    private String result;
    @JsonProperty("task_id")
    private String taskId;// Added to match the Agent's loop data
    @JsonAlias({"agentId", "pc_id", "agent_id", "pcId","idAgent"})
    private Integer vram;
    private Double cpu;
    private Double maxCpu;
    private Double gpu;
    private Double maxGpu;
    private Double diskUsage;
    private List<String> modelsInVRAM;
    private List<String> availableModels;
}