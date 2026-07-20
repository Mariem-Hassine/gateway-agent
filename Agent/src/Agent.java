import java.io.IOException;
import java.net.HttpURLConnection;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
//import net.thenuts.naf.exec.NExec;
import net.thevpc.nuts.*;
import net.thevpc.nuts.command.NExec;
import org.springframework.web.client.RestTemplate;

public class Agent {
    private final String gateway_url= "http://192.168.1.119:8080/api/hello";
    //private final String gateway_url = "http://192.168.1.6:8080/api/hello";
    //file d'attente pour stocker les taches recu du gateway
    private final LinkedBlockingQueue<String> tasks = new LinkedBlockingQueue<>();

    // recuperer le nom du modele deja existant et l'installe s'il n'existe pas
    public String extraireNomModele(String nomModelRequis) throws Exception{
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest .newBuilder()
                .uri(URI.create("http://localhost:11434/api/tags"))
                .GET()
                .build();
        HttpResponse<String> response= client.send(request,HttpResponse.BodyHandlers.ofString());//response sous forme json
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root =mapper.readTree(response.body());
        //parcours la liste des modeles
        JsonNode models=root.get("models");
        for(JsonNode model : models){
            if(model.get("name").asText().equals(nomModelRequis)){
                return"modele trouvé!";
            }
        }
        // Accès au premier modèle de la liste
        //String nomModele = root.get("models").get(0).get("name").asText();

        System.out.println("Modèle  " + nomModelRequis+"non trouvé.lancement de l'installation...");
        installerModel(nomModelRequis);
        return "modele desiré installé";
    }

    private void installerModel(String nomModelRequis) {
        System.out.println("debut de l'installation du modele:"+nomModelRequis);
        try{
            String cmd="ollama pull"+nomModelRequis;
            String res = String.valueOf(NExec.of(cmd).system());
            System.out.println("installation terminée;"+"details:"+res);

        }catch(Exception e){
            System.out.println("erreur au cour de l'installation du modele desirée"+e.getMessage());
        }
    }
    public void testExtractionGPU() {
        try {
            System.out.println("--- DÉBUT DU TEST D'EXTRACTION GPU ---");

            // Appel direct de votre méthode existante
            List<Map<String, Object>> modelsInfo = lireOllamaPs();

            if (modelsInfo.isEmpty()) {
                System.out.println("Aucun modèle actif trouvé sur Ollama.");
            } else {
                for (Map<String, Object> model : modelsInfo) {
                    System.out.println("------------------------------------");
                    System.out.println("Modèle : " + model.get("name"));
                    System.out.println("VRAM utilisée : " + model.get("size_vram") + " bytes");
                    System.out.println("Expiration : " + model.get("expires_at"));
                }
            }

            System.out.println("------------------------------------");
            System.out.println("--- FIN DU TEST D'EXTRACTION ---");

        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération des données GPU : " + e.getMessage());
            System.err.println("Assurez-vous que Ollama est bien lancé (ollama serve).");
        }
    }
   /* public void preparerStatus() {
        try {
            List<Map<String,Object>> Infos = lireOllamaPs();
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest .newBuilder()
                    .uri(URI.create("http://localhost:11434/api/ps"))
                    .GET()
                    .build();
            HttpResponse<String> response= client.send(request,HttpResponse.BodyHandlers.ofString());//response sous form json
            System.out.println("Code retour : " + response.statusCode());
            System.out.println("Corps de la réponse : " + response.body());

        } catch (Exception e) {
            System.err.println("Erreur lors de l'envoi : " + e.getMessage());
        }
    }*/

    // Appelle l'API locale d'Ollama et parse le JSON en List<Map>
    public List<Map<String, Object>> lireOllamaPs() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/ps"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        JsonNode modelsNode = root.get("models");

        List<Map<String, Object>> result = new ArrayList<>();
        if (modelsNode != null && modelsNode.isArray()) {
            for (JsonNode modelNode : modelsNode) {
                Map<String, Object> modelInfo = new HashMap<>();
                modelInfo.put("name", modelNode.get("name").asText());
                modelInfo.put("size_vram", modelNode.get("size_vram").asLong());
                modelInfo.put("expires_at", modelNode.get("expires_at").asText());
                result.add(modelInfo);
            }
        }
        return result;
    }

    /*public void start() {
        //thread1(producteur): recupere les taches du gateway et le mettent dans la file
        Thread producteur = new Thread(() -> {
            while (true) {
                try {
                    String tache = recuperertache();
                    if (tache != null) {
                        tasks.put(tache);//ajouter a la file
                        System.out.println("tache ajouté a la file:" + tache);
                    }
                    Thread.sleep(1000);//pause entre 2 appels au GW
                } catch (Exception e) {
                    System.out.println("erreur:" + e.getMessage());
                }
            }
        });
        //thread2(consommateur):prend les taches de la file et appel ollama
        Thread consommateur = new Thread(() -> {
            while (true) {
                try {
                    String tache = tasks.take();//attend qu'une tache soit dispo
                    String resOllama = processeur(tache);
                    sendResToGW(resOllama);
                }catch(Exception e){
                    System.err.println("erreue:"+e.getMessage());
                }
            }
        }
        );
        producteur.start();
        consommateur.start();
    }*/
    public void start() {
        // 1. Thread producteur : Injecte une tâche de test au lieu d'appeler le réseau
        Thread producteur = new Thread(() -> {
            try {
                // On attend 2 secondes pour laisser le temps d'extraire les modèles
                Thread.sleep(2000);
                String tacheTest = "Explique le multithreading en Java avec exemples de code";
                tasks.put(tacheTest);
                System.out.println("[TEST] Tâche injectée dans la file : " + tacheTest);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        // 2. Thread consommateur : Appelle Ollama mais NE PAS envoie à la Gateway
        Thread consommateur = new Thread(() -> {
            while (true) {
                try {
                    String tache = tasks.take();
                    String resOllama = processeur(tache);

                    // IMPORTANT : On affiche le résultat ici au lieu d'appeler sendResToGW
                    System.out.println("[TEST] Résultat final obtenu : " + resOllama);

                } catch (Exception e) {
                    System.err.println("Erreur : " + e.getMessage());
                }
            }
        });
        producteur.start();
        consommateur.start();
    }

    private void sendResToGW(String res) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String json = "{\"status\": \"termine\", \"result\": \"" + res.replace("\"", "\\\"") + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gateway_url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    //methode de recuperation d'une tache
        private String recuperertache() throws Exception{
            List<Map<String, Object>> modelsInfo = lireOllamaPs();//extraction des données réels
            Map<String, Object> payload = new HashMap<>();
            payload.put("pc_id", System.getProperty("user.name") + "@" + java.net.InetAddress.getLocalHost().getHostName());
            payload.put("status", "pret");
            payload.put("models", modelsInfo);
            // Conversion en JSON
            ObjectMapper mapper = new ObjectMapper();
            String jsonPayload = mapper.writeValueAsString(payload);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gateway_url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload)) // <--- Utilisation du vrai JSON
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
                return response.body();
            }
            return null;
        }

        private String processeur (String task){
            System.out.println("[Agent] Envoi à Ollama de : " + task);

            // Le JSON attendu par Ollama (/api/generate)
            String jsonPayload = "{\"model\": \"qwen2:0.5b\", \"prompt\": \"" + task + "\", \"stream\": false}";

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
                        return "etat:pret|resultat:" + resultat;
                    }
                }
                return "etat:erreur|resultat:Format_JSON_inattendu";

            } catch (Exception e) {
                System.err.println("[Agent] Erreur de communication avec Ollama : " + e.getMessage());
                return "etat:erreur|resultat:Ollama_Non_Joignable";
            }
        }
    }
