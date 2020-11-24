package com.github.dexluthor.client.ui.concurrent;

import com.github.dexluthor.utils.ApplicationProperties;
import javafx.beans.property.DoubleProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.Socket;

import static javafx.application.Platform.runLater;

@Slf4j
public class SavingService extends Service<Void> {
    private final Socket socket;
    private final DoubleProperty sizeProgress;
    private final DoubleProperty fileProgress;
    private final ApplicationProperties props = ApplicationProperties.INSTANCE;

    public SavingService(Socket socket, DoubleProperty fileProgress, DoubleProperty sizeProgress) {
        this.socket = socket;
        this.sizeProgress = sizeProgress;
        this.fileProgress = fileProgress;
    }

    @Override
    protected Task<Void> createTask() {
        return new SavingTask();
    }

    class SavingTask extends Task<Void> {

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        protected Void call() {
            try {
                DataInputStream in = new DataInputStream(socket.getInputStream());
                while (true) {
                    long fileLength = in.readLong();
                    if (fileLength == -1) {
                        log.debug("Got poison pill");
                        break;
                    }
                    String fileName = in.readUTF();
                    File file = new File(fileName);

                    long totallyReadBytes = 0;
                    if (!file.exists()) {
                        file.getParentFile().mkdirs();
                        file.createNewFile();
                    } else {
                        totallyReadBytes = file.length();
                    }
                    try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                        raf.seek(totallyReadBytes);
                        byte[] buffer = new byte[props.getChunkSize()];
                        while (totallyReadBytes != fileLength) {
                            if (fileLength - totallyReadBytes < props.getChunkSize()) {
                                buffer = new byte[(int) (fileLength - totallyReadBytes)];
                            }
                            int read = in.read(buffer);
                            totallyReadBytes += read;
                            raf.write(buffer, 0, read);
                            log.trace("Got chunk of {}. Left {} MB", fileName, (fileLength - totallyReadBytes) / (1024 * 1024));
                            runLater(() -> sizeProgress.set(sizeProgress.get() + read));
                        }
                        runLater(() -> fileProgress.set(fileProgress.get() + 1));
                        log.info("Saved {}", fileName);
                    }
                }
            } catch (EOFException e) {
                log.warn("EOFException");
            } catch (IOException e) {
                if ("Connection reset".equals(e.getMessage())) {
                    log.warn("Server disconnected");
                    throw new RuntimeException();
                }
            }
            return null;
        }
    }
}
