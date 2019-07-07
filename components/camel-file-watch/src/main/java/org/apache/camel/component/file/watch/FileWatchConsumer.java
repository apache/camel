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
import java.lang.reflect.Field;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.file.watch.constants.FileEvent;
import org.apache.camel.component.file.watch.constants.FileEventEnum;
import org.apache.camel.component.file.watch.utils.PathUtils;
import org.apache.camel.component.file.watch.utils.WatchServiceUtils;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.AntPathMatcher;

/**
 * The file-watch consumer.
 */
public class FileWatchConsumer extends DefaultConsumer {

    private WatchService watchService;
    private ExecutorService watchDirExecutorService;
    private ExecutorService pollExecutorService;
    private LinkedBlockingQueue<FileEvent> eventQueue;
    private Path baseDirectory;
    private AntPathMatcher antPathMatcher;
    private WatchEvent.Kind[] kinds;


    public FileWatchConsumer(FileWatchEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        if (getEndpoint().getQueueSize() <= 0) {
            eventQueue = new LinkedBlockingQueue<>();
        } else {
            eventQueue = new LinkedBlockingQueue<>(getEndpoint().getQueueSize());
        }

        antPathMatcher = new AntPathMatcher();
        baseDirectory = Paths.get(getEndpoint().getPath());

        Set<FileEventEnum> events = new HashSet<>(endpoint.getEvents());

        kinds = new WatchEvent.Kind[endpoint.getEvents().size() + 1];
        kinds[0] = StandardWatchEventKinds.OVERFLOW; //always watch Overflow event for logging purposes
        int i = 0;
        for (FileEventEnum fileEventEnum: events) {
            kinds[i + 1] = fileEventEnum.kind();
            i++;
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (!Files.exists(baseDirectory)) {
            if (getEndpoint().isAutoCreate()) {
                baseDirectory = Files.createDirectories(baseDirectory);
            } else {
                throw new RuntimeCamelException("Path must exists when autoCreate = false");
            }
        }

        if (!Files.isDirectory(baseDirectory)) {
            throw new RuntimeCamelException(String.format("Parameter path must be directory, %s given", baseDirectory.toString()));
        }

        watchService = baseDirectory.getFileSystem().newWatchService();

        watchDirExecutorService = getEndpoint().getCamelContext().getExecutorServiceManager()
            .newSingleThreadExecutor(this, "CamelFileWatchService");
        pollExecutorService = getEndpoint().getCamelContext().getExecutorServiceManager()
            .newFixedThreadPool(this, "CamelFileWatchPoll", getEndpoint().getConcurrentConsumers());

        register(baseDirectory);

        watchDirExecutorService.submit(new WatchServiceRunnable());

        for (int i = 0; i < getEndpoint().getConcurrentConsumers(); i++) {
            pollExecutorService.submit(new PollRunnable());
        }
    }

    private void register(Path path) throws IOException {
        boolean registered = false;
        if (WatchServiceUtils.isPollingWatchService(watchService)) {
            try {
               // Find enum value SensitivityWatchEventModifier.HIGH using reflection to avoid importing com.sun packages
                Class<?> sensitivityWatchEventModifierClass = getEndpoint().getCamelContext()
                    .getClassResolver().resolveClass("com.sun.nio.file.SensitivityWatchEventModifier");
                if (sensitivityWatchEventModifierClass != null) {
                    Field enumConstantField = sensitivityWatchEventModifierClass.getDeclaredField("HIGH");
                    WatchEvent.Modifier sensitivityModifier = (WatchEvent.Modifier) enumConstantField.get(null);

                    if (sensitivityModifier != null) {
                        path.register(watchService, kinds, sensitivityModifier);
                        registered = true;
                    }
                }
            } catch (IllegalAccessException | NoSuchFieldException ignored) {
                // This is expected on JVMs where PollingWatchService or SensitivityWatchEventModifier are not available
            }
        }

        if (!registered) {
            path.register(watchService, kinds);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (watchService != null) {
            try {
                watchService.close();
                log.info("WatchService closed");
            } catch (IOException e) {
                log.info("Cannot close WatchService", e);
            }
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
        Exchange exchange = getEndpoint().createExchange();
        File file = event.getEventPath().toFile();
        Message message = exchange.getIn();
        message.setBody(file);
        message.setHeader(FileWatchComponent.EVENT_TYPE_HEADER, event.getEventType());
        message.setHeader(Exchange.FILE_NAME_ONLY, event.getEventPath().getFileName().toString());
        message.setHeader("CamelFileAbsolute", true);

        final String absolutePath = PathUtils.normalizeToString(event.getEventPath().toAbsolutePath());
        message.setHeader("CamelFileAbsolutePath", absolutePath);
        message.setHeader(Exchange.FILE_PATH, absolutePath);

        final String relativePath = PathUtils.normalizeToString(baseDirectory.relativize(event.getEventPath()));
        message.setHeader(Exchange.FILE_NAME, relativePath);
        message.setHeader("CamelFileRelativePath", relativePath);
        message.setHeader(Exchange.FILE_NAME_CONSUMED, relativePath);

        message.setHeader(Exchange.FILE_PARENT, PathUtils.normalizeToString(event.getEventPath().getParent().toAbsolutePath()));
        message.setHeader(Exchange.FILE_LAST_MODIFIED, event.getEventDate());

        return exchange;
    }

    private boolean matchFilters(FileEvent fileEvent) {
        if (!getEndpoint().getEvents().contains(fileEvent.getEventType())) {
            return false;
        }

        String pattern = getEndpoint().getAntInclude();
        if (pattern == null || pattern.trim().isEmpty()) {
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

    class WatchServiceRunnable implements Runnable {
        WatchKey watchKey;

        @Override
        public void run() {
            while (take() && isRunAllowed() && !isStoppingOrStopped() && !isSuspendingOrSuspended()) {
                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    if (event.kind().equals(StandardWatchEventKinds.OVERFLOW)) {
                        log.warn("OVERFLOW occurred. Some events may be lost");
                        continue;
                    }

                    Path base = (Path) watchKey.watchable();
                    WatchEvent<Path> eventCast = cast(event);
                    FileEvent fileEvent = new FileEvent(FileEventEnum.valueOf(eventCast.kind()), base.resolve(eventCast.context()));

                    if (matchFilters(fileEvent)) {
                        eventQueue.offer(fileEvent);
                    }
                }
            }
        }

        private boolean take() {
            if (watchKey != null && !watchKey.reset()) {
                log.info("WatchDirRunnable stopping, because watchKey is in invalid state");
                return false;
            }
            try {
                watchKey = watchService.take();
                return true;
            } catch (ClosedWatchServiceException | InterruptedException e) {
                log.info("WatchDirRunnable stopping because " + e.getClass().getSimpleName() + ": " + e.getMessage());
                return false;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private WatchEvent<Path> cast(WatchEvent<?> event) {
        if (event != null && event.kind().type() == Path.class) {
            return (WatchEvent<Path>) event;
        } else {
            throw new ClassCastException("Cannot cast " + event + " to WatchEvent<Path>");
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
                    return;
                }

                if (event != null) {
                    try {
                        Exchange exchange = prepareExchange(event);
                        getProcessor().process(exchange);
                    } catch (Throwable t) {
                        handleException(t);
                    }
                }
            }
        }
    }


}
