package com.cts.protos.desktop.agent;

import com.sun.management.OperatingSystemMXBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import net.thevpc.nuts.command.NExec;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NElementReader;
import net.thevpc.nuts.elem.NElementWriter;

import java.lang.management.ManagementFactory;

public class Agent {
    //private final String gateway_url= "http://192.168.1.119:8080/api/hello";
    private final String gateway_connect_url = "http://192.168.1.119:8080/api/connect";
    private final String gateway_continue_url = "http://192.168.1.119:8080/api/continue";
    private final String gateway_status_url = "http://localhost:8080/api/hello";
    private final String gateway_invalidate_url = "http://192.168.1.119:8080/api/invalidate";
    //private final String gateway_response_url = "http://192.168.1.119:8080/api/prompt";//envoyer le resultat de l'execution d'une tache

    private String connectionId= null ;
    private Map<String, Object> lastRC = new HashMap<>();

    OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    long totalRam = osBean.getTotalMemorySize();//RAM totale de la machine
    long freeRam = osBean.getFreeMemorySize();//RAM actuelle libre
    //file d'attente pour stocker les taches recu du gateway
    private final LinkedBlockingQueue<TaskMessage> tasks = new LinkedBlockingQueue<>();
    // Identifiant unique de l'agent
    private final String  idAgent = initialiserIdAgent();

    private String initialiserIdAgent() {
        try {
            String base = System.getProperty("user.name") + "@" + java.net.InetAddress.getLocalHost().getHostName();
            String suffixe = java.util.UUID.randomUUID().toString().substring(0, 8);
            return base + "-" + suffixe;
        } catch (Exception e) {
            return "unknown-host-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
    }

    public Map<String, Object> construirePayloadAC() {
        long vramDispo = obtenirVramDisponible();
        List<String> modelesVRAM = obtenirModelsVram();

        // Calcul universel du disque sur toutes les partitions (Windows & Linux/macOS)
        java.io.File[] racines = java.io.File.listRoots();
        long totalDiskAll = 0;
        long freeDiskAll = 0;

        if (racines != null) {
            for (java.io.File racine : racines) {
                if (racine.exists()) {
                    totalDiskAll += racine.getTotalSpace();
                    freeDiskAll += racine.getFreeSpace();
                }
            }}
        //calculer proprement le pourcentage d'occupation de l'espace disque
       // double diskUsage = totalDiskAll > 0 ? ((double) (totalDiskAll - freeDiskAll) / totalDiskAll) * 100.0 : 0.0;
        double diskUsage;

        // On vérifie que l'espace total est supérieur à 0 (pour éviter la division par zéro)
        if (totalDiskAll > 0) {
            // On calcule l'espace utilisé (Total - Libre)
            long espaceUtilise = totalDiskAll - freeDiskAll;

            // On calcule le pourcentage en convertissant en double pour garder la précision des virgules
            diskUsage = ((double) espaceUtilise / totalDiskAll) * 100.0;
        } else {
            // Si aucun disque n'est détecté ou si la taille est 0, on met le pourcentage à 0 par sécurité
            diskUsage = 0.0;
        }

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

        // Vraie valeur GPU basée sur l'occupation VRAM
        long totalVramCapacity = 6442450944L;
        long vramUtilisee = totalVramCapacity - vramDispo;
        double gpuUsage = totalVramCapacity > 0 ? ((double) vramUtilisee / totalVramCapacity) * 100.0 : 0.0;

        Map<String, Object> payload = new HashMap<>();
        payload.put("vram", vramDispo);
        payload.put("cpu", cpuUsage);//pourcentage instantanée d'utilisation actuelle du cpu
        payload.put("gpu", gpuUsage);
        //payload.put("maxCpu", 100.0); // Valeur réelle max du CPU
        //payload.put("maxGpu", 100.0); // Valeur réelle max du GPU
        payload.put("modelsInVRAM", modelesVRAM);
        payload.put("diskUsage", diskUsage);
        return payload;
    }

    public Map<String, Object> construirePayloadRC() {
        Map<String, Object> staticInfo = new HashMap<>();

        Map<String, Long> diskPartitions = new HashMap<>();
        java.io.File[] racines = java.io.File.listRoots();

        if (racines != null) {
            for (java.io.File racine : racines) {
                if (racine.exists()) {
                    diskPartitions.put(racine.getAbsolutePath(), racine.getTotalSpace());                }
            }
        }
        staticInfo.put("diskPartitions", diskPartitions);
        staticInfo.put("maxCpu", 100.0); // Max CPU en pourcentage
        staticInfo.put("maxGpu", 100.0);
        staticInfo.put("max_cpu_cores", Runtime.getRuntime().availableProcessors());//la capacité matérielle totale de la machine (ex: 16 cœurs)
        //staticInfo.put("max_gpu_vram_bytes", 6442450944L);
        staticInfo.put("availableModels", obtenirModelesDisque());

        return staticInfo;
    }


    public Map<String, Object> status() {
        // Fusionne proprement toutes les données (AC + RC )
        Map<String, Object> payload = new HashMap<>();
        payload.putAll(construirePayloadAC());
        payload.putAll(construirePayloadRC());
        //payload.put("idAgent", idAgent);
        return payload;
    }

    public List<String> obtenirModelsVram() {//retoune une liste des noms des modeles
        List<String> modelesVram = new ArrayList<>();

        try {//si Ollama est injoignable, ou si le JSON est malformé, l'exécution passe directement au catch sans planter le programme.
            HttpClient client = HttpClient.newHttpClient();

            //  Récupérer les modèles actifs dans le GPU (/api/ps)
            HttpRequest requestPs = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/ps"))//endpoint Ollama qui liste les modèles actuellement chargés en VRAM
                    .GET()
                    .build();
            HttpResponse<String> responsePs = client.send(requestPs, HttpResponse.BodyHandlers.ofString());//responsePs contient le code de statut HTTP + le corps de la réponse
            NElement rootPs = NElementReader.ofJson().read(responsePs.body());
            NElement modelsNodePs = rootPs.asObject().get().get("models").orNull();
            if (modelsNodePs != null && modelsNodePs.isAnyArray()) {
                for (NElement m : modelsNodePs.asArray().get()) {
                    if (m.isObject()) {
                        String nom =  m.asObject().get().getStringValue("name").orElse("");
                        modelesVram.add(nom);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[Agent] Erreur lors de la récupération des modèles en VRAM : " + e.getMessage());
        }

        return modelesVram;
    }
    public List<String> obtenirModelesDisque() {
        List<String> modelesDisque = new java.util.ArrayList<>();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest requestTags = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/tags"))
                    .GET()
                    .build();
            HttpResponse<String> responseTags = client.send(requestTags, HttpResponse.BodyHandlers.ofString());
            NElement rootTags = NElementReader.ofJson().read(responseTags.body());

            NElement modelsNodeTags = rootTags.asObject().get().get("models").orNull();
            if (modelsNodeTags != null && modelsNodeTags.isAnyArray()) {
                for (NElement m : modelsNodeTags.asArray().get()) {
                    if (m.isObject()) {
                        String nom = m.asObject().get().getStringValue("name")
                                .orElse(m.asObject().get().getStringValue("model").orElse(""));
                        if (!nom.isEmpty()) {
                            modelesDisque.add(nom);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Agent] Erreur lors de la lecture de /api/tags : " + e.getMessage());
        }
        return modelesDisque;
    }
    public void envoyerConnexionInitiale() throws Exception {
        // 1. Récupération des données RC + AC de base
        Map<String, Object> payload = status();

        // 2. Adaptation des champs selon le nouveau contrat OpenAPI de la Gateway
        payload.put("idAgent", this.idAgent);

        String jsonPayload = NElementWriter.ofPlainJson().formatPlain(payload);
        System.out.println("[Agent] Envoi de la connexion initiale vers /api/connect : " + jsonPayload);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gateway_connect_url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        System.out.println("[Agent] URL cible de connexion : " + gateway_connect_url);
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("La Gateway a refusé la connexion. Code retour : " + response.statusCode() + " - Corps : " + response.body());
        }
            NElement root = NElementReader.ofJson().read(response.body());//parser la reponse (JSON) en NElement (le corps brut de la réponse HTTP, une chaîne JSON comme {"status":"ok","connectionId":"CONN-BWAHAHAHA"})
            if (root.isObject()) {//verification de type
                connectionId = root.asObject().get().getStringValue("connectionId").orElse(null);
            }

            if (connectionId == null) {
                throw new RuntimeException("La Gateway n'a pas renvoyé de connectionId valide.");
            }

            System.out.println("[Agent] Connexion initiale réussie - connectionId reçu : " + connectionId);
        }

    // recuperer le nom du modele deja existant et l'installe s'il n'existe pas
    public String extraireNomModele(String nameModel) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/tags"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());//response sous forme json
        //String json = NElementWriter.ofPlainJson().formatPlain(response);
        //parcours la liste des modeles
        NElement root = NElementReader.ofJson().read(response.body());
        NElement models = root.asObject().get().get("models").orNull();
        if (models != null && models.isAnyArray()) {
            for (NElement model : models.asArray().get()) {
                if (model.isObject()) {
                    String nomModele = model.asObject().get().getStringValue("name").orElse("");
                    if (nomModele.equals(nameModel)) {
                        return "modele " +nameModel+" trouvé!";
                    }
                }
            }

        }
        // Accès au premier modèle de la liste
        //String nomModele = root.get("models").get(0).get("name").asText();

        System.out.println("Modèle  " + nameModel + "non trouvé.lancement de l'installation...");
        installerModel(nameModel);
        return "modele desiré installé";
    }

    private void installerModel(String nomModelRequis) {
        System.out.println("debut de l'installation du modele:" + nomModelRequis);
        try {
            net.thevpc.nuts.Nuts.require();
            String res = NExec.ofSystem("ollama", "pull", nomModelRequis).grabbedOut();
            System.out.println("installation terminée;" + "details:" + res);

        } catch (Exception e) {
            System.out.println("erreur au cour de l'installation du modele desirée " + e.getMessage());
        }
    }
    /*private void envoyerContinue() throws Exception {
        if (this.connectionId == null) {
            System.err.println("[Agent] Impossible d'envoyer 'continue' : aucun connectionId disponible.");
            return;
        }

        // Récupération des métriques (status)
        Map<String, Object> payload = status();

        // Ajout des informations requises pour le continue
        payload.put("connectionId", this.connectionId);
        //payload.put("type", "continue");

        String jsonPayload = NElementWriter.ofPlainJson().formatPlain(payload);
        System.out.println("[Agent] Envoi du message 'continue' vers la Gateway : " + jsonPayload);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gateway_continue_url)) // ou gateway_continue_url selon votre config
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            System.out.println("[Agent] Message 'continue' bien pris en compte par la Gateway.");
        } else {
            System.err.println("[Agent] Erreur 'continue' - Code : " + response.statusCode());
        }
    }*/
    public void sendResAndContinue(String taskId ,String result){
        if (this.connectionId == null) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("connectionId", this.connectionId);
            payload.put("taskId", taskId);       // Le taskId de la tâche achevée
            payload.put("result", result);     // Le texte de la réponse d'Ollama

            // Intégration des métriques AC en temps réel (obligatoires pour le StatusRequest)
            payload.putAll(construirePayloadAC());

            String jsonPayload = NElementWriter.ofPlainJson().formatPlain(payload);
            System.out.println("[Agent] Envoi du résultat de la tâche " + taskId + " via /api/continue");

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(35))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gateway_continue_url)) // Utilisation exclusive de /api/continue
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("[Agent] Résultat bien pris en compte par la Gateway via /api/continue.");
                // Note: La réponse du serveur 200 sur ce POST peut aussi contenir une *nouvelle* tâche directement !
            } else {
                System.err.println("[Agent] Erreur lors de l'envoi du résultat - Code : " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("[Agent] Erreur réseau lors de l'envoi du résultat : " + e.getMessage());
        }
    }

    public void start() {
        try {
            // 1. Connexion unique initiale pour obtenir le connectionId
            envoyerConnexionInitiale();
        } catch (Exception e) {
            String errorMessage = (e.getMessage() != null) ? e.getMessage() : e.getClass().getName();
            System.err.println("[Erreur Critique] Impossible de joindre la Gateway (" + errorMessage + "). Arrêt de l'agent.");
            return;
        }
            // Tente la connexion initiale. Si la GW est injoignable, stoppe tout ici.
            /*Thread threadContinue = new Thread(() -> {
            while (true) {
                try {
                    envoyerContinue();
                } catch (Exception e) {
                    System.err.println("[Erreur Critique] Impossible de joindre la Gateway (" + e.getMessage() + "). Nouvelle tentative dans 30s...");
                }
                try {
                    Thread.sleep(30000); // Pause de 30 secondes entre chaque connexion
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        threadContinue.start();*/
        //thread1(producteur): recupere les taches du gateway et le mettent dans la file
        Thread producteur = new Thread(() -> {
            while (true) {
                try {
                    // 1. Vérifier si les RC (disques, modèles, etc.) ont changé
                    checkRCChanges();

                    //TaskMessage tache = recupererTache();
                    TaskMessage tache = recupererTacheViaContinue();
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

                    extraireNomModele(modeleCible); // Vérifie et installe si besoin
                    String resOllama = processeur(tache.getPrompt(), modeleCible);
                    sendResAndContinue(tache.getIdPrompt(), resOllama);
                } catch (Exception e) {
                    System.err.println("erreue:" + e.getMessage());
                }
            }
        });
        producteur.start();
        consommateur.start();
    }
    private void sendResToGW(String IdPrompt, String res) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // Construction d'une Map pour maîtriser explicitement les clés JSON envoyées à la Gateway
        Map<String, Object> responsePayload = new HashMap<>();
        responsePayload.put("id", IdPrompt);
        responsePayload.put("connectionId", connectionId); // S'assure que idAgent n'est pas null
        responsePayload.put("result", res);
        // Sérialisation propre de la Map
        String json = NElementWriter.ofPlainJson().formatPlain(responsePayload);
        System.out.println("[Agent] Envoi du résultat JSON : " + json);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gateway_continue_url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("[Agent] Résultat envoyé à la Gateway pour la tâche ID: " + IdPrompt);
    }
    // Interroge Ollama pour calculer la VRAM disponible
    public long obtenirVramDisponible() {//Retourne un long (nombre en octets)
        long totalVramCapacity = 6442450944L; // Votre capacité totale fixe (ex: 6 Go en bytes)

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/ps"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            NElement root = NElementReader.ofJson().read(response.body());
            NElement modelsNode = root.asObject().get().get("models").orNull();

            long vramTotaleUtilisee = 0;
            if (modelsNode != null && modelsNode.isAnyArray()) {
                for (NElement modelNode : modelsNode.asArray().get()) {
                    if (modelNode.isObject()) {
                        long ram = modelNode.asObject().get().getLongValue("size_vram").orElse(0L);
                        vramTotaleUtilisee += ram;
                    }
                }
            }

            // VRAM disponible = Capacité totale - Ce qui est actuellement utilisé par les modèles actifs
            long vramDisponible = totalVramCapacity - vramTotaleUtilisee;

            // Sécurité pour éviter un nombre négatif si la capacité déclarée est inférieure à la réalité
            return Math.max(0, vramDisponible);

        } catch (Exception e) {
            System.err.println("[Agent] Impossible de lire l'état d'Ollama : " + e.getMessage());
            return 0; // Par sécurité si Ollama ne répond pas
        }
    }
    private TaskMessage recupererTacheViaContinue() throws Exception {
        if (this.connectionId == null) {
            return null;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("connectionId", this.connectionId);

        // Ajouter les métriques temps réel (AC)
        Map<String, Object> ac = construirePayloadAC();
        payload.putAll(ac);

        String jsonPayload = NElementWriter.ofPlainJson().formatPlain(payload);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(35))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gateway_continue_url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String body = response.body();
            // Vérifier si la réponse contient une tâche ou un signal d'attente
            if (body != null && !body.equals("WAIT_NO_TASKS_AVAILABLE") && body.contains("|||")) {
                String[] parts = body.split("\\|\\|\\|", 2);
                if (parts.length == 2) {
                    TaskMessage task = new TaskMessage();
                    task.setIdPrompt(parts[0]);
                    task.setPrompt(parts[1]);
                    // Si le modèle n'est pas fourni dans le texte brut, on attribue un défaut (ex: qwen2:0.5b)
                    task.setNameModel("qwen2:0.5b");
                    return task;
                }
            }
        } else if (response.statusCode() == 401) {
            System.err.println("[Agent] Erreur 401 : ConnectionId invalide ou expiré. Tentative de ré-enregistrement auprès de la Gateway...");
            try {
                this.connectionId = null;
                envoyerConnexionInitiale();
                System.out.println("[Agent] Reconnexion réussie après un 401 !");
            } catch (Exception ex) {
                System.err.println("[Agent] Échec de la reconnexion : " + ex.getMessage() + ". Nouvelle tentative dans 5 secondes...");
                Thread.sleep(5000);
            }
            return null;
        }
        return null;
    }

    //methode de recuperation d'une tache
    /*private TaskMessage recupererTache() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("vram_available", obtenirVramDisponible());
        payload.put("agentId", idAgent);
        String jsonPayload = NElementWriter.ofPlainJson().formatPlain(payload);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gateway_continue_url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
            NElement root = NElementReader.ofJson().read(response.body());
            // Vérifier si la réponse est un TaskPayload valide (contient "query" ou "id")
            if (root.isObject()) {
                var rootObj = root.asObject().get();
                String prompt = rootObj.getStringValue("query").orElse(null);
                String idPrompt = rootObj.getStringValue("id").orElse(null); // ou getLongValue si l'id est un nombre
                String nameModel = rootObj.getStringValue("nameModel").orElse(null);
                if (prompt != null && idPrompt != null) {
                    // Construction manuelle de votre objet TaskMessage
                    TaskMessage task = new TaskMessage();
                    task.setIdPrompt(idPrompt);
                    task.setPrompt(prompt);
                    task.setNameModel(nameModel);
                    return task;
                }}}
        return null;
    }*/

    private String processeur(String task, String modelRequis) {
        System.out.println("[Agent] Envoi à Ollama de : " + task);

        // Le JSON attendu par Ollama (/api/generate)
        String jsonPayload = "{\"model\": \"" + modelRequis + "\", \"prompt\": \"" + task + "\", \"stream\": false}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .timeout(Duration.ofSeconds(150)) // Ollama peut être lent selon le modèle
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            // Sécurité : Vérifier si la réponse contient bien la clé "response"
            if (responseBody != null && responseBody.contains("\"response\":\"")) {
                String[] parts = responseBody.split("\"response\":\"");
                if (parts.length > 1) {
                    String resultat = parts[1].split("\"")[0];
                    System.out.println("[Agent] Ollama a répondu : " + resultat);
                    return "etat:pret | resultat:" + resultat;
                }
            }
            return "etat:erreur|resultat:Format_JSON_inattendu";

        } catch (Exception e) {
            System.err.println("[Agent] Erreur de communication avec Ollama : " + e.getMessage());
            return "etat:erreur|resultat:Ollama_Non_Joignable";
        }
    }

    public void checkRCChanges() {
        Map<String, Object> currentRC = construirePayloadRC();

        // Si c'est le tout premier appel, on initialise simplement le cache
        if (lastRC.isEmpty()) {
            lastRC = currentRC;
            return;
        }

        // Si les RC ont changé (ex: nouveau disque branché, modèle installé/supprimé)
        if (!currentRC.equals(lastRC)) {
            sendInvalidate();
            // Mettre à jour la référence avec le nouvel état
            lastRC = currentRC;
        }
    }

    public void sendInvalidate() {
        if (this.connectionId == null) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("connectionId", this.connectionId);

            String jsonPayload = NElementWriter.ofPlainJson().formatPlain(payload);
            System.out.println("[Agent] Modification matérielle (RC) détectée ! Envoi de l'invalidation vers /api/invalidate");

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gateway_invalidate_url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("[Agent] Invalidation prise en compte par la Gateway (ACKNOWLEDGED).");
            } else {
                System.err.println("[Agent] Erreur lors de l'invalidation - Code : " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("[Agent] Erreur réseau lors de l'invalidation : " + e.getMessage());
        }

    }
}
