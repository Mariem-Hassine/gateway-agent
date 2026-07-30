package com.cts.protos.desktop.agent.service;

import com.sun.management.OperatingSystemMXBean;
import net.thevpc.nuts.platform.NEnv;
import net.thevpc.nuts.platform.NGpu;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OsResources {
    OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    public int availableProcessors(){
        return Runtime.getRuntime().availableProcessors();
    }

    public Map<String, Long> diskPartitions(){
        Map<String, Long> diskPartitions = new HashMap<>();
        java.io.File[] racines = java.io.File.listRoots();

        if (racines != null) {
            for (java.io.File racine : racines) {
                if (racine.exists()) {
                    long totalGB = racine.getTotalSpace() / (1024 * 1024 * 1024);
                    diskPartitions.put(racine.getAbsolutePath(), totalGB);
                }
            }
        }
        return diskPartitions;
    }

    public double cpuUsage(){
        // Vraie valeur CPU
        double cpuUsage = 0.0;
        try {
            double load = osBean.getCpuLoad();
            if (load >= 0) {
                cpuUsage = load * 100.0;//transformer une fraction en un pourcentage
            }
        } catch (Exception e) {
            cpuUsage = 0.0;
        }
        return cpuUsage;
    }

    public long totalVRam() {
        try {
            List<NGpu> gpus = NEnv.of().gpus();
            if (gpus != null && !gpus.isEmpty()) {
                long total = 0;
                for (NGpu gpu : gpus) {
                    total += gpu.vram().total();
                }
                return total;
            }
        } catch (Exception e) {
            System.err.println("[Agent] Erreur lecture GPU via Nuts : " + e.getMessage());
        }
        return 0; // fallback si Nuts ne détecte aucun GPU
    }

    public double getAvailableDiskSpace(String ollamaPath) {
        java.io.File folder = new java.io.File(ollamaPath);//file est un obj representant le chemin
        while (!folder.exists() && folder.getParentFile() != null) {
            folder = folder.getParentFile();
        }

        long freeSpaceBytes = folder.getFreeSpace();
        return freeSpaceBytes / (1024.0 * 1024 * 1024);
    }

}
