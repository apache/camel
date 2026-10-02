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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.util.json.JsonObject;

/**
 * Notices an agent asking a {@link ToolDescriptor#isDeterministic() deterministic} tool the same question over and over
 * within one session. A small model can get stuck doing that: every answer is the same, so nothing in its input changes
 * and it asks again. From the third identical call the tool answers with a short note instead of the full answer, so
 * the input does change and the context is not filled with copies. The first two calls are always answered in full, so
 * a legitimate re-read still works.
 * <p>
 * One instance tracks one session: a client connection, or a conversation of an AI panel. Call {@link #reset()} when a
 * new session starts on the same instance.
 */
public final class RepeatedToolCalls {

    /** The key of the tool listing's {@code _meta} entry that marks a tool as deterministic. */
    public static final String DETERMINISTIC_META_KEY = "camel.apache.org/deterministic";

    /** How many identical calls are answered in full before the short note is given. */
    static final int FULL_ANSWERS = 2;

    /** Guards against unbounded growth over a very long session; the oldest counts are simply forgotten. */
    private static final int MAX_TRACKED = 1000;

    private final Map<String, Integer> counts = new HashMap<>();

    /**
     * Counts the call, and returns the short note to answer with instead of the full answer, or null when the tool
     * should run and answer in full: a tool that is not deterministic, or one of the first {@value #FULL_ANSWERS}
     * identical calls.
     */
    public synchronized JsonObject repeatOf(ToolDescriptor tool, Map<String, ?> args) {
        if (tool == null || !tool.isDeterministic()) {
            return null;
        }
        if (counts.size() >= MAX_TRACKED) {
            counts.clear();
        }
        int times = counts.merge(key(tool.name(), args), 1, Integer::sum);
        if (times <= FULL_ANSWERS) {
            return null;
        }
        JsonObject note = new JsonObject();
        note.put("repeated", true);
        note.put("tool", tool.name());
        note.put("timesAsked", times);
        note.put("note", tool.name() + " was already called with these same arguments " + (times - 1)
                         + " times in this session and answered in full. Its answer does not change, so it is not sent"
                         + " again: it is in your earlier tool result.");
        return note;
    }

    /** Forgets the calls so far: a new session starts. */
    public synchronized void reset() {
        counts.clear();
    }

    /**
     * The tool name with its arguments in name order, leaving out the empty ones: a model that sends an optional
     * argument as an empty string is asking the same question as one that leaves it out.
     */
    static String key(String tool, Map<String, ?> args) {
        Map<String, String> sorted = new TreeMap<>();
        if (args != null) {
            for (Map.Entry<String, ?> e : args.entrySet()) {
                Object v = e.getValue();
                String s = v != null ? v.toString().trim() : "";
                if (!s.isEmpty()) {
                    sorted.put(e.getKey(), s);
                }
            }
        }
        return tool + sorted;
    }
}
