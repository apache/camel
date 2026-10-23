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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.EndpointConsumerChecks;

/**
 * The direct: and seda: endpoints the other route files of the project consume, for the editor to mark a line that
 * sends to one no route consumes (CAMEL-24955). Reading them means reading every route file of the project, so they are
 * read on a background thread and kept: the editor asks while typing, gets what was read last (null until the first
 * read is done), and is told when a read finds something else. They are read again when older than a few seconds, as
 * the other files may be edited meanwhile, in the TUI or outside it.
 */
final class ConsumedEndpoints {

    static final long TTL_MS = 10_000;

    private final Executor executor;
    private final Consumer<Runnable> renderThread;

    // the file asked about last: the endpoints are those of the route files under directory, excludeFile left out
    private Path directory;
    private String excludeFile;
    private CamelCatalog catalog;
    private Runnable onChange;
    private Set<String> consumed;
    private long readTime;
    private boolean reading;

    /**
     * @param executor     where the files are read
     * @param renderThread runs the callback of a read that found something else on the UI thread
     */
    ConsumedEndpoints(Executor executor, Consumer<Runnable> renderThread) {
        this.executor = executor;
        this.renderThread = renderThread;
    }

    /**
     * The notes of a YAML route file: each line that sends to a direct: or seda: endpoint no route of the project
     * consumes, as "Line N: message"; none while the other route files are being read the first time.
     *
     * @param rootDir  the project directory, whose route files are read; the file's own directory when it is not in it
     * @param onChange called on the UI thread when a read finds other endpoints, to mark the file again
     */
    List<String> notes(Path file, Path rootDir, String content, CamelCatalog catalog, Runnable onChange) {
        Path f = file.toAbsolutePath().normalize();
        Path root = rootDir != null ? rootDir.toAbsolutePath().normalize() : null;
        if (root == null || !f.startsWith(root)) {
            root = f.getParent();
        }
        return EndpointConsumerChecks.validateYamlConsumers(content,
                get(root, root.relativize(f).toString(), catalog, onChange));
    }

    /**
     * The endpoints the route files under the directory consume, the excluded file left out: what was read last for
     * them, starting a read when there is none yet or it is old.
     *
     * @param  onChange called on the UI thread when a read finds other endpoints than were returned
     * @return          null while not known: not read yet, or the files cannot say (see
     *                  {@link EndpointConsumerChecks#consumed})
     */
    synchronized Set<String> get(Path directory, String excludeFile, CamelCatalog catalog, Runnable onChange) {
        this.catalog = catalog;
        this.onChange = onChange;
        if (!Objects.equals(directory, this.directory) || !Objects.equals(excludeFile, this.excludeFile)) {
            this.directory = directory;
            this.excludeFile = excludeFile;
            consumed = null;
            readTime = 0;
        }
        if (!reading && (readTime == 0 || MonotonicClock.millis() - readTime > TTL_MS)) {
            startRead();
        }
        return consumed;
    }

    /** Reads the endpoints of the file asked about last; the caller holds the lock. */
    private void startRead() {
        reading = true;
        Path dir = directory;
        String exclude = excludeFile;
        CamelCatalog cat = catalog;
        executor.execute(() -> read(dir, exclude, cat));
    }

    private void read(Path dir, String exclude, CamelCatalog cat) {
        Set<String> found = null;
        try {
            found = EndpointConsumerChecks.consumed(dir, exclude, cat);
        } catch (RuntimeException e) {
            // what the files consume is not known: the check says nothing
        }
        Runnable callback;
        synchronized (this) {
            if (!Objects.equals(dir, directory) || !Objects.equals(exclude, excludeFile)) {
                // another file was opened meanwhile: read for that one instead
                startRead();
                return;
            }
            reading = false;
            boolean changed = readTime == 0 ? found != null : !Objects.equals(found, consumed);
            consumed = found;
            readTime = MonotonicClock.millis();
            callback = changed ? onChange : null;
        }
        if (callback != null) {
            renderThread.accept(callback);
        }
    }
}
