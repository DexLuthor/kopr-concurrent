package com.github.dexluthor.server;

import com.github.dexluthor.server.concurrent.FileSendingJob;
import com.github.dexluthor.utils.ApplicationProperties;
import com.github.dexluthor.utils.FileCrawler;
import com.github.dexluthor.utils.Pair;
import com.github.dexluthor.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class FileSender {
    private final List<Socket> sockets = new LinkedList<>();
    private final List<File> allFiles = new LinkedList<>();
    private final BlockingQueue<Pair<String, Long>> filesFromServer = new LinkedBlockingQueue<>();
    private final ApplicationProperties props = ApplicationProperties.INSTANCE;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ConcurrentMap<String, Long> filePathToBytes = new ConcurrentHashMap<>();
    private Socket managingSocket;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private CountDownLatch countDownLatch;

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
            allFiles.add(file);
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
            val inputStream = new DataInputStream(managingSocket.getInputStream());// sockets num
            val outputStream = new DataOutputStream(managingSocket.getOutputStream());

            props.setNumberOfSockets(inputStream.readInt());                                         // number of sockets
            final String continueOrStart = inputStream.readUTF();                                    // continue or start
            if ("continue".equalsIgnoreCase(continueOrStart)) {
                val objectInputStream = new ObjectInputStream(managingSocket.getInputStream()); // map
                filePathToBytes = new ConcurrentHashMap<>(
                        Utils.unsentIntersection(filePathToBytes, (Map<String, Long>) objectInputStream.readObject())
                );
                filesFromServer.addAll(Pair.ofMap(filePathToBytes));
            } else if ("start".equalsIgnoreCase(continueOrStart)) {
                filesFromServer.addAll(Pair.ofMap(filePathToBytes));
            }
            outputStream.writeInt(filesFromServer.size());
            outputStream.writeLong(filesFromServer // actual
                    .stream()
                    .peek(e -> System.out.println("Pair: " + e))
                    .map(Pair::getValue)
                    .reduce(Long::sum)
                    .get()
            );
            outputStream.writeLong(allFiles // total
                    .stream()
                    .map(File::length)
                    .reduce(Long::sum)
                    .get()
            );
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
        countDownLatch = new CountDownLatch(props.getNumberOfSockets());
        executor = Executors.newFixedThreadPool(props.getNumberOfSockets());
        try {
            for (Socket socket : sockets) {
                DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
                executor.execute(new FileSendingJob(dataOutputStream, filesFromServer, this, countDownLatch));
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