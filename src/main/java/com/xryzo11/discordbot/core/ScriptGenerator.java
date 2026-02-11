package com.xryzo11.discordbot.core;

import com.xryzo11.discordbot.DiscordBot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScriptGenerator {
    private static final Logger LOGGER = Logger.getLogger(ScriptGenerator.class.getName());

    public static void main(String[] args) {
        String artifactId = System.getProperty("artifactId");
        String version = System.getProperty("version");

        if (artifactId == null || version == null || "null".equals(artifactId) || "null".equals(version)) {
            Package pkg = DiscordBot.class.getPackage();
            artifactId = pkg.getImplementationTitle();
            version = pkg.getImplementationVersion();
        }

        String releaseDirectory = "target" + File.separator + version + File.separator;
        File releaseDir = new File(releaseDirectory);
        if (!releaseDir.exists()) {
            if (!releaseDir.mkdirs()) {
                LOGGER.log(Level.SEVERE, "Failed to create release directory: {0}", releaseDir);
                return;
            }
        }

        String libDirectory = releaseDirectory + "libs" + File.separator;
        File libDir = new File(libDirectory);
        if (!libDir.exists()) {
            if (!libDir.mkdirs()) {
                LOGGER.log(Level.SEVERE, "Failed to create directory: {0}", libDir);
                return;
            }
        }

        copyJarToRelease(artifactId, version, releaseDirectory);

        createNewScripts(libDirectory, releaseDirectory, artifactId, version);
    }

    private static void copyJarToRelease(String artifactId, String version, String releaseDirectory) {
        String sourceJarName = artifactId + "-" + version + "-shaded.jar";
        String destJarName = artifactId + "-" + version + ".jar";
        File sourceJar = new File("target" + File.separator + sourceJarName);
        File destJar = new File(releaseDirectory + destJarName);

        if (sourceJar.exists()) {
            try {
                Files.copy(sourceJar.toPath(), destJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                LOGGER.log(Level.INFO, "Copied {0} to release directory as {1}", new Object[]{sourceJarName, destJarName});
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to copy JAR file to release directory", e);
            }
        } else {
            LOGGER.log(Level.WARNING, "Source JAR not found: {0}", sourceJar.getAbsolutePath());
        }
    }

    public static void createNewScripts(String libDirectory, String releaseDirectory, String artifactId, String version) {
        String jarName = artifactId + "-" + version + ".jar";

        createStartScriptWindows(releaseDirectory);
        createStartScriptLinux(releaseDirectory);
        createStartParamsScriptWindows(jarName, libDirectory);
        createStartParamsScriptLinux(jarName, libDirectory);
        createRestartScriptWindows(libDirectory);
        createRestartScriptLinux(libDirectory);
    }

    public static void createNewScripts(String directory) {
        String artifactId = System.getProperty("artifactId");
        String version = System.getProperty("version");
        String jarName = artifactId + "-" + version + ".jar";
        if (jarName.equals("null-null.jar")) {
            Package pkg = DiscordBot.class.getPackage();
            artifactId = pkg.getImplementationTitle();
            version = pkg.getImplementationVersion();
            jarName = artifactId + "-" + version + ".jar";
        }

        String targetDirectory = directory + File.separator + "..";
        createStartScriptWindows(targetDirectory);
        createStartScriptLinux(targetDirectory);
        createStartParamsScriptWindows(jarName, directory);
        createStartParamsScriptLinux(jarName, directory);
        createRestartScriptWindows(directory);
        createRestartScriptLinux(directory);
    }

    private static void createStartScriptLinux(String directory) {
        try {
            File startScript = new File(directory, "start.sh");
            if (startScript.exists()) {
                if (!startScript.delete()) {
                    LOGGER.log(Level.WARNING, "Failed to delete existing start.sh script.");
                }
            }

            try (FileWriter fw = new FileWriter(startScript)) {
                fw.write("#!/bin/bash\n");
                fw.write("\n");
                fw.write("export LANG=\"en_US.UTF-8\"\n");
                fw.write("export LC_ALL=\"en_US.UTF-8\"\n");
                fw.write("\n");
                fw.write("export _JAVA_OPTIONS=\"-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false\"\n");
                fw.write("\n");
                fw.write("while true; do\n");
                fw.write("  clear\n");
                fw.write("  if [ -f \"./libs/start-params.sh\" ]; then\n");
                fw.write("    ./libs/start-params.sh\n");
                fw.write("  else\n");
                fw.write("    JAR_FILE=$(ls ./*.jar 2>/dev/null | head -n 1)\n");
                fw.write("    if [ -n \"$JAR_FILE\" ]; then\n");
                fw.write("      java -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false --enable-native-access=ALL-UNNAMED -jar \"$JAR_FILE\"\n");
                fw.write("    else\n");
                fw.write("      echo \"No start-params.sh or *.jar found in ./libs\" >&2\n");
                fw.write("      sleep 5\n");
                fw.write("    fi\n");
                fw.write("  fi\n");
                fw.write("  sleep 3\n");
                fw.write("done\n");
            }

            if (!startScript.setExecutable(true)) {
                LOGGER.log(Level.WARNING, "Failed to set start.sh as executable.");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred while creating start.sh", e);
        }
    }

    private static void createStartScriptWindows(String directory) {
        try {
            File startScript = new File(directory, "start.bat");
            if (startScript.exists()) {
                if (!startScript.delete()) {
                    LOGGER.log(Level.WARNING, "Failed to delete existing start.bat script.");
                }
            }

            try (FileWriter fw = new FileWriter(startScript)) {
                fw.write("@echo off\n");
                fw.write("setlocal\n");
                fw.write("\n");
                fw.write("set \"_JAVA_OPTIONS=-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false\"\n");
                fw.write("\n");
                fw.write(":loop\n");
                fw.write("cls\n");
                fw.write("if exist \"libs\\start-params.bat\" (\n");
                fw.write("    call \"libs\\start-params.bat\"\n");
                fw.write(") else (\n");
                fw.write("    for %%f in (.\\*.jar) do (\n");
                fw.write("        java -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false --enable-native-access=ALL-UNNAMED -jar \"%%f\"\n");
                fw.write("        goto loop_end\n");
                fw.write("    )\n");
                fw.write("    echo No start-params.bat or *.jar found in .\\libs >&2\n");
                fw.write("    timeout /t 5 >nul\n");
                fw.write(")\n");
                fw.write(":loop_end\n");
                fw.write("timeout /t 3 >nul\n");
                fw.write("goto loop\n");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred while creating start.bat", e);
        }
    }

    private static void createStartParamsScriptLinux(String jarName, String directory) {
        try {
            File startParamsScript = new File(directory, "start-params.sh");
            if (startParamsScript.exists()) {
                if (!startParamsScript.delete()) {
                    LOGGER.log(Level.WARNING, "Failed to delete existing start-params.sh script.");
                }
            }
            try (FileWriter fw = new FileWriter(startParamsScript)) {
                fw.write("#!/bin/bash\n");
                fw.write("exec java -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false --enable-native-access=ALL-UNNAMED -jar \"" + jarName + "\"\n");
            }
            if (!startParamsScript.setExecutable(true)) {
                LOGGER.log(Level.WARNING, "Failed to set start-params.sh as executable.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred while creating start-params.sh", e);
        }
    }

    private static void createStartParamsScriptWindows(String jarName, String directory) {
        try {
            File startParamsScript = new File(directory, "start-params.bat");
            if (startParamsScript.exists()) {
                if (!startParamsScript.delete()) {
                    LOGGER.log(Level.WARNING, "Failed to delete existing start-params.bat script.");
                }
            }
            try (FileWriter fw = new FileWriter(startParamsScript)) {
                fw.write("@echo off\n");
                fw.write("java -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false --enable-native-access=ALL-UNNAMED -jar \"" + jarName + "\"\n");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred while creating start-params.bat", e);
        }
    }


    private static void createRestartScriptLinux(String directory) {
        try {
            File restartScript = new File(directory, "restart.sh");
            if (restartScript.exists()) {
                if (!restartScript.delete()) {
                    LOGGER.log(Level.WARNING, "Failed to delete existing restart.sh script.");
                }
            }
            try (FileWriter fw = new FileWriter(restartScript)) {
                fw.write("#!/bin/bash\n");
                fw.write("pkill -f \"DiscordBot.*\\.jar\"\n");
                fw.write("clear\n");
            }
            if (!restartScript.setExecutable(true)) {
                LOGGER.log(Level.WARNING, "Failed to set restart.sh as executable.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred while creating restart.sh", e);
        }
    }

    private static void createRestartScriptWindows(String directory) {
        try {
            File restartScript = new File(directory, "restart.bat");
            if (restartScript.exists()) {
                if (!restartScript.delete()) {
                    LOGGER.log(Level.WARNING, "Failed to delete existing restart.bat script.");
                }
            }
            try (FileWriter fw = new FileWriter(restartScript)) {
                fw.write("@echo off\n");
                fw.write("for /f \"tokens=2\" %%i in ('wmic process where \"commandline like '%%DiscordBot%%.jar%%' and name='java.exe'\" get processid ^| findstr /r \"[0-9]\"') do taskkill /F /PID %%i\n");
                fw.write("cls\n");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "An error occurred while creating restart.bat", e);
        }
    }
}