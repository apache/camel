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
package org.apache.camel.dsl.jbang.run.export;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.dsl.jbang.run.common.CamelHome;
import org.apache.camel.dsl.jbang.run.common.Printer;

/**
 * Lets one export at a time use a work directory, such as the .camel-jbang directory of the current directory, which
 * every export there shares: an export deletes it when it starts, and builds the project and keeps the run settings in
 * it, so exports at the same time would mix up their projects (CAMEL-26912).
 * <p/>
 * The exports of other processes are kept out by a file lock in the Camel directory of the user (not in the work
 * directory, which an export deletes), and the exports of this process, which a file lock does not tell apart, by a
 * lock of its own.
 */
final class WorkDirLock implements AutoCloseable {

    private static final Map<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private final ReentrantLock lock;
    private final FileChannel channel;
    private final FileLock fileLock;

    private WorkDirLock(ReentrantLock lock, FileChannel channel, FileLock fileLock) {
        this.lock = lock;
        this.channel = channel;
        this.fileLock = fileLock;
    }

    /**
     * Waits until no other export uses the work directory, and locks it.
     *
     * @param workDir the work directory
     * @param printer where to say that the export waits for another one
     */
    static WorkDirLock acquire(Path workDir, Printer printer) throws IOException {
        Path dir = workDir.toAbsolutePath().normalize();
        ReentrantLock lock = LOCKS.computeIfAbsent(dir, d -> new ReentrantLock());
        boolean waiting = false;
        if (!lock.tryLock()) {
            waiting = waiting(printer, dir);
            lock.lock();
        }
        FileChannel channel = null;
        try {
            Path camelDir = CamelHome.getCamelDir();
            Files.createDirectories(camelDir);
            // the lock file is kept: deleting it would let an export lock a new file while another holds the old one
            String name = "export-" + UUID.nameUUIDFromBytes(dir.toString().getBytes(StandardCharsets.UTF_8)) + ".lock";
            channel = FileChannel.open(camelDir.resolve(name), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock fileLock = channel.tryLock();
            if (fileLock == null) {
                if (!waiting) {
                    waiting(printer, dir);
                }
                fileLock = channel.lock();
            }
            return new WorkDirLock(lock, channel, fileLock);
        } catch (IOException | RuntimeException e) {
            if (channel != null) {
                channel.close();
            }
            lock.unlock();
            throw e;
        }
    }

    private static boolean waiting(Printer printer, Path dir) {
        printer.println("Waiting for another export that uses " + dir + " to finish");
        return true;
    }

    @Override
    public void close() throws IOException {
        try {
            fileLock.release();
            channel.close();
        } finally {
            lock.unlock();
        }
    }
}
