package com.cts.protos.desktop.agent.service;

import net.thevpc.nuts.command.NExec;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NElementReader;
import net.thevpc.nuts.platform.NEnv;
import net.thevpc.nuts.platform.NOsFamily;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class OllamaConnector {
    private final boolean ollamaAvailable = false;

    /* private boolean checkAndActivateOllama(){
         System.out.println("test du port 11434...");
         if(isOllamaRunning()){
             System.out.println("ollama est deja actif");
             this.ollamaAvailable = true;
             return true;
         }

     }*/
    private boolean isOllamaRunning() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/version"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
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
                        return "modele " + nameModel + " trouvé!";
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
                        String nom = m.asObject().get().getStringValue("name").orElse("");
                        modelesVram.add(nom);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[Agent] Erreur lors de la récupération des modèles en VRAM : " + e.getMessage());
        }

        return modelesVram;
    }

    public List<String> availableModelsDiskUsage() {
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

    // Interroge Ollama pour calculer la VRAM disponible
    public long obtenirVramDisponible() {//Retourne un long (nombre en octets)
        long totalVramCapacity = new OsResources().totalVRam(); // Votre capacité totale fixe (ex: 6 Go en bytes)

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

    public String pathOllama() {
        // Vérifier si l'utilisateur a défini un chemin personnalisé (priorité absolue)
        String cheminPersonnalise = System.getenv("OLLAMA_MODELS");
        if (cheminPersonnalise != null && !cheminPersonnalise.trim().isEmpty()) {
            return cheminPersonnalise;
        }
        // Si aucune variable n'est définie, utiliser le chemin par défaut
        String userHome = NEnv.of().userHome();
        switch (NOsFamily.current()) {
            case WINDOWS:
                return userHome + "\\.ollama";
            default:
                return userHome + "/.ollama";
        }
    }


    public String processeur(String task, String modelRequis) {
        System.out.println("[Agent] Envoi à Ollama de : " + task);

        // Le JSON attendu par Ollama (/api/generate)
        String jsonPayload = "{\"model\": \"" + modelRequis + "\", \"prompt\": \"" + task + "\", \"stream\": false}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .timeout(Duration.ofSeconds(200)) // Ollama peut être lent selon le modèle
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            NElement root = NElementReader.ofJson().read(response.body());
            if (root.isObject()) {
                String resultat = root.asObject().get().getStringValue("response").orElse("");
                System.out.println("[Agent] Ollama a répondu : " + resultat);
                return "resultat:" + resultat;
            }
            return "etat:erreur|resultat:Format_JSON_inattendu";
        } catch (Exception e) {
            System.err.println("[Agent] Erreur de communication avec Ollama : " + e.getMessage());
            return "resultat:Ollama_Non_Joignable";
        }
    }


}
