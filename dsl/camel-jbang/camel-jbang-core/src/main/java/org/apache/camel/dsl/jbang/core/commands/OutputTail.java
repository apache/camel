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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Reads the output of a process (its stdout or stderr) on a thread of its own until the end of the stream, and keeps
 * the last lines.
 * <p>
 * The stream is read all the time, so the process never blocks on a full pipe, and the thread ends at the end of the
 * stream instead of reading it again.
 */
final class OutputTail {

    private final int maxLines;
    private final Deque<String> lines;
    private final Runnable onLine;
    private Thread thread;

    /**
     * @param maxLines the number of last lines to keep
     * @param onLine   called after each line that is read, or null
     */
    OutputTail(int maxLines, Runnable onLine) {
        this.maxLines = maxLines;
        this.lines = new ArrayDeque<>(maxLines);
        this.onLine = onLine;
    }

    /**
     * Starts to read the stream on a daemon thread with the given name. The stream is closed at its end.
     */
    OutputTail start(InputStream in, String threadName) {
        thread = new Thread(() -> read(in), threadName);
        thread.setDaemon(true);
        thread.start();
        return this;
    }

    private void read(InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                add(line);
                if (onLine != null) {
                    onLine.run();
                }
            }
        } catch (IOException e) {
            // the process is gone, and its pipe with it
        }
    }

    private synchronized void add(String line) {
        if (lines.size() >= maxLines) {
            lines.removeFirst();
        }
        lines.addLast(line);
    }

    /**
     * The lines kept so far, oldest first.
     */
    synchronized List<String> lines() {
        return new ArrayList<>(lines);
    }

    /**
     * Waits until the end of the stream is read, so the last lines of a process that has ended are kept.
     *
     * @param  millis the time to wait at most
     * @return        true when the end of the stream was read
     */
    boolean awaitEnd(long millis) throws InterruptedException {
        if (thread == null) {
            return true;
        }
        thread.join(millis);
        return !thread.isAlive();
    }
}
