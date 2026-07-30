package com.cts.protos.desktop.simulator;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NElementReader;
import net.thevpc.nuts.elem.NElementWriter;//transforme un objet java en json

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GatewaySimulator {

    private static final Map<String, String> pendingTasks = new ConcurrentHashMap<>();//map qui stoke les taches en attente

    public static void start(String[] args) throws IOException {
        int port = 8080;
        pendingTasks.put("azerty123","quelle est la capitale de la tunisie?");
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/connect", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                try {
                    net.thevpc.nuts.Nuts.require();
                    NElement root = NElementReader.ofJson().read(body);
                    if (root.isObject()) {
                        var obj = root.asObject().get();
                        String idAgent = obj.getStringValue("idAgent").orElse("inconnu");
                        long vram = obj.getLongValue("vram").orElse(0L);
                        double cpu = obj.getDoubleValue("cpu").orElse(0.0);
                        double diskUsage = obj.getDoubleValue("diskUsage").orElse(0.0);

                        System.out.println("\n[Gateway] Connexion reçue de l'agent : " + idAgent);
                        System.out.println("   - VRAM : " + vram + " octets");
                        System.out.println("   - CPU % : " + cpu);
                        System.out.println("   - Disque Usage % : " + diskUsage);
                    }
                } catch (Exception e) {
                    System.err.println("[Gateway] Erreur lecture specs connect : " + e.getMessage());
                }

                // Dans le contexte /api/connect de Gateway.java
                String response = "{\"status\":\"ok\",\"connectionId\":\"CONN-TEST-12345\"}";
                sendResponse(exchange, 200, response);
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        });

        // 2. Endpoint /api/hello (Long-polling / tâches)
        server.createContext("/api/hello", new GatewayHandler());

        // 3. Endpoint d'injection web
        server.createContext("/api/inject", new InjectHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("==================================================");
        System.out.println("[Gateway Simulateur] Démarré avec succès !");
        System.out.println("En écoute sur : http://localhost:" + port);
        System.out.println(" Injecter une tâche : http://localhost:" + port + "/api/inject?query=Bonjour");
        System.out.println("==================================================");
    }

    static class GatewayHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            try {
                net.thevpc.nuts.Nuts.require();
                NElement root = NElementReader.ofJson().read(requestBody);
                String jsonResponse;

                if (root.isObject()) {
                    var rootObj = root.asObject().get();

                    // Réception du résultat de l'agent
                    if (rootObj.get("result").orNull() != null) {
                        String taskId = rootObj.getStringValue("id").orElse("inconnu");
                        String agentId = rootObj.getStringValue("agentId").orElse("inconnu");
                        String resultat = rootObj.getStringValue("result").orElse("");
                        System.out.println("\n[Gateway] Résultat reçu de l'agent [" + agentId + "] pour la tâche ID: " + taskId);
                        System.out.println("   ==> Réponse : " + resultat);

                        jsonResponse = "{\"status\":\"recu\"}";
                        sendResponse(exchange, 200, jsonResponse);
                        return;
                    }

                    // Long-Polling (l'agent demande une tâche)
                    String agentId = rootObj.getStringValue("idAgent")
                            .orElse(rootObj.getStringValue("agentId").orElse("agent-inconnu"));
                    if (!pendingTasks.isEmpty()) {
                        Map.Entry<String, String> entry = pendingTasks.entrySet().iterator().next();
                        String taskId = entry.getKey();
                        String query = entry.getValue();
                        pendingTasks.remove(taskId);

                        System.out.println("[Gateway] Attribution de la tâche " + taskId + " à l'agent " + agentId);
                        String modeleImpose = "qwen2:0.5b";
                        Map<String, Object> taskPayload = Map.of(
                                "id", taskId,
                                "query", query,
                                "agentId", agentId,
                                "selectedModel", modeleImpose
                        );
                        jsonResponse = NElementWriter.ofPlainJson().formatPlain(taskPayload);
                    } else {
                        Map<String, Object> idlePayload = Map.of(
                                "agentId", agentId,
                                "timeout", true
                        );
                        jsonResponse = NElementWriter.ofPlainJson().formatPlain(idlePayload);
                    }

                    sendResponse(exchange, 200, jsonResponse);

                } else {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid JSON Object\"}");
                }

            } catch (Exception e) {
                System.err.println("[Gateway Erreur] " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"Internal Server Error\"}");
            }
        }
    }

    static class InjectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String queryParams = exchange.getRequestURI().getQuery();
            String queryText = "Explique le multithreading en Java.";

            if (queryParams != null && queryParams.startsWith("query=")) {
                queryText = java.net.URLDecoder.decode(queryParams.substring(6), StandardCharsets.UTF_8);
            }

            String taskId = UUID.randomUUID().toString();
            pendingTasks.put(taskId, queryText);

            String htmlResponse = "<h3>Tâche injectée avec succès !</h3>" +
                    "<p><b>ID :</b> " + taskId + "</p>" +
                    "<p><b>Requête :</b> " + queryText + "</p>";

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, htmlResponse.getBytes(StandardCharsets.UTF_8).length);
            OutputStream os = exchange.getResponseBody();
            os.write(htmlResponse.getBytes(StandardCharsets.UTF_8));
            os.close();

            System.out.println("\n[Gateway] Tâche ajoutée en file d'attente : \"" + queryText + "\" (ID: " + taskId + ")");
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}