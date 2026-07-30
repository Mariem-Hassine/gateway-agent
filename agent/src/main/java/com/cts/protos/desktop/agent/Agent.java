package com.cts.protos.desktop.agent;

import com.cts.protos.desktop.agent.model.Payload;
import com.cts.protos.desktop.agent.service.GatewayConnector;
import com.cts.protos.desktop.agent.service.OllamaConnector;
import com.cts.protos.desktop.agent.service.OsResources;
import com.cts.protos.desktop.agent.model.TaskMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class Agent {

    //  Un seul client HTTP réutilisé par l'ensemble de l'agent

    private Payload lastRC = new Payload();
    private OllamaConnector ollamaConnector = new OllamaConnector();
    private OsResources osResources = new OsResources();
    private GatewayConnector gatewayConnector;

    //file d'attente pour stocker les taches recu du gateway
    private final LinkedBlockingQueue<TaskMessage> tasks = new LinkedBlockingQueue<>();
    // Identifiant unique de l'agent

    private String resolveAgentId() {
        try {
            String base = System.getProperty("user.name") + "@" + java.net.InetAddress.getLocalHost().getHostName();
            String suffixe = java.util.UUID.randomUUID().toString().substring(0, 8);
            return base + "-" + suffixe;
        } catch (Exception e) {
            return "unknown-host-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
    }

    public Agent() {
        gatewayConnector=new GatewayConnector(resolveAgentId());
    }

    public Payload buildAlwaysChangesPayload(Payload payload) {
        long vramDispo = ollamaConnector.obtenirVramDisponible();
        List<String> modelesVRAM = ollamaConnector.obtenirModelsVram();
        double availableDiskSpace = osResources.getAvailableDiskSpace(ollamaConnector.pathOllama());

        double cpuUsage = osResources.cpuUsage();

        // Vraie valeur GPU basée sur l'occupation VRAM
        long totalVramCapacity = osResources.totalVRam();
        long vramUtilisee = totalVramCapacity - vramDispo;
        double gpuUsage = totalVramCapacity > 0 ? ((double) vramUtilisee / totalVramCapacity) * 100.0 : 0.0;

        payload.setVram(vramDispo);
        payload.setCpu(cpuUsage);//pourcentage instantanée d'utilisation actuelle du cpu
        payload.setGpu( gpuUsage);
        //payload.put("maxCpu", 100.0); // Valeur réelle max du CPU
        //payload.put("maxGpu", 100.0); // Valeur réelle max du GPU
        payload.setLoadedModels(modelesVRAM.toArray(new String[0]));
        payload.setDiskUsage(availableDiskSpace);
        return payload;
    }

    public Payload buildRarelyChangedPayload(Payload staticInfo) {
        Map<String, Long> diskPartitions = osResources.diskPartitions();
        staticInfo.setDiskPartitions(diskPartitions);
        staticInfo.setMaxCpu(100.0); // Max CPU en pourcentage
        staticInfo.setMaxGpu(100.0);
        staticInfo.setAvailableProcessors(osResources.availableProcessors());//la capacité matérielle totale de la machine (ex: 16 cœurs)
        //staticInfo.put("max_gpu_vram_bytes", 6442450944L);
        staticInfo.setAvailableModels(ollamaConnector.availableModelsDiskUsage().toArray(new String[0]));
        return staticInfo;
    }


    public Payload status() {
        // Fusionne proprement toutes les données (AC + RC )
        Payload payload = new Payload();
        buildAlwaysChangesPayload(payload);
        buildRarelyChangedPayload(payload);
        return payload;
    }


    public void start() {
        try {
            // 1. Connexion unique initiale pour obtenir le connectionId
            gatewayConnector.envoyerConnexionInitiale(status());
        } catch (Exception e) {
            String errorMessage = (e.getMessage() != null) ? e.getMessage() : e.getClass().getName();
            System.err.println("[Erreur Critique] Impossible de joindre la Gateway (" + errorMessage + "). Arrêt de l'agent.");
            return;
        }
        //thread1(producteur): recupere les taches du gateway et le mettent dans la file
        Thread producteur = new Thread(() -> {
            while (true) {
                try {
                    // 1. Vérifier si les RC (disques, modèles, etc.) ont changé
                    checkRCChanges();

                    //TaskMessage tache = recupererTache();
                    TaskMessage tache = gatewayConnector.recupererTacheViaContinue(buildAlwaysChangesPayload(new Payload()));
                    if (tache != null && tache.getPrompt() != null && !tache.getPrompt().isEmpty()) {
                        tasks.put(tache); // Ajouter à la file
                        System.out.println("Tâche ajoutée à la file (ID: " + tache.getIdPrompt() + ", Modèle: " + tache.getNameModel() + ")");
                    }
                    Thread.sleep(1000);//pause entre 2 appels au GW(timout=30s)
                } catch (Exception e) {
                    System.out.println("erreur:" + e.getMessage());

                }
            }
        });
        //thread2(consommateur):prend les taches de la file et appel ollama
        Thread consommateur = new Thread(() -> {
            while (true) {
                try {
                    TaskMessage tache = tasks.take();//attend qu'une tache soit dispo
                    String modeleCible = tache.getNameModel() != null ? tache.getNameModel() : "qwen2:0.5b";

                    ollamaConnector.extraireNomModele(modeleCible); // Vérifie et installe si besoin
                    String resOllama = ollamaConnector.processeur(tache.getPrompt(), modeleCible);
                    TaskMessage tacheSuivante = gatewayConnector.sendResAndContinue(tache.getIdPrompt(), resOllama,status());
                    if (tacheSuivante != null) {
                        tasks.put(tacheSuivante);
                        System.out.println("Tâche enchaînée ajoutée à la file (ID: " + tacheSuivante.getIdPrompt() + ")");
                    }
                } catch (Exception e) {
                    System.err.println("Erreur consommateur : " + e.getMessage());
                }
            }
        });
        producteur.start();
        consommateur.start();
    }


    public void checkRCChanges() {
        Payload currentRC = buildRarelyChangedPayload(status());

        // Si c'est le tout premier appel, on initialise simplement le cache
        if (lastRC==null) {
            lastRC = currentRC.copy();
            return;
        }

        // Si les RC ont changé (ex: nouveau disque branché, modèle installé/supprimé)
        if (!currentRC.equals(lastRC)) {
            gatewayConnector.sendInvalidate();
            // Mettre à jour la référence avec le nouvel état
            lastRC = currentRC.copy();
        }
    }
}
