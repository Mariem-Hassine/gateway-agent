import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Gateway {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/channel", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            System.out.println("[Gateway] Reçu de l'agent : " + requestBody);

            String response = "Tache: Analyser ce texte";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
        server.start();
        System.out.println("[Gateway] Démarré sur port 8080...");
    }
}