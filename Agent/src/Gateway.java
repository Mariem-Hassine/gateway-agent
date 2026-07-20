import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.UUID;

public class Gateway {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/hello", new Handler());
        server.setExecutor(null);
        server.start();
        System.out.println("Gateway Légère lancée sur le port 8080...");
    }

    static class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            // 1. Lire tout le contenu pour purger le buffer
            byte[] buffer = new byte[4096];
            InputStream is = t.getRequestBody();
            while (is.read(buffer) != -1) { /* on vide le flux */ }

            // 2. Envoyer une réponse propre
            String response = UUID.randomUUID().toString() + "|||Donne 3 conseils pour réussir un entretien.";

            // 3. Spécifier la taille des headers
            t.getResponseHeaders().set("Content-Type", "text/plain");
            t.sendResponseHeaders(200, response.length());

            // 4. Écrire et fermer
            try (OutputStream os = t.getResponseBody()) {
                os.write(response.getBytes());
            }
            System.out.println("Tâche envoyée.");
        }
    }}