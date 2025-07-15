package com.xryzo11.discordbot;

import java.io.File;
import java.io.FileWriter;

public class ScriptGenerator {
    public static void main(String[] args) {
        createNewScripts("target/");
    }

    public static void createNewScripts(String directory) {
        String jarName = System.getProperty("artifactId", "DiscordBot") + "-" +
        System.getProperty("version", "1.2.4-SNAPSHOT") + "-shaded.jar";
        createStartScript(jarName, directory);
        createRestartScript(jarName, directory);
    }

    private static void createStartScript(String jarName, String directory) {
        try (FileWriter fw = new FileWriter(directory + "start.sh")) {
            fw.write("#!/bin/bash\n");
            fw.write("clear\n");
            fw.write("echo 'export JAVA_HOME=/usr/lib/jvm/jdk-24.0.1-oracle-x64' | sudo tee -a /etc/profile\n");
            fw.write("echo 'export PATH=$JAVA_HOME/bin:$PATH' | sudo tee -a /etc/profile\n");
            fw.write("source /etc/profile\n");
            fw.write("java --enable-native-access=ALL-UNNAMED -jar " + jarName + "\n");
            new File("start.sh").setExecutable(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createRestartScript(String jarName, String directory) {
        try (FileWriter fw = new FileWriter(directory + "restart.sh")) {
            fw.write("#!/bin/bash\n");
            fw.write("echo \"Stopping existing DiscordBot process...\"\n");
            fw.write("pkill -f \"" + jarName + "\"\n");
            fw.write("echo \"Starting DiscordBot with start.sh\"\n");
            fw.write("java --enable-native-access=ALL-UNNAMED -jar " + jarName + "\n");
            new File("restart.sh").setExecutable(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}