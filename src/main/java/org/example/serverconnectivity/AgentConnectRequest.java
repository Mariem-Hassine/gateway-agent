package org.example.serverconnectivity;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    public float getCpu() {
        return cpu;
    }

    public void setCpu(float cpu) {
        this.cpu = cpu;
    }

    public float getGpu() {
        return gpu;
    }

    public void setGpu(float gpu) {
        this.gpu = gpu;
    }

    public List<String> getModelsInVRAM() {
        return modelsInVRAM;
    }

    public void setModelsInVRAM(List<String> modelsInVRAM) {
        this.modelsInVRAM = modelsInVRAM;
    }

    public float getDiskUsage() {
        return diskUsage;
    }

    public void setDiskUsage(float diskUsage) {
        this.diskUsage = diskUsage;
    }

    public float getMaxGpu() {
        return maxGpu;
    }

    public void setMaxGpu(float maxGpu) {
        this.maxGpu = maxGpu;
    }

    public float getMaxCpu() {
        return maxCpu;
    }

    public void setMaxCpu(float maxCpu) {
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
    private float cpu;
    private float gpu;
    private List<String> modelsInVRAM;
    private float diskUsage;

    // rarely changes
    private float maxGpu;
    private float maxCpu;
    private List<String> availableModels;
    private Map<String, Long> diskPartitions; // Key: Partition (e.g. "C:"), Value: Size in MB





    public AgentConnectRequest() {}

}