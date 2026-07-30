// ----------  NO LONGER USED REPLACED BY AGENTCONNECTREQUEST----------------------/

package com.cts.protos.aigateway.model;


public class CurrentState {
    public int activeQueries ;
    public double capacity ;
    public double CPUPower ;

    public CurrentState(){

    }

    public CurrentState(int activeQueries, double capacity, double CPUPower, String CPUOg) {
        this.activeQueries = activeQueries;
        this.capacity = capacity;
        this.CPUPower = CPUPower;
        this.CPUOg = CPUOg;
    }

    public String CPUOg ;

    public int getActiveQueries() {
        return activeQueries;
    }

    public void setActiveQueries(int activeQueries) {
        this.activeQueries = activeQueries;
    }

    public double getCapacity() {
        return capacity;
    }

    public void setCapacity(double capacity) {
        this.capacity = capacity;
    }

    public double getCPUPower() {
        return CPUPower;
    }

    public void setCPUPower(double CPUPower) {
        this.CPUPower = CPUPower;
    }

    @Override
    public String toString() {
        return "CurrentState{ " +
                "\nactiveQueries=" + activeQueries +
                "\ncapacity=" + capacity +
                "\nCPUPower=" + CPUPower +
                "\nCPUOg='" + CPUOg + '\'' +
                "\n}";
    }

    public String getCPUOg() {
        return CPUOg;
    }

    public void setCPUOg(String CPUOg) {
        this.CPUOg = CPUOg;
    }


}

