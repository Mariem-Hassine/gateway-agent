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

    public Long getVram() {
        return vram;
    }

    public void setVram(Long vram) {
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
    public Long getRam() {
        return maxRam;
    }

    public void setMaxRam(Long maxRam) {
        this.maxRam = maxRam;
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
    private Long vram;
    private Double cpu;
    private Double gpu;
    private List<String> modelsInVRAM;
    private Double diskUsage;
    private Long maxRam ;

    // rarely changes

    private List<String> availableModels;
    private Map<String, Long> diskPartitions; // Key: Partition (e.g. "C:"), Value: Size in MB





    public AgentConnectRequest() {}

}