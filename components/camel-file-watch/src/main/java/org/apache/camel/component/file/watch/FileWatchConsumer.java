/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.watch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import io.methvin.watcher.DirectoryWatcher;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.file.watch.constants.FileEvent;
import org.apache.camel.component.file.watch.constants.FileEventEnum;
import org.apache.camel.component.file.watch.utils.PathUtils;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.AntPathMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The file-watch consumer.
 */
public class FileWatchConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(FileWatchConsumer.class);

    private final Set<FileEventEnum> events = new HashSet<>();
    private ExecutorService watchDirExecutorService;
    private ExecutorService pollExecutorService;
    private LinkedBlockingQueue<FileEvent> eventQueue;
    private Path baseDirectory;
    private AntPathMatcher antPathMatcher;
    private DirectoryWatcher watcher;

    public FileWatchConsumer(FileWatchEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        if (getEndpoint().getQueueSize() <= 0) {
            eventQueue = new LinkedBlockingQueue<>();
        } else {
            eventQueue = new LinkedBlockingQueue<>(getEndpoint().getQueueSize());
        }

        antPathMatcher = new AntPathMatcher();
        baseDirectory = Paths.get(getEndpoint().getPath()).toAbsolutePath();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        for (String event : getEndpoint().getEvents().split(",")) {
            FileEventEnum fe = FileEventEnum.valueOf(event);
            events.add(fe);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (!Files.exists(baseDirectory)) {
            if (getEndpoint().isAutoCreate()) {
                baseDirectory = Files.createDirectories(baseDirectory);
            } else {
                throw new IllegalArgumentException("Path must exists when autoCreate = false");
            }
        }

        if (!Files.isDirectory(baseDirectory)) {
            throw new IllegalArgumentException(
                    String.format("Parameter path must be directory, %s given", baseDirectory.toString()));
        }

        DirectoryWatcher.Builder watcherBuilder = DirectoryWatcher.builder()
                .path(this.baseDirectory)
                .logger(LOG)
                .listener(new FileWatchDirectoryChangeListener());

        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            // If not macOS, use FileSystem WatchService. io.methvin.watcher uses by default WatchService associated to default FileSystem.
            // We need per FileSystem WatchService, to allow monitoring on machine with multiple file systems.
            // Keep default for macOS
            watcherBuilder.watchService(this.baseDirectory.getFileSystem().newWatchService());
        }

        watcherBuilder.fileHashing(getEndpoint().isUseFileHashing());
        if (getEndpoint().getFileHasher() != null && getEndpoint().isUseFileHashing()) {
            watcherBuilder.fileHasher(getEndpoint().getFileHasher());
        }

        this.watcher = watcherBuilder.build();

        watchDirExecutorService = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newFixedThreadPool(this, "CamelFileWatchService", getEndpoint().getPollThreads());
        pollExecutorService = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newFixedThreadPool(this, "CamelFileWatchPoll", getEndpoint().getConcurrentConsumers());

        for (int i = 0; i < getEndpoint().getPollThreads(); i++) {
            this.watcher.watchAsync(watchDirExecutorService);
        }
        for (int i = 0; i < getEndpoint().getConcurrentConsumers(); i++) {
            pollExecutorService.submit(new PollRunnable());
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (this.watcher != null) {
            this.watcher.close();
        }
        if (watchDirExecutorService != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(watchDirExecutorService);
        }
        if (pollExecutorService != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(pollExecutorService);
        }
        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        doStop();
    }

    @Override
    protected void doResume() throws Exception {
        doStart();
    }

    private Exchange prepareExchange(FileEvent event) {
        Exchange exchange = createExchange(true);
        File file = event.getEventPath().toFile();
        Message message = exchange.getIn();
        message.setBody(file);
        message.setHeader(FileWatchConstants.EVENT_TYPE_HEADER, event.getEventType().name());
        message.setHeader(FileWatchConstants.FILE_NAME_ONLY, event.getEventPath().getFileName().toString());
        message.setHeader(FileWatchConstants.FILE_ABSOLUTE, true);

        final String absolutePath = PathUtils.normalizeToString(event.getEventPath().toAbsolutePath());
        message.setHeader(FileWatchConstants.FILE_ABSOLUTE_PATH, absolutePath);
        message.setHeader(FileWatchConstants.FILE_PATH, absolutePath);

        final String relativePath = PathUtils.normalizeToString(baseDirectory.relativize(event.getEventPath()));
        message.setHeader(FileWatchConstants.FILE_NAME, relativePath);
        message.setHeader(FileWatchConstants.FILE_RELATIVE_PATH, relativePath);
        message.setHeader(FileWatchConstants.FILE_NAME_CONSUMED, relativePath);

        message.setHeader(FileWatchConstants.FILE_PARENT,
                PathUtils.normalizeToString(event.getEventPath().getParent().toAbsolutePath()));
        message.setHeader(FileWatchConstants.FILE_LAST_MODIFIED, event.getEventDate());
        message.setHeader(Exchange.MESSAGE_TIMESTAMP, event.getEventDate());

        return exchange;
    }

    private boolean matchFilters(FileEvent fileEvent) {
        if (!events.isEmpty() && !events.contains(fileEvent.getEventType())) {
            return false;
        }

        if (!getEndpoint().isRecursive()) {
            // On some platforms (eg macOS) is WatchService always recursive,
            // so we need to filter this out to make this component platform independent
            try {
                if (!Files.isSameFile(fileEvent.getEventPath().getParent(), this.baseDirectory)) {
                    return false;
                }
            } catch (IOException e) {
                LOG.warn(String.format("Exception occurred during executing filter. Filtering file %s out.",
                        fileEvent.getEventPath()), e);
                return false;
            }
        }

        String pattern = getEndpoint().getAntInclude();
        if (pattern == null || pattern.isBlank()) {
            return true;
        }

        return antPathMatcher.match(
                getEndpoint().getAntInclude(),
                PathUtils.normalizeToString(baseDirectory.relativize(fileEvent.getEventPath())) // match against relativized path
        );
    }

    @Override
    public FileWatchEndpoint getEndpoint() {
        return (FileWatchEndpoint) super.getEndpoint();
    }

    class FileWatchDirectoryChangeListener implements DirectoryChangeListener {
        @Override
        public void onEvent(DirectoryChangeEvent directoryChangeEvent) {
            if (directoryChangeEvent.eventType() == DirectoryChangeEvent.EventType.OVERFLOW) {
                LOG.warn("OVERFLOW occurred, some events may be lost. Consider increasing of option 'pollThreads'");
                return;
            }
            FileEvent fileEvent = new FileEvent(directoryChangeEvent);
            if (matchFilters(fileEvent)) {
                eventQueue.offer(fileEvent);
            }
        }

        @Override
        public boolean isWatching() {
            return !isStoppingOrStopped() && !isSuspendingOrSuspended();
        }

        @Override
        public void onException(Exception e) {
            handleException(e);
        }
    }

    class PollRunnable implements Runnable {
        @Override
        public void run() {
            while (!isStoppingOrStopped() && !isSuspendingOrSuspended()) {
                FileEvent event;
                try {
                    event = eventQueue.poll(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (event != null) {
                    try {
                        Exchange exchange = prepareExchange(event);
                        getProcessor().process(exchange);
                    } catch (Exception t) {
                        handleException(t);
                    }
                }
            }
        }
    }
}
