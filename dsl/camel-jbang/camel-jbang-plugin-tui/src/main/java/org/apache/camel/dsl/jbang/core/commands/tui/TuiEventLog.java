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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe bounded ring buffer for TUI key events. Used by the embedded MCP server to expose recent user
 * interactions to AI agents.
 */
class TuiEventLog {

    record Event(String key, String label, Instant timestamp) {
    }

    private final Event[] buffer;
    private int head;
    private int size;

    TuiEventLog(int capacity) {
        this.buffer = new Event[capacity];
    }

    synchronized void record(String key, String label) {
        buffer[head] = new Event(key, label, Instant.now());
        head = (head + 1) % buffer.length;
        if (size < buffer.length) {
            size++;
        }
    }

    synchronized List<Event> getRecent(int limit) {
        int count = Math.min(limit, size);
        List<Event> result = new ArrayList<>(count);
        int start = (head - count + buffer.length) % buffer.length;
        for (int i = 0; i < count; i++) {
            result.add(buffer[(start + i) % buffer.length]);
        }
        return result;
    }
}
