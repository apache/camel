/**
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
package org.apache.camel.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.support.ReloadStrategySupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * A file based {@link org.apache.camel.spi.ReloadStrategy} which watches a file folder
 * for modified files and reload on file changes.
 * <p/>
 * This implementation uses the JDK {@link WatchService} to watch for when files are
 * created or modified. Mac OS X users should be noted the osx JDK does not support
 * native file system changes and therefore the watch service is much slower than on
 * linux or windows systems.
 */
public class FileWatcherReloadStrategy extends ReloadStrategySupport {

    private String folder;
    private ExecutorService executorService;

    public FileWatcherReloadStrategy() {
    }

    public FileWatcherReloadStrategy(String directory) {
        setFolder(directory);
    }


    public void setFolder(String folder) {
        this.folder = folder;
    }

    @Override
    protected void doStart() throws Exception {
        if (folder == null) {
            // no folder configured
            return;
        }

        File dir = new File(folder);
        if (dir.exists() && dir.isDirectory()) {
            log.info("Starting ReloadStrategy to watch directory: {}", dir);
            try {
                Path path = dir.toPath();
                WatchService watcher = path.getFileSystem().newWatchService();
                // we cannot support deleting files as we don't know which routes that would be
                path.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);

                executorService = getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "FileWatcherReloadStrategy");
                executorService.submit(new WatchFileChangesTask(watcher, path));
            } catch (IOException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
    }


    @Override
    protected void doStop() throws Exception {
        if (executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
            executorService = null;
        }
    }

    /**
     * Background task which watches for file changes
     */
    protected class WatchFileChangesTask implements Runnable {

        private final WatchService watcher;
        private final Path folder;

        public WatchFileChangesTask(WatchService watcher, Path folder) {
            this.watcher = watcher;
            this.folder = folder;
        }

        public void run() {
            log.debug("ReloadStrategy is starting watching folder: {}", folder);

            // allow to run while starting Camel
            while (isStarting() || isRunAllowed()) {
                WatchKey key;
                try {
                    log.trace("ReloadStrategy is polling for file changes in directory: {}", folder);
                    // wait for a key to be available
                    key = watcher.poll(2, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    break;
                }

                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent<Path> we = (WatchEvent<Path>) event;
                        Path path = we.context();
                        String name = folder.resolve(path).toAbsolutePath().toFile().getAbsolutePath();
                        log.trace("Modified/Created file: {}", name);

                        // must be an .xml file
                        if (name.toLowerCase(Locale.US).endsWith(".xml")) {
                            log.debug("Modified/Created XML file: {}", name);
                            try {
                                FileInputStream fis = new FileInputStream(name);
                                onReloadRoutes(getCamelContext(), name, fis);
                                IOHelper.close(fis);
                            } catch (Exception e) {
                                log.warn("Error reloading routes from file: " + name + " due " + e.getMessage() + ". This exception is ignored.", e);
                            }
                        }
                    }

                    // the key must be reset after processed
                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                }
            }

            log.info("ReloadStrategy is stopping watching folder: {}", folder);
        }
    }

}
