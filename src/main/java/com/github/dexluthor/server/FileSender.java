package com.github.dexluthor.server;

import com.github.dexluthor.server.concurrent.FileSendingJob;
import com.github.dexluthor.utils.ApplicationProperties;
import com.github.dexluthor.utils.FileCrawler;
import com.github.dexluthor.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.dexluthor.utils.Utils.unsentIntersection;

@Slf4j
public class FileSender {
    private final ApplicationProperties props = ApplicationProperties.INSTANCE;
    private final List<Socket> sockets = new LinkedList<>();
    private final BlockingQueue<Pair<String, Long>> filesToSend = new LinkedBlockingQueue<>();
    private final AtomicBoolean isRunning = new AtomicBoolean();
    private ConcurrentMap<String, Long> filePathToBytes = new ConcurrentHashMap<>();
    private Socket managingSocket;
    private ServerSocket serverSocket;
    private ExecutorService executor;

    private long totalMb, actualMb;
    private int totalFileCount;

    public boolean isRunning() {
        return isRunning.get();
    }

    public void setIsRunning(boolean running) {
        isRunning.set(running);
    }

    public FileSender crawl(File fileToCrawl) {
        log.trace("started crawling");

        final List<File> files = FileCrawler.crawl(fileToCrawl);
        for (final File file : files) {
            totalFileCount++;
            totalMb += file.length();
            filePathToBytes.put(file.getAbsolutePath(), 0L);
        }

        log.trace("finished crawling");
        return this;
    }

    public FileSender connect() {
        if (!isRunning.get()) {
            isRunning.set(true);
            try {
                serverSocket = new ServerSocket(props.getPort());
                log.info("Opening server socket " + serverSocket);

                connectManagingSocket();
                getAndSendMeta();
                connectConsumers();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return this;
    }

    private void connectManagingSocket() throws IOException {
        log.debug("Waiting for managing socket from client");
        managingSocket = serverSocket.accept();
        log.debug("Managing socket connected");
    }

    @SuppressWarnings("unchecked")
    private void getAndSendMeta() {
        try {
            val inputStream = new DataInputStream(managingSocket.getInputStream());
            val outputStream = new DataOutputStream(managingSocket.getOutputStream());

            props.setNumberOfSockets(inputStream.readInt());                                         // number of sockets
            final String continueOrStart = inputStream.readUTF();                                    // continue or start

            var mapFromClient = Collections.<String, Long>emptyMap();
            if ("continue".equalsIgnoreCase(continueOrStart)) {
                mapFromClient = (Map<String, Long>) new ObjectInputStream(managingSocket.getInputStream()).readObject();

                actualMb = mapFromClient.values().stream().reduce(Long::sum).orElse(0L);

                filePathToBytes = new ConcurrentHashMap<>(unsentIntersection(filePathToBytes, mapFromClient));
            }
            filesToSend.addAll(Pair.ofMap(filePathToBytes));
            outputStream.writeInt(totalFileCount);                          // total files
            outputStream.writeLong(actualMb);                               // actual mb
            outputStream.writeInt(totalFileCount - filesToSend.size());  // actual files
            outputStream.writeLong(totalMb);                                // total mb
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            log.error("Internal error", e);
        }
    }

    private void connectConsumers() throws IOException {
        sockets.clear();
        for (int i = 0; i < props.getNumberOfSockets(); i++) {
            log.debug("Waiting for client socket connection");
            sockets.add(serverSocket.accept());
            log.debug("Client socket connected");
        }
    }

    public synchronized void reconnect() {
        if (!isRunning.get()) {
            try {
                isRunning.set(true);
                executor.shutdown();
                connectManagingSocket();
                getAndSendMeta();
                connectConsumers();
                send();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void send() {
        final CountDownLatch countDownLatch = new CountDownLatch(props.getNumberOfSockets());
        executor = Executors.newFixedThreadPool(props.getNumberOfSockets());
        try {
            for (Socket socket : sockets) {
                DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
                executor.execute(new FileSendingJob(dataOutputStream, filesToSend, this, countDownLatch));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            countDownLatch.await();
            System.exit(0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}