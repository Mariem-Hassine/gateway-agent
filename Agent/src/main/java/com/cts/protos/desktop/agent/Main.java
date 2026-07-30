
package com.cts.protos.desktop.agent;

import net.thevpc.nuts.Nuts;

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
   public static void main(String[] args) {
        try {
            // Initialisation obligatoire de l'espace de travail Nuts
            Nuts.require();
            Agent agent = new Agent();
            System.out.println("[Main] Démarrage de l'agent et initialisation...");
            agent.start();

        } catch (Exception e) {
            System.err.println("[Erreur Critique] Le programme principal a rencontré un problème : " + e.getMessage());
            e.printStackTrace();
        }
}
}

