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
package org.apache.camel.component.stream;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * To watch for file changes/rollover via JDK file watcher API. This is used to know for example of streaming from a
 * file, that gets rolled-over, so we know about this, and can begin reading the file again from the beginning.
 */
public class FileWatcherStrategy extends ServiceSupport implements CamelContextAware {

    @FunctionalInterface
    public interface OnChangeEvent {

        void onChange(File file);

    }

    private static final Logger LOG = LoggerFactory.getLogger(FileWatcherStrategy.class);
    private CamelContext camelContext;
    private final String directory;
    private final OnChangeEvent onChangeEvent;
    private WatchService watcher;
    private ExecutorService executorService;
    private WatchFileChangesTask task;
    private long pollTimeout = 1000;

    public FileWatcherStrategy(String directory, OnChangeEvent onChangeEvent) {
        this.directory = directory;
        this.onChangeEvent = onChangeEvent;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public long getPollTimeout() {
        return pollTimeout;
    }

    /**
     * Sets the poll timeout in millis. The default value is 1000.
     */
    public void setPollTimeout(long pollTimeout) {
        this.pollTimeout = pollTimeout;
    }

    @Override
    protected void doStart() throws Exception {
        if (directory == null) {
            // no folder configured
            return;
        }

        File dir = new File(directory);
        if (dir.exists() && dir.isDirectory()) {
            LOG.info("Starting ReloadStrategy to watch directory: {}", dir);

            WatchEvent.Modifier modifier = null;

            // if its mac OSX then attempt to apply workaround or warn its slower
            String os = ObjectHelper.getSystemProperty("os.name", "");
            if (os.toLowerCase(Locale.US).startsWith("mac")) {
                // this modifier can speedup the scanner on mac osx (as java on mac has no native file notification integration)
                Class<WatchEvent.Modifier> clazz = getCamelContext().getClassResolver()
                        .resolveClass("com.sun.nio.file.SensitivityWatchEventModifier", WatchEvent.Modifier.class);
                if (clazz != null) {
                    WatchEvent.Modifier[] modifiers = clazz.getEnumConstants();
                    for (WatchEvent.Modifier mod : modifiers) {
                        if ("HIGH".equals(mod.name())) {
                            modifier = mod;
                            break;
                        }
                    }
                }
                if (modifier != null) {
                    LOG.info(
                            "On Mac OS X the JDK WatchService is slow by default so enabling SensitivityWatchEventModifier.HIGH as workaround");
                } else {
                    LOG.warn(
                            "On Mac OS X the JDK WatchService is slow and it may take up till 10 seconds to notice file changes");
                }
            }

            try {
                Path path = dir.toPath();
                watcher = path.getFileSystem().newWatchService();
                registerPathToWatcher(modifier, path, watcher);

                task = new WatchFileChangesTask(watcher, path, onChangeEvent);

                executorService
                        = getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "FileWatcherStrategy");
                executorService.submit(task);
            } catch (IOException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    private WatchKey registerPathToWatcher(WatchEvent.Modifier modifier, Path path, WatchService watcher) throws IOException {
        WatchKey key;
        if (modifier != null) {
            key = path.register(watcher, new WatchEvent.Kind<?>[] { ENTRY_CREATE, ENTRY_MODIFY }, modifier);
        } else {
            key = path.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);
        }
        return key;
    }

    @Override
    protected void doStop() throws Exception {
        if (executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
            executorService = null;
        }
        if (watcher != null) {
            IOHelper.close(watcher);
        }
    }

    /**
     * Background task which watches for file changes
     */
    protected class WatchFileChangesTask implements Runnable {

        private final WatchService watcher;
        private final Path folder;
        private volatile boolean running;
        private OnChangeEvent changeEvent;

        public WatchFileChangesTask(WatchService watcher, Path folder, OnChangeEvent changeEvent) {
            this.watcher = watcher;
            this.folder = folder;
            this.changeEvent = changeEvent;
        }

        public boolean isRunning() {
            return running;
        }

        @Override
        public void run() {
            LOG.debug("FileWatcherStrategy is starting watching folder: {}", folder);

            // allow to run while starting Camel
            while (isStarting() || isRunAllowed()) {
                running = true;

                WatchKey key;
                try {
                    LOG.trace("FileWatcherStrategy is polling for file changes in directory: {}", folder);
                    // wait for a key to be available
                    key = watcher.poll(pollTimeout, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    break;
                }

                if (key != null) {

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent<Path> we = (WatchEvent<Path>) event;
                        Path path = we.context();
                        File file = folder.resolve(path).toFile();
                        LOG.trace("Modified/Created/Deleted file: {}", file);
                        changeEvent.onChange(file);
                    }

                    // the key must be reset after processed
                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                }
            }

            running = false;

            LOG.info("FileWatcherStrategy is stopping watching folder: {}", folder);
        }
    }

}
