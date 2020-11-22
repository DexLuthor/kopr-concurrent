package com.github.dexluthor.utils;

import com.github.dexluthor.server.exceptions.DirectoryForbiddenException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class FileCrawler {
    public static List<File> crawl(File file) {
        ForkJoinPool forkJoinPool = new ForkJoinPool();

        final List<File> filePathToDeliveredBytes =
                forkJoinPool.invoke(new FileCrawlingTask(file));

        forkJoinPool.shutdown();
        return filePathToDeliveredBytes;
    }

    @Slf4j
    static class FileCrawlingTask extends RecursiveTask<List<File>> {
        private final File currentDir;

        public FileCrawlingTask(final File fileToSend) {
            if (fileToSend == null || !fileToSend.exists()) {
                throw new IllegalArgumentException();
            }
            this.currentDir = fileToSend;
        }

        @Override
        protected List<File> compute() {
            File[] content = currentDir.listFiles();
            if (content == null) {
                throw new DirectoryForbiddenException("Directory is not accessible", currentDir);
            }
            final List<FileCrawlingTask> tasks = new LinkedList<>();
            final List<File> files = new LinkedList<>();

            for (final File currentFile : content) {
                if (currentFile.isFile()) {
                    files.add(currentFile);
                    log.debug("Found " + currentFile.getAbsolutePath());
                }
                if (currentFile.isDirectory()) {
                    final FileCrawlingTask task = new FileCrawlingTask(currentFile);
                    task.fork();
                    tasks.add(task);
                }
            }
            for (final FileCrawlingTask task : tasks) {
                files.addAll(task.join());
            }
            return files;
        }
    }
}
