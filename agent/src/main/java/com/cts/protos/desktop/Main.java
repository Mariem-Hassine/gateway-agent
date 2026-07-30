
package com.cts.protos.desktop;

import com.cts.protos.desktop.agent.Agent;
import com.cts.protos.desktop.simulator.GatewaySimulator;
import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.io.NOut;
import net.thevpc.nuts.text.NMsg;

import java.util.Arrays;

public class Main {
   public static void main(String[] args) {
        try {
            if(args.length>0 && args[0].equals("simulator")){
                GatewaySimulator.start(Arrays.copyOfRange(args,1,args.length-1));
                return;
            }
            // Initialisation obligatoire de l'espace de travail Nuts
            Nuts.require();
            Agent agent = new Agent();
            NOut.println(NMsg.ofC("[%s] Démarrage de l'agent et initialisation...", NMsg.ofStyledSuccess("Main")));
            agent.start();
        } catch (Exception e) {
            NOut.println(NMsg.ofC("[%s] Le programme principal a rencontré un problème : %s", NMsg.ofStyledSuccess("Erreur"),e).asError());
        }
}
}

