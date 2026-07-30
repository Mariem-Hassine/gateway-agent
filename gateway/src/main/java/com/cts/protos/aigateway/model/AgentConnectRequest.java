package com.cts.protos.aigateway.model;
import java.util.List;
import java.util.Map;

public class AgentConnectRequest {

    // never changes
    private String idAgent;

    public String getIdAgent() {
        return idAgent;
    }

    public void setIdAgent(String idAgent) {
        this.idAgent = idAgent;
    }

    public long getVram() {
        return vram;
    }

    public void setVram(long vram) {
        this.vram = vram;
    }

    public Double getCpu() {
        return cpu;
    }

    public void setCpu(Double cpu) {
        this.cpu = cpu;
    }

    public Double getGpu() {
        return gpu;
    }

    public void setGpu(Double gpu) {
        this.gpu = gpu;
    }

    public List<String> getModelsInVRAM() {
        return modelsInVRAM;
    }

    public void setModelsInVRAM(List<String> modelsInVRAM) {
        this.modelsInVRAM = modelsInVRAM;
    }

    public Double getDiskUsage() {
        return diskUsage;
    }

    public void setDiskUsage(Double diskUsage) {
        this.diskUsage = diskUsage;
    }

    public Double getMaxGpu() {
        return maxGpu;
    }

    public void setMaxGpu(Double maxGpu) {
        this.maxGpu = maxGpu;
    }

    public Double getMaxCpu() {
        return maxCpu;
    }

    public void setMaxCpu(Double maxCpu) {
        this.maxCpu = maxCpu;
    }

    public List<String> getAvailableModels() {
        return availableModels;
    }

    public void setAvailableModels(List<String> availableModels) {
        this.availableModels = availableModels;
    }

    public Map<String, Long> getDiskPartitions() {
        return diskPartitions;
    }

    public void setDiskPartitions(Map<String, Long> diskPartitions) {
        this.diskPartitions = diskPartitions;
    }

    // always changes
    private long vram;
    private Double cpu;
    private Double gpu;
    private List<String> modelsInVRAM;
    private Double diskUsage;

    // rarely changes
    private Double maxGpu;
    private Double maxCpu;
    private List<String> availableModels;
    private Map<String, Long> diskPartitions; // Key: Partition (e.g. "C:"), Value: Size in MB





    public AgentConnectRequest() {}

}