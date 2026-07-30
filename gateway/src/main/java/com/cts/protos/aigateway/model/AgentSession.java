package com.cts.protos.aigateway.model;

public class AgentSession {
    private final String pcId;
    private final AgentConnectRequest hardwareSpecs;
    //private volatile String status; // "pret", "busy", "disconnected"
    private volatile long lastCheckInTimestamp;

    public String getPcId() {
        return pcId;
    }

    public AgentConnectRequest getHardwareSpecs() {
        return hardwareSpecs;
    }


    public long getLastCheckInTimestamp() {
        return lastCheckInTimestamp;
    }

    public void setLastCheckInTimestamp(long lastCheckInTimestamp) {
        this.lastCheckInTimestamp = lastCheckInTimestamp;
    }

    public AgentSession(AgentConnectRequest specs) {
        this.pcId = specs.getIdAgent();
        this.hardwareSpecs = specs;
        //this.status = specs.getStatus();
        this.lastCheckInTimestamp = System.currentTimeMillis();
    }




}