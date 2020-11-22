package com.github.dexluthor.server;

import com.github.dexluthor.utils.ApplicationProperties;

import java.io.File;

public class ServerLauncher {
    public static void main(String[] args) {
        new FileSender()
                .crawl(new File(ApplicationProperties.INSTANCE.getSourceDir()))
                .connect()
                .send();
    }
}
