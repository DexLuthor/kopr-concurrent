package com.github.dexluthor.utils;

import java.io.IOException;
import java.util.Properties;

public enum ApplicationProperties {
    INSTANCE;

    static final Properties properties;

    static {
        properties = new Properties();
        try {
            properties.load(ApplicationProperties.class
                    .getClassLoader().getResourceAsStream("conf/application.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getPort() {
        return Integer.parseInt(properties.getProperty("server.port"));
    }

    public String getIP() {
        return properties.getProperty("server.ip");
    }

    public String getDestinationDir() {
        return properties.getProperty("destinationDirectory");
    }

    public String getSourceDir() {
        return properties.getProperty("sourceDirectory");
    }

    public int getNumberOfSockets() {
        return Integer.parseInt(properties.getProperty("numberOfSockets"));
    }

    public void setNumberOfSockets(int n) {
        properties.setProperty("numberOfSockets", String.valueOf(n));
    }

    public int getChunkSize() {
        return Integer.parseInt(properties.getProperty("chunkSize"));
    }
}
