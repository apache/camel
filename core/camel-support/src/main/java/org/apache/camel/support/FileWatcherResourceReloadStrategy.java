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
package org.apache.camel.support;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.CompileStrategy;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceReload;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A file based {@link org.apache.camel.spi.ResourceReloadStrategy} which watches a file folder for modified files and
 * reload on file changes.
 * <p/>
 * The folder is scanned on an interval and each file's modification time and size compared with the previous scan, the
 * way the file component finds changed files. A scan therefore reports all the files of one save together, which is
 * what a reload needs: a route is built with the properties of the moment, so a route and a property saved together
 * must be reloaded together (CAMEL-25032). The JDK {@link java.nio.file.WatchService} reports one file at a time with
 * no batch boundary, and on macOS has no native backend at all, where it fell back to a poll of about ten seconds
 * (CAMEL-25041).
 */
@ManagedResource(description = "Managed FileWatcherResourceReloadStrategy")
public class FileWatcherResourceReloadStrategy extends ResourceReloadStrategySupport {

    private static final Logger LOG = LoggerFactory.getLogger(FileWatcherResourceReloadStrategy.class);

    ExecutorService executorService;
    WatchFileChangesTask task;
    /** The modification time and length of every file of the previous scan, by path: what a change is measured from. */
    final Map<String, long[]> known = new HashMap<>();
    /** The compile work directory, resolved once at start (null when there is none). */
    Path compileWorkDir;
    FileFilter fileFilter;
    String folder;
    boolean isRecursive;
    boolean scheduler = true;
    long pollTimeout = 1000;
    /**
     * A file modified less than this ago is left for the next scan: a save still being written would otherwise be
     * reloaded half-finished. The file component leaves a file alone the same way.
     */
    long stableTimeout = 200;

    public FileWatcherResourceReloadStrategy() {
        setRecursive(false);
    }

    public FileWatcherResourceReloadStrategy(String directory) {
        setFolder(directory);
        setRecursive(false);
    }

    public FileWatcherResourceReloadStrategy(String directory, boolean isRecursive) {
        setFolder(directory);
        setRecursive(isRecursive);
    }

    public void setFolder(String folder) {
        // clip file: prefix if mistakenly specified
        if (folder != null && folder.startsWith("file:")) {
            folder = folder.substring(5);
        }
        this.folder = folder;
    }

    public void setRecursive(boolean isRecursive) {
        this.isRecursive = isRecursive;
    }

    public void setScheduler(boolean scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Sets how often the folder is scanned for changed files, in millis. The default value is 1000.
     */
    public void setPollTimeout(long pollTimeout) {
        this.pollTimeout = pollTimeout;
    }

    /**
     * Sets how long a file must have been unchanged before a scan reports it, in millis. The default value is 200, so
     * that a save still being written is reloaded once it is complete and not half-finished.
     */
    public void setStableTimeout(long stableTimeout) {
        this.stableTimeout = stableTimeout;
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

    public FileFilter getFileFilter() {
        return fileFilter;
    }

    /**
     * To use a custom filter for accepting files.
     */
    public void setFileFilter(FileFilter fileFilter) {
        this.fileFilter = fileFilter;
    }

    @Override
    public void onReload(Object source) {
        // this implementation uses a watcher to automatic reload
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (folder == null) {
            // no folder configured
            return;
        }
        if (!scheduler) {
            // do not start scheduler so exit start phase
            return;
        }

        File dir = new File(folder);
        if (dir.exists() && dir.isDirectory()) {
            String msg = startupMessage(dir);
            if (msg != null) {
                LOG.info(msg);
            }

            this.compileWorkDir = resolveCompileWorkDir();
            // the files as they are now are the starting point, not a change: record them without reloading
            known.clear();
            scan();

            task = new WatchFileChangesTask(dir.toPath());

            executorService = getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this,
                    "FileWatcherReloadStrategy");
            executorService.submit(task);
        }
    }

    protected String startupMessage(File dir) {
        return "Starting ReloadStrategy to watch directory: " + dir;
    }

    /**
     * The properties of a change come before the rest. {@link RouteWatcherReloadStrategy} reloads a change as one batch
     * and does not need this, but a listener reloaded one file at a time does: a route built before the property it
     * uses was applied fails with "Property with key [x] not found", and the watch service reports the files of one
     * save in no particular order. The sort is stable, so files of the same kind keep the order they were reported in.
     */
    static void orderPropertiesFirst(List<File> changed) {
        changed.sort(Comparator.comparingInt(f -> f.getName().endsWith(".properties") ? 0 : 1));
    }

    /**
     * The files whose modification time or length changed since the previous scan, plus the files that are gone. The
     * snapshot is updated as it goes, so a file is reported once per change.
     * <p/>
     * A file modified less than {@link #stableTimeout} ago is left for the next scan: a save still being written would
     * otherwise be reloaded half-finished.
     */
    protected List<File> scan() {
        List<File> changed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Path root = new File(folder).toPath();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (isCompileWorkDir(dir)) {
                        // the class files the runtime writes while compiling a Java source are not changes of ours,
                        // and would trigger a reload, which compiles again, which writes again (CAMEL-24862)
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!isRecursive && !dir.equals(root)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    File f = file.toFile();
                    if (fileFilter != null && !fileFilter.accept(f)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String name = FileUtil.compactPath(f.getPath());
                    seen.add(name);
                    long modified = attrs.lastModifiedTime().toMillis();
                    long length = attrs.size();
                    long[] previous = known.get(name);
                    if (previous != null && previous[0] == modified && previous[1] == length) {
                        return FileVisitResult.CONTINUE;
                    }
                    long age = System.currentTimeMillis() - modified;
                    if (age >= 0 && age < stableTimeout) {
                        // still being written: leave it, and let the next scan report it. A modification time in the
                        // future (a clock askew on a network share, a touch -t) is reported now rather than waited out
                        seen.remove(name);
                        return FileVisitResult.CONTINUE;
                    }
                    known.put(name, new long[] { modified, length });
                    LOG.debug("Detected Modified/Created file: {}", name);
                    changed.add(f);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // a file that went away between the walk and reading it is a change like any other
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.warn("Cannot scan directory: {} due to: {}. This exception is ignored.", folder, e.getMessage(), e);
            return changed;
        }
        // what the snapshot has and the scan did not see is gone; a file still being written was taken out of seen,
        // so only drop what the scan positively did not find
        for (Iterator<Map.Entry<String, long[]>> it = known.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, long[]> entry = it.next();
            File f = new File(entry.getKey());
            if (!seen.contains(entry.getKey()) && !f.exists()) {
                LOG.debug("Detected Deleted file: {}", entry.getKey());
                it.remove();
                changed.add(f);
            }
        }
        return changed;
    }

    /**
     * Whether the directory is the compile work directory (or inside it): the class files the runtime writes there when
     * it compiles a Java source are not changes to watch, and would otherwise trigger a reload of their own, which
     * compiles again, which writes again (CAMEL-24862).
     */
    protected boolean isCompileWorkDir(Path dir) {
        return compileWorkDir != null && dir.toAbsolutePath().normalize().startsWith(compileWorkDir);
    }

    private Path resolveCompileWorkDir() {
        CompileStrategy cs = getCamelContext() != null
                ? getCamelContext().getCamelContextExtension().getContextPlugin(CompileStrategy.class) : null;
        String workDir = cs != null ? cs.getWorkDir() : null;
        return workDir != null ? Path.of(workDir).toAbsolutePath().normalize() : null;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdown(executorService);
            executorService = null;
        }

    }

    /**
     * Background task which watches for file changes
     */
    protected class WatchFileChangesTask implements Runnable {

        private final Path folder;
        private volatile boolean running;

        public WatchFileChangesTask(Path folder) {
            this.folder = folder;
        }

        public boolean isRunning() {
            return running;
        }

        public void run() {
            LOG.debug("FileReloadStrategy is starting watching folder: {}", folder);

            // allow running while starting Camel
            while (isStarting() || isRunAllowed()) {
                running = true;

                try {
                    Thread.sleep(pollTimeout);
                } catch (InterruptedException e) {
                    LOG.info("Interrupted while waiting to scan for file changes");
                    Thread.currentThread().interrupt();
                    break;
                }

                LOG.trace("FileReloadStrategy is scanning for file changes in directory: {}", folder);
                List<File> changed = scan();
                if (!changed.isEmpty()) {
                    // the files of one scan are the files of one save: reloaded together
                    onReloadBatch(changed);
                }
            }

            running = false;

            LOG.debug("FileReloadStrategy is stopping watching folder: {}", folder);
        }
    }

    /**
     * Reloads the files of one change. The default reloads them one at a time, which is what a listener of a single
     * {@link ResourceReload} expects; {@link RouteWatcherReloadStrategy} overrides it to apply the properties of the
     * batch and then reload the routes once.
     */
    protected void onReloadBatch(List<File> changed) {
        // the properties first: a listener that rebuilds something from them should see them before the rest.
        // A copy, as the list the caller passed is theirs and may not be modifiable
        List<File> files = new ArrayList<>(changed);
        orderPropertiesFirst(files);
        for (File file : files) {
            reloadFile(file);
        }
    }

    /**
     * Reloads one file through the {@link ResourceReload} listener, counting it and reporting a failure the way a
     * failed context reload is reported, so a listener can act on the file. A failure is logged and not rethrown: one
     * unreadable file does not stop the watcher.
     */
    protected void reloadFile(File file) {
        String name = FileUtil.compactPath(file.getPath());
        try {
            setLastError(null);
            // must use file resource loader as we cannot load from classpath
            Resource resource = PluginHelper.getResourceLoader(getCamelContext()).resolveResource("file:" + name);
            getResourceReload().onReload(name, resource);
            incSucceededCounter();
        } catch (Exception e) {
            setLastError(e);
            incFailedCounter();
            // the same event a failed context reload emits, so a listener can act on the file
            EventHelper.notifyContextReloadFailure(getCamelContext(), name, e);
            String msg = e.getMessage();
            if (msg != null && msg.endsWith(".")) {
                msg = msg.substring(0, msg.length() - 1);
            }
            LOG.warn("Error reloading routes from file: {} due to: {}. This exception is ignored.", name, msg, e);
        }
    }

}
