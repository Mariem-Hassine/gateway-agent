//package com.cts.protos.desktop.agent;
//
//import net.thevpc.nuts.Nuts;
//import net.thevpc.nuts.command.NExec;
//
//public class HautLaMain {
//    public static void main(String[] args) {
//        Nuts.require();
//        String result = NExec.ofSystem("ollama", "pull", "qwen2:0.5b").grabbedAll();
//        System.out.println(result);
//    }
//}