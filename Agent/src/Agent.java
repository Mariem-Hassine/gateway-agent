import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;

public class Agent {
    private final String gateway_url= "http://192.168.1.119:8080/api/hello";
    //file d'attente pour stocker les taches recu du gateway
    private final LinkedBlockingQueue<String> tasks = new LinkedBlockingQueue<>();
    public void start() {
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


    /*String etat = "{\"status\": \"pret\"}";
    while(true){
    //construction des requetes
        HttpClient connect = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request_connect = HttpRequest.newBuilder()
                .uri(URI.create(gateway_url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(150))
                .POST(HttpRequest.BodyPublishers.ofString(etat))
                .build();

        try{
            //envoie de requete et attente de response
            HttpResponse<String> response = connect.send(request_connect, HttpResponse.BodyHandlers.ofString());//response reçu par l'Agent
            if(response.statusCode()!=200){
                System.err.println("[Agent] Alerte : Serveur Gateway injoignable ou erreur " + response.statusCode());
                return; // On arrête l'exécution car on ne peut pas travailler
            }
            String task = response.body();//extraction du contenu reel de la response
            System.out.println("[agent] tache recue : "+task);
            String resOllama = processeur(task);
            String jsonResultat = "{\"status\": \"termine\", \"result\": \"" + resOllama.replace("\"", "\\\"") + "\"}";

            HttpRequest request_envoi = HttpRequest.newBuilder()
                    .uri(URI.create(gateway_url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonResultat)) // Envoie {"status":"...", "result":"..."}
                    .build();

            // Envoi effectif du résultat
            HttpResponse<String> responseFinale = connect.send(request_envoi, HttpResponse.BodyHandlers.ofString());
            System.out.println("[Agent] Résultat envoyé. Statut Gateway : " + responseFinale.statusCode());
        } catch (java.net.ConnectException e) {
            // Cas spécifique : Le serveur n'est pas démarré (Refused)
            System.err.println("[Agent] Erreur critique : Impossible de contacter la Gateway (Serveur éteint ?)");
        } catch (java.net.http.HttpConnectTimeoutException e) {
            // Cas spécifique : Le réseau est trop lent ou le serveur est gelé
            System.err.println("[Agent] Erreur : Timeout de connexion.");
        } catch (Exception e) {
            System.err.println("[Agent] Erreur imprévue : " + e.getMessage());
        }
}
}*/
    //methode de recuperation d'une tache
        private String recuperertache() throws Exception{
            HttpClient client= HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gateway_url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"status\": \"pret\"}"))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.body() == null || response.body().isEmpty()) {
                return null; // Évite le null pointer
            }
            return response.body();

            //return (response.statusCode() == 200) ? response.body() : null;
        }

        private String processeur (String task){
            System.out.println("[Agent] Envoi à Ollama de : " + task);

            // Le JSON attendu par Ollama (/api/generate)
            String jsonPayload = "{\"model\": \"llama3\", \"prompt\": \"" + task + "\", \"stream\": false}";

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
