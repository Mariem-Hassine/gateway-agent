package org.example.serverconnectivity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AgentConnectRequest {

    @JsonProperty("pc_id")
    private String pcId;

    private String status;

    @JsonProperty("total_vram_capacity")
    private Long totalVramCapacity;

    @JsonProperty("vram_available")
    private Long vramAvailable;

    @JsonProperty("cpu_cores")
    private Integer cpuCores;

    @JsonProperty("free_disk_space_bytes")
    private Long freeDiskSpaceBytes;

    @JsonProperty("total_disk_space_bytes")
    private Long totalDiskSpaceBytes;

    public AgentConnectRequest() {}

    // Getters and Setters
    public String getPcId() { return pcId; }
    public void setPcId(String pcId) { this.pcId = pcId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getTotalVramCapacity() { return totalVramCapacity; }
    public void setTotalVramCapacity(Long totalVramCapacity) { this.totalVramCapacity = totalVramCapacity; }

    public Long getVramAvailable() { return vramAvailable; }
    public void setVramAvailable(Long vramAvailable) { this.vramAvailable = vramAvailable; }

    public Integer getCpuCores() { return cpuCores; }
    public void setCpuCores(Integer cpuCores) { this.cpuCores = cpuCores; }

    public Long getFreeDiskSpaceBytes() { return freeDiskSpaceBytes; }
    public void setFreeDiskSpaceBytes(Long freeDiskSpaceBytes) { this.freeDiskSpaceBytes = freeDiskSpaceBytes; }

    public Long getTotalDiskSpaceBytes() { return totalDiskSpaceBytes; }
    public void setTotalDiskSpaceBytes(Long totalDiskSpaceBytes) { this.totalDiskSpaceBytes = totalDiskSpaceBytes; }
}