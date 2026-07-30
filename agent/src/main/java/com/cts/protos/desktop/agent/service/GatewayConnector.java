package com.cts.protos.desktop.agent.service;

import com.cts.protos.desktop.agent.model.Payload;
import com.cts.protos.desktop.agent.model.TaskMessage;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NElementReader;
import net.thevpc.nuts.elem.NElementWriter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class GatewayConnector {
    private final String baseUrl = "http://localhost:8080";
    //private final String gateway_url= "http://192.168.1.119:8080/api/hello";
    private final String gateway_connect_url = baseUrl + "/api/connect";
    private final String gateway_continue_url = baseUrl + "/api/continue";
    private final String gateway_status_url = baseUrl + "/api/hello";
    private final String gateway_invalidate_url = baseUrl + "/api/invalidate";
    //private final String gateway_response_url = baseUrl+"/api/prompt";//envoyer le resultat de l'execution d'une tache
    private String connectionId = null;
    private String agentId;

    public GatewayConnector(String agentId) {
        this.agentId = agentId;
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

    public void envoyerConnexionInitiale(Payload payload) throws Exception {
        // 1. Récupération des données RC + AC de base

        // 2. Adaptation des champs selon le nouveau contrat OpenAPI de la Gateway
        payload.setAgentId(this.agentId);

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

    private synchronized void reconnecter(Payload payload) {
        // Si un autre thread a déjà remis à jour le connectionId, on sort !
        if (this.connectionId != null) {
            return;
        }
        System.err.println("[Agent] ConnectionId expiré (401). Ré-enregistrement auprès de la Gateway...");
        try {
            envoyerConnexionInitiale(payload);
            System.out.println("[Agent] Reconnexion réussie !");
        } catch (Exception ex) {
            System.err.println("[Agent] Échec de la reconnexion : " + ex.getMessage());
        }
    }




    public TaskMessage recupererTacheViaContinue(Payload ac) {
        if (this.connectionId == null) {
            return null;
        }
        ac.setConnectionId(this.connectionId);

        String jsonPayload = NElementWriter.ofPlainJson().formatPlain(ac);

        /*HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(35))
                .build();*/

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gateway_continue_url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(300))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                return parseTaskMessage(body);
            } else if (response.statusCode() == 401) {
                reconnecter(ac);
            }
        } catch (java.net.http.HttpTimeoutException e) {
            System.err.println("[Agent] Timeout réseau (25s) lors de la récupération de tâche.");
        } catch (Exception e) {
            System.err.println("[Agent] Erreur de communication avec la Gateway : " + e.getMessage());
        }
        return null;
    }

    public TaskMessage sendResAndContinue(String taskId, String result,Payload payload) {
        if (this.connectionId == null) {
            return null;
        }
        try {
            payload.setConnectionId(this.connectionId);
            payload.setConnectionId(this.connectionId);
            payload.setTaskId(taskId);
            payload.setTaskResult(result);
            String jsonPayload = NElementWriter.ofPlainJson().formatPlain(payload);
            System.out.println("[Agent] Envoi du résultat de la tâche " + taskId + " via /api/continue");

            /*HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(35))
                    .build();*/

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gateway_continue_url)) // Utilisation exclusive de /api/continue
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("[Agent] Résultat bien pris en compte par la Gateway via /api/continue.");
                String body = response.body();
                return parseTaskMessage(body);

            }
        } catch (java.net.http.HttpTimeoutException e) {
            System.err.println("[Consommateur] Timeout lors de l'envoi du résultat de la tâche " + taskId);
        } catch (Exception e) {
            System.err.println("[Consommateur] Erreur d'envoi : " + e.getMessage());
        }
        return null;
    }

    private TaskMessage parseTaskMessage(String body) {
        if (body == null || body.trim().isEmpty() || body.contains("WAIT_NO_TASKS_AVAILABLE")) {
            return null; // Aucune tâche disponible
        }

        String texteBrut = body.trim();
        if (texteBrut.contains("|||")) {
            String[] parts = texteBrut.split("\\|\\|\\|", 3);
            if (parts.length >= 2) {
                TaskMessage task = new TaskMessage();
                task.setIdPrompt(parts[0].trim());
                task.setPrompt(parts[1].trim());
                // Récupération du modèle s'il est fourni dans la 3ème partie
                if (parts.length == 3 && !parts[2].trim().isEmpty()) {
                    task.setNameModel(parts[2].trim());
                } else {
                    task.setNameModel("qwen2:0.5b"); // Fallback de sécurité
                }
                return task;
            }
        }

        System.err.println("[Agent] Format de réponse incompris depuis /api/continue : " + body);
        return null;
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

}
