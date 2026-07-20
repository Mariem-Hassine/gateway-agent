import java.util.List;
import java.util.Map;

public class Main {
    /*public static void main(String[] args)  {
        Agent agent=new Agent();
        agent.start();
        //agent.envoyerStatus();


        try {

            // 2. Appel de la méthode
            String nameMOdel = agent.extraireNomModele("qwen2:7b");

            // 3. Affichage à l'intérieur du try (ou après, si nameMOdel est défini)
            System.out.println("Le nom du modèle est : " + nameMOdel);

        } catch (Exception e) {
            // 4. Gestion obligatoire de l'exception
            System.err.println("Erreur lors de la récupération du modèle : " + e.getMessage());
            e.printStackTrace();
        }
    }*/
    public static void main(String[] args) throws Exception {
        Agent agent = new Agent();
        agent.testExtractionGPU();

        // Test extraction données Ollama
        System.out.println("--- Extraction des modèles locaux ---");
        List<Map<String, Object>> models = agent.lireOllamaPs();
        for (Map<String, Object> m : models) {
            System.out.println("Modèle détecté : " + m.get("name") + " | VRAM: " + m.get("size_vram"));
        }

        // Lancer l'agent en mode test
        agent.start();
    }
}
