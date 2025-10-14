package com.xryzo11.discordbot.core;

import com.xryzo11.discordbot.DiscordBot;

import java.io.File;
import java.io.FileWriter;

public class ScriptGenerator {
    public static void main(String[] args) {
        createNewScripts("target" + File.separator);
    }

    public static void createNewScripts(String directory) {
        String artifactId = System.getProperty("artifactId");
        String version = System.getProperty("version");
        String jarName = artifactId + "-" + version + "-shaded.jar";
        if (jarName.equals("null-null-shaded.jar")) {
            Package pkg = DiscordBot.class.getPackage();
            artifactId = pkg.getImplementationTitle();
            version = pkg.getImplementationVersion();
            jarName = artifactId + "-" + version + "-shaded.jar";
        }
        createStartScript(directory);
        createStartParamsScript(jarName, directory);
        createRestartScript(jarName, directory);
    }

    private static void createStartScript(String directory) {
        try {
            File startScript = new File(directory, "start.sh");
            startScript.delete();
            try (FileWriter fw = new FileWriter(startScript)) {
                fw.write("#!/bin/bash\n");
                fw.write("echo 'export JAVA_HOME=/usr/lib/jvm/jdk-24.0.1-oracle-x64' | sudo tee -a /etc/profile\n");
                fw.write("echo 'export PATH=$JAVA_HOME/bin:$PATH' | sudo tee -a /etc/profile\n");
                fw.write("source /etc/profile\n");
                fw.write("while (true); do\n");
                fw.write("  clear\n");
                if (Config.isYtDlpUpdateEnabled()) fw.write("  pip install -U yt-dlp\n");
                fw.write("  ./start-params.sh\n");
                fw.write("  sleep 3\n");
                fw.write("done\n");
            }
            startScript.setExecutable(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createStartParamsScript(String jarName, String directory) {
        try {
            File startParamsScript = new File(directory, "start-params.sh");
            startParamsScript.delete();
            try (FileWriter fw = new FileWriter(startParamsScript)) {
                fw.write("#!/bin/bash\n");
                fw.write("exec java --enable-native-access=ALL-UNNAMED -jar \"" + jarName + "\"\n");
            }
            startParamsScript.setExecutable(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createRestartScript(String jarName, String directory) {
        try {
            File restartScript = new File(directory, "restart.sh");
            restartScript.delete();
            try (FileWriter fw = new FileWriter(restartScript)) {
                fw.write("#!/bin/bash\n");
                fw.write("pkill -f \"" + jarName + "\"\n");
                fw.write("clear\n");
            }
            restartScript.setExecutable(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}