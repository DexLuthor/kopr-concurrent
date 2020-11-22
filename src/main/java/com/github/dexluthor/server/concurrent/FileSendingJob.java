package com.github.dexluthor.server.concurrent;

import com.github.dexluthor.server.FileSender;
import com.github.dexluthor.utils.ApplicationProperties;
import com.github.dexluthor.utils.Pair;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class FileSendingJob implements Runnable {
    private final DataOutputStream outputStream;
    private final BlockingQueue<Pair<String, Long>> pathsBytes;
    private final FileSender fileSender;
    private final CountDownLatch countDownLatch;
    private final ApplicationProperties props = ApplicationProperties.INSTANCE;

    public FileSendingJob(DataOutputStream outputStream, BlockingQueue<Pair<String, Long>> pathsBytes, final FileSender fileSender, final CountDownLatch countDownLatch) {
        this.outputStream = outputStream;
        this.pathsBytes = pathsBytes;
        this.fileSender = fileSender;
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
        sendFiles();
        sendPoison();
    }

    private void sendFiles() {
        try {
            while (true) {
                final Pair<String, Long> takenPair;
                synchronized (pathsBytes) {
                    if (!pathsBytes.isEmpty()) {
                        takenPair = pathsBytes.take();
                    } else {
                        break;
                    }
                }
                File file = new File(takenPair.getKey());

                RandomAccessFile raf = null;
                try {
                    outputStream.writeLong(file.length());
                    outputStream.writeUTF(props.getDestinationDir() +
                            file.getAbsolutePath().substring(props.getSourceDir().length()));

                    byte[] buffer = new byte[props.getChunkSize()];
                    long totallyRead = takenPair.getValue();

                    raf = new RandomAccessFile(file, "r");
                    raf.seek(totallyRead);

                    while (totallyRead != file.length()) {
                        if (file.length() - totallyRead < props.getChunkSize()) {
                            buffer = new byte[(int) (file.length() - totallyRead)];
                        }
                        totallyRead += raf.read(buffer);

                        outputStream.write(buffer);
                        outputStream.flush();
                        log.trace("sent chunk of {}. Left {} MB", file.getName(), (file.length() - totallyRead) / (1024 * 1024));
                    }
                    log.info("{} sent", file.getName());
                } catch (EOFException e) {
                    log.warn("EOFException");
                } catch (IOException e) {
                    if ("Connection reset by peer".equals(e.getMessage())
                            || "Connection reset by peer: socket write error".equals(e.getMessage())) {
                        log.error("Connection reset by peer in FileSenderJob.sendFile");
                        synchronized (fileSender) {
                            if (fileSender.isRunning()) {
                                log.info("reconnect");
                                // fileSender.copyingState = CopyingState.INTERRUPTED;
                                fileSender.setIsRunning(false);
                                fileSender.reconnect();
                            }
                        }
                        break;
                    }
                } finally {
                    if (raf != null) {
                        try {
                            raf.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        countDownLatch.countDown();
    }

    private void sendPoison() {
        try {
            outputStream.writeLong(-1);
            log.debug("Poison sent");
            outputStream.close();
        } catch (SocketException e) {
            if ("Connection reset by peer".equals(e.getMessage())) {
                log.error("Connection reset by peer in FileSenderJob.sendPoison");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
