package com.cts.protos.desktop.agent.model;

import net.thevpc.nuts.util.NCopiable;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Payload implements NCopiable,Cloneable {
    private String agentId;
    private String connectionId;
    private String taskId = null;
    private String taskResult = null;
    private Long vram;
    private Double cpu;
    private Double gpu;
    private String[] loadedModels;
    private Double diskUsage;
    private Map<String, Long> diskPartitions;
    private Double maxCpu;
    private Double maxGpu;
    private Integer availableProcessors;
    private String[] availableModels;

    public String connectionId() {
        return connectionId;
    }

    public String taskId() {
        return taskId;
    }

    public Payload setTaskId(String taskId) {
        this.taskId = taskId;
        return this;
    }

    public String taskResult() {
        return taskResult;
    }

    public Payload setTaskResult(String taskResult) {
        this.taskResult = taskResult;
        return this;
    }

    public String[] loadedModels() {
        return loadedModels;
    }

    public Payload setConnectionId(String connectionId) {
        this.connectionId = connectionId;
        return this;
    }

    public String agentId() {
        return agentId;
    }

    public Payload setAgentId(String agentId) {
        this.agentId = agentId;
        return this;
    }

    public Long vram() {
        return vram;
    }

    public Payload setVram(Long vram) {
        this.vram = vram;
        return this;
    }

    public Double cpu() {
        return cpu;
    }

    public Payload setCpu(Double cpu) {
        this.cpu = cpu;
        return this;
    }

    public Double gpu() {
        return gpu;
    }

    public Payload setGpu(Double gpu) {
        this.gpu = gpu;
        return this;
    }

    public String[] modelsInVRAM() {
        return loadedModels;
    }

    public Payload setLoadedModels(String[] loadedModels) {
        this.loadedModels = loadedModels;
        return this;
    }

    public Double diskUsage() {
        return diskUsage;
    }

    public Payload setDiskUsage(Double diskUsage) {
        this.diskUsage = diskUsage;
        return this;
    }


    public Map<String, Long> diskPartitions() {
        return diskPartitions;
    }

    public Payload setDiskPartitions(Map<String, Long> diskPartitions) {
        this.diskPartitions = diskPartitions;
        return this;
    }

    public Double maxCpu() {
        return maxCpu;
    }

    public Payload setMaxCpu(Double maxCpu) {
        this.maxCpu = maxCpu;
        return this;
    }

    public Double maxGpu() {
        return maxGpu;
    }

    public Payload setMaxGpu(Double maxGpu) {
        this.maxGpu = maxGpu;
        return this;
    }

    public Integer availableProcessors() {
        return availableProcessors;
    }

    public Payload setAvailableProcessors(Integer availableProcessors) {
        this.availableProcessors = availableProcessors;
        return this;
    }

    public String[] availableModels() {
        return availableModels;
    }

    public Payload setAvailableModels(String[] availableModels) {
        this.availableModels = availableModels;
        return this;
    }

    @Override
    public Payload copy() {
        return clone();
    }

    @Override
    protected Payload clone() {
        Payload other = null;
        try {
            other = (Payload) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        if (other.availableModels != null) {
            other.availableModels= Arrays.copyOf(other.availableModels, other.availableModels.length);
        }
        if (other.loadedModels != null) {
            other.loadedModels= Arrays.copyOf(other.loadedModels, other.loadedModels.length);
        }
        if (other.diskPartitions != null) {
            other.diskPartitions= new HashMap<>(other.diskPartitions);
        }
        return other;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Payload payload = (Payload) o;
        return Objects.equals(agentId, payload.agentId) && Objects.equals(connectionId, payload.connectionId) && Objects.equals(taskId, payload.taskId) && Objects.equals(taskResult, payload.taskResult) && Objects.equals(vram, payload.vram) && Objects.equals(cpu, payload.cpu) && Objects.equals(gpu, payload.gpu) && Objects.deepEquals(loadedModels, payload.loadedModels) && Objects.equals(diskUsage, payload.diskUsage) && Objects.equals(diskPartitions, payload.diskPartitions) && Objects.equals(maxCpu, payload.maxCpu) && Objects.equals(maxGpu, payload.maxGpu) && Objects.equals(availableProcessors, payload.availableProcessors) && Objects.deepEquals(availableModels, payload.availableModels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentId, connectionId, taskId, taskResult, vram, cpu, gpu, Arrays.hashCode(loadedModels), diskUsage, diskPartitions, maxCpu, maxGpu, availableProcessors, Arrays.hashCode(availableModels));
    }
}
