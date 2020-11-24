package com.github.dexluthor.client.ui;

import com.github.dexluthor.client.ui.concurrent.SavingService;
import com.github.dexluthor.utils.ApplicationProperties;
import com.github.dexluthor.utils.FileCrawler;
import com.github.dexluthor.utils.Utils;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class MainController {
    private final DoubleProperty fileProgress = new SimpleDoubleProperty();
    private final DoubleProperty fileSizeProgress = new SimpleDoubleProperty();
    private final ApplicationProperties props = ApplicationProperties.INSTANCE;
    private final List<SavingService> services = new LinkedList<>();
    @FXML
    private ProgressBar progressBarFileLength;
    @FXML
    private ProgressBar progressBarFiles;
    @FXML
    private Label labelFilesPercent;
    @FXML
    private Label labelFileLength;
    @FXML
    private Label labelFrom;
    @FXML
    private Label labelTo;
    @FXML
    private Spinner<Integer> socketsSpinner;
    @FXML
    private Button startButton;
    private Socket managingSocket;
    private boolean isRunning;
    private int totalFileCount;
    private long totalFileSize;
    private CountDownLatch countDownLatch;

    @FXML
    void initialize() {
        labelFrom.setText(props.getSourceDir());
        labelTo.setText(props.getDestinationDir());
        SpinnerValueFactory<Integer> spinner = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12, Runtime.getRuntime().availableProcessors());
        socketsSpinner.setValueFactory(spinner);

        initListeners();
    }

    private void initListeners() {
        fileProgress.addListener((observable, oldValue, newValue) -> {
            labelFilesPercent.setText((int) ((newValue.floatValue() / totalFileCount) * 100) + "%");
            progressBarFiles.setProgress(newValue.doubleValue() / totalFileCount);
        });
        fileSizeProgress.addListener((observable, oldValue, newValue) -> {
            labelFileLength.setText(newValue.intValue() / (1024 * 1024) + "/" + totalFileSize / (1024 * 1024) + " MB");
            progressBarFileLength.setProgress(newValue.doubleValue() / totalFileSize);
        });
    }

    @FXML
    void onStartButtonClick(MouseEvent event) {
        props.setNumberOfSockets(socketsSpinner.getValue());

        if (!isRunning) {
            try {
                managingSocket = new Socket(props.getIP(), props.getPort());
                sendAndGetMeta();
                for (int i = 0; i < socketsSpinner.getValue(); i++) {
                    startSaving();
                }
            } catch (IOException e) {
                log.error("Connection refused");
                return;
            }
            isRunning = true;
            startButton.setText("Copying");
            startButton.setDisable(true);
        }
    }

    private void sendAndGetMeta() {
        countDownLatch = new CountDownLatch(socketsSpinner.getValue());
        try {
            val outputStream = new DataOutputStream(managingSocket.getOutputStream());
            val inputStream = new DataInputStream(managingSocket.getInputStream());

            outputStream.writeInt(socketsSpinner.getValue());// sockets number

            final Map<String, Long> pathDeliveredBytes = new HashMap<>();
            for (File file : FileCrawler.crawl(new File(props.getDestinationDir()))) {
                pathDeliveredBytes.put(file.getAbsolutePath(), file.length());
            }

            if (!pathDeliveredBytes.isEmpty()) {
                if (restartWindow()) {
                    outputStream.writeUTF("continue");
                    new ObjectOutputStream(managingSocket.getOutputStream())
                            .writeObject(pathDeliveredBytes);
                } else {
                    for (val file : Objects.requireNonNull(new File(props.getDestinationDir()).listFiles())) {
                        Utils.deleteDirOrFile(file);
                    }
                    outputStream.writeUTF("start");
                }
            } else {
                outputStream.writeUTF("start");
            }
            totalFileCount = inputStream.readInt();
            fileSizeProgress.set(inputStream.readLong());
            fileProgress.set(inputStream.readInt());
            totalFileSize = inputStream.readLong();
        } catch (IOException e) {
            e.printStackTrace();
        }
        changeButtonTextAfterFinish();
    }

    private void changeButtonTextAfterFinish() {
        new Service<Void>() {
            protected Task<Void> createTask() {
                return new Task<Void>() {
                    protected Void call() {
                        try {
                            countDownLatch.await();
                            Platform.runLater(() -> startButton.setText("Finished"));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }
                };
            }
        }.start();
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private boolean restartWindow() {
        Optional<ButtonType> button = new Alert(Alert.AlertType.CONFIRMATION, "Continue?").showAndWait();
        return "OK".equals(button.get().getText());
    }

    private void startSaving() throws IOException {
        Socket socket = new Socket(props.getIP(), props.getPort());
        SavingService service = new SavingService(socket, fileProgress, fileSizeProgress);
        services.add(service);

        service.setOnFailed(event -> {
            if (isRunning) {
                isRunning = false;
                startButton.setText("Restart");
                startButton.setDisable(false);
            }
        });
        service.setOnSucceeded(e -> countDownLatch.countDown());
        service.start();
    }

}
