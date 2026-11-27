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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A non-recursive watch reports a subdirectory created in, or deleted from, the base directory. The tests run
 * directory-watcher the way the consumer does on Linux and Windows: with a watch service of the file system, which
 * reports a created directory through the file tree visitor. On macOS the consumer leaves the watch service to
 * directory-watcher, which reports a created directory without the visitor, so a route test there does not cover this.
 */
class FileWatchNonRecursiveDirectoryTest {

    @TempDir
    Path dir;

    @Test
    void aCreatedSubdirectoryIsReported() throws Exception {
        BlockingQueue<DirectoryChangeEvent> events = new LinkedBlockingQueue<>();
        DirectoryWatcher watcher = startWatcher(events);
        try {
            Path created = Files.createDirectory(dir.resolve("created"));

            awaitEvent(events, DirectoryChangeEvent.EventType.CREATE, created);
        } finally {
            watcher.close();
        }
    }

    @Test
    void aDeletedSubdirectoryIsReported() throws Exception {
        Path existing = Files.createDirectory(dir.resolve("existing"));
        BlockingQueue<DirectoryChangeEvent> events = new LinkedBlockingQueue<>();
        DirectoryWatcher watcher = startWatcher(events);
        try {
            // with file hashing, a DELETE is only reported for a path that was hashed when the watch started
            Files.delete(existing);

            awaitEvent(events, DirectoryChangeEvent.EventType.DELETE, existing);
        } finally {
            watcher.close();
        }
    }

    @Test
    void aSubdirectoryRemovedRightAfterItWasCreatedIsNoError() throws Exception {
        Path gone = dir.resolve("gone");
        List<Path> directories = new ArrayList<>();
        List<Path> files = new ArrayList<>();

        // what directory-watcher does for the CREATE event of a directory that no longer exists
        new FileWatchConsumer.BaseDirectoryVisitor(dir).recursiveVisitFiles(gone, directories::add, files::add);

        assertEquals(List.of(), directories);
        assertEquals(List.of(gone), files);
    }

    private DirectoryWatcher startWatcher(BlockingQueue<DirectoryChangeEvent> events) throws Exception {
        DirectoryWatcher watcher = DirectoryWatcher.builder()
                .path(dir)
                .watchService(dir.getFileSystem().newWatchService())
                .fileTreeVisitor(new FileWatchConsumer.BaseDirectoryVisitor(dir))
                .fileHashing(true)
                .listener(events::add)
                .build();
        // registers the base directory and hashes its entries before it returns
        watcher.watchAsync();
        return watcher;
    }

    private static void awaitEvent(BlockingQueue<DirectoryChangeEvent> events, DirectoryChangeEvent.EventType type, Path path)
            throws InterruptedException {
        List<DirectoryChangeEvent> others = new ArrayList<>();
        // the polling watch service of the JDK on macOS checks the directory every few seconds
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            DirectoryChangeEvent event = events.poll(100, TimeUnit.MILLISECONDS);
            if (event != null) {
                if (event.eventType() == type && event.path().equals(path)) {
                    return;
                }
                others.add(event);
            }
        }
        fail("No " + type + " event for " + path + ", other events: " + others);
    }
}
