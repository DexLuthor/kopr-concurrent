package com.github.dexluthor.utils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Utils {
    public static Map<String, Long> unsentIntersection(Map<String, Long> server, Map<String, Long> client) {
        final Map<String, Long> unsent = new HashMap<>();
        final ApplicationProperties props = ApplicationProperties.INSTANCE;

        //1. unsent
        for (final Map.Entry<String, Long> entry : server.entrySet()) {
            String serverToClientPath = props.getDestinationDir().replace('/', '\\') +
                    entry.getKey().substring(props.getSourceDir().length());

            if (!client.containsKey(serverToClientPath)) {
                unsent.put(entry.getKey(), entry.getValue());
            }
        }

        //2. not completely delivered
        for (final Map.Entry<String, Long> entry : client.entrySet()) {
            String clientToServerPath = props.getSourceDir().replace('/', '\\') +
                    entry.getKey().substring(props.getDestinationDir().length());

            if (entry.getValue() < new File(clientToServerPath).length()) {
                unsent.put(clientToServerPath, entry.getValue());
            }
        }

        return unsent;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void deleteDirOrFile(File file) {
        if (file.isDirectory()) {
            final File[] files = file.listFiles();
            if (files != null) {
                for (final File f : files) {
                    deleteDirOrFile(f);
                }
            }
        }
        file.delete();
    }
}
