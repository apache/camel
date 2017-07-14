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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.support.ReloadStrategySupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A file based {@link org.apache.camel.spi.ReloadStrategy} which watches a file folder
 * for modified files and reload on file changes.
 * <p/>
 * This implementation uses the JDK {@link WatchService} to watch for when files are
 * created or modified. Mac OS X users should be noted the osx JDK does not support
 * native file system changes and therefore the watch service is much slower than on
 * linux or windows systems.
 */
@ManagedResource(description = "Managed FileWatcherReloadStrategy")
public class FileWatcherReloadStrategy extends ReloadStrategySupport {

    private String folder;
    private boolean isRecursive;
    private ExecutorService executorService;
    private WatchFileChangesTask task;
    private Map<WatchKey, Path> folderKeys;
    private long pollTimeout = 2000;

    public FileWatcherReloadStrategy() {
        setRecursive(false);
    }

    public FileWatcherReloadStrategy(String directory) {
        setFolder(directory);
        setRecursive(false);
    }
    
    public FileWatcherReloadStrategy(String directory, boolean isRecursive) {
        setFolder(directory);
        setRecursive(isRecursive);
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }
    
    public void setRecursive(boolean isRecursive) {
        this.isRecursive = isRecursive;
    }

    /**
     * Sets the poll timeout in millis. The default value is 2000.
     */
    public void setPollTimeout(long pollTimeout) {
        this.pollTimeout = pollTimeout;
    }

    @ManagedAttribute(description = "Folder being watched")
    public String getFolder() {
        return folder;
    }
    
    @ManagedAttribute(description = "Whether the reload strategy watches directory recursively")
    public boolean isRecursive() {
        return isRecursive;
    }

    @ManagedAttribute(description = "Whether the watcher is running")
    public boolean isRunning() {
        return task != null && task.isRunning();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (folder == null) {
            // no folder configured
            return;
        }

        File dir = new File(folder);
        if (dir.exists() && dir.isDirectory()) {
            log.info("Starting ReloadStrategy to watch directory: {}", dir);

            WatchEvent.Modifier modifier = null;

            // if its mac OSX then attempt to apply workaround or warn its slower
            String os = ObjectHelper.getSystemProperty("os.name", "");
            if (os.toLowerCase(Locale.US).startsWith("mac")) {
                // this modifier can speedup the scanner on mac osx (as java on mac has no native file notification integration)
                Class<WatchEvent.Modifier> clazz = getCamelContext().getClassResolver().resolveClass("com.sun.nio.file.SensitivityWatchEventModifier", WatchEvent.Modifier.class);
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
                    log.info("On Mac OS X the JDK WatchService is slow by default so enabling SensitivityWatchEventModifier.HIGH as workaround");
                } else {
                    log.warn("On Mac OS X the JDK WatchService is slow and it may take up till 10 seconds to notice file changes");
                }
            }

            try {
                Path path = dir.toPath();
                WatchService watcher = path.getFileSystem().newWatchService();
                // we cannot support deleting files as we don't know which routes that would be
                if (isRecursive) {
                    this.folderKeys = new HashMap<WatchKey, Path>();
                    registerRecursive(watcher, path, modifier);
                } else {
                    registerPathToWatcher(modifier, path, watcher);
                }                

                task = new WatchFileChangesTask(watcher, path);

                executorService = getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "FileWatcherReloadStrategy");
                executorService.submit(task);
            } catch (IOException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
    }

    private WatchKey registerPathToWatcher(WatchEvent.Modifier modifier, Path path, WatchService watcher) throws IOException {
        WatchKey key;
        if (modifier != null) {
            key = path.register(watcher, new WatchEvent.Kind<?>[]{ENTRY_CREATE, ENTRY_MODIFY}, modifier);
        } else {
            key = path.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);
        }
        return key;
    }
    
    private void registerRecursive(final WatchService watcher, final Path root, final WatchEvent.Modifier modifier) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                WatchKey key = registerPathToWatcher(modifier, dir, watcher);
                folderKeys.put(key, dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

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
        private volatile boolean running;

        public WatchFileChangesTask(WatchService watcher, Path folder) {
            this.watcher = watcher;
            this.folder = folder;
        }

        public boolean isRunning() {
            return running;
        }

        public void run() {
            log.debug("ReloadStrategy is starting watching folder: {}", folder);

            // allow to run while starting Camel
            while (isStarting() || isRunAllowed()) {
                running = true;

                WatchKey key;
                try {
                    log.trace("ReloadStrategy is polling for file changes in directory: {}", folder);
                    // wait for a key to be available
                    key = watcher.poll(pollTimeout, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    break;
                }

                if (key != null) {
                    Path pathToReload = null;
                    if (isRecursive) {
                        pathToReload = folderKeys.get(key);
                    } else {
                        pathToReload = folder;
                    }
                    
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent<Path> we = (WatchEvent<Path>) event;
                        Path path = we.context();
                        String name = pathToReload.resolve(path).toAbsolutePath().toFile().getAbsolutePath();
                        log.trace("Modified/Created file: {}", name);

                        // must be an .xml file
                        if (name.toLowerCase(Locale.US).endsWith(".xml")) {
                            log.debug("Modified/Created XML file: {}", name);
                            try {
                                FileInputStream fis = new FileInputStream(name);
                                onReloadXml(getCamelContext(), name, fis);
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

            running = false;

            log.info("ReloadStrategy is stopping watching folder: {}", folder);
        }
    }

}
