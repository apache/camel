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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
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
 * The note starts with what to do next, and then says that the answer is the same each time. A note that only said the
 * answer was already given did not stop a small model. In the local-model benchmark (r32), a model asked for a sample
 * that does not exist, and then asked the same 11 or 12 more times, in 3 runs of a step. It said sorry for the repeated
 * calls and asked again, until its 25 tool calls were spent. It never wrote the test of the step. So the note starts
 * with the next step: the {@link ToolDescriptor#repeatHint() hint} of the tool, such as writing the file, whose checks
 * say what to change, or else to go on with the task.
 * <p>
 * One instance tracks one session: a client connection, or a conversation of an AI panel. Call {@link #reset()} when a
 * new session starts on the same instance.
 */
public final class RepeatedToolCalls {

    /** The key of the tool listing's {@code _meta} entry that marks a tool as deterministic. */
    public static final String DETERMINISTIC_META_KEY = "camel.apache.org/deterministic";

    /** How many identical calls are answered in full before the short note is given. */
    static final int FULL_ANSWERS = 2;

    /** The next step the note starts with when the tool has no {@link ToolDescriptor#repeatHint() hint} of its own. */
    static final String NEXT_STEP = "Go on with the next step of your task.";

    /** Guards against unbounded growth over a very long session; the oldest counts are simply forgotten. */
    private static final int MAX_TRACKED = 1000;

    private final Map<String, Integer> counts = new HashMap<>();

    /** The calls whose answer was an error given as data, such as a name the catalog has not got, with that answer. */
    private final Map<String, JsonObject> misses = new HashMap<>();

    /**
     * Returns the short note to answer with instead of running the tool, or null when the tool should run and answer in
     * full: a tool that is not deterministic, or one of the first {@value #FULL_ANSWERS} identical calls. When this
     * method returns null the caller must invoke {@link #recordSuccessfulAnswer(ToolDescriptor, Map)} after the tool
     * returns successfully; failed calls (those that throw) must not count against the retry budget.
     */
    public synchronized JsonObject repeatOf(ToolDescriptor tool, Map<String, ?> args) {
        if (tool == null || !tool.isDeterministic(args)) {
            return null;
        }
        if (counts.size() >= MAX_TRACKED) {
            counts.clear();
            misses.clear();
        }
        // Peek at the current count without incrementing. The increment happens only after a successful answer
        // (recordSuccessfulAnswer), so a call that throws an exception does not consume the retry budget.
        String key = key(tool.name(), args, tool.deterministicWhen());
        int current = counts.getOrDefault(key, 0);
        if (current < FULL_ANSWERS) {
            // Will be incremented by recordSuccessfulAnswer; answer in full.
            return null;
        }
        // Already answered FULL_ANSWERS times; the next identical call gets the note.
        // Increment so timesAsked keeps growing (allows the model to see the repetition count rising).
        int times = counts.merge(key, 1, Integer::sum);
        JsonObject note = new JsonObject();
        note.put("repeated", true);
        note.put("tool", tool.name());
        note.put("timesAsked", times);
        JsonObject miss = misses.get(key);
        if (miss != null) {
            // the hint of the tool is for an answer that was found, such as writing the file with the options it
            // gave; a miss gives no options, so the note says what the answer was and what to ask instead (CAMEL-26932)
            Object suggestions = miss.get("suggestions");
            note.put("note", "The answer was: " + miss.getString("error") + ". "
                             + (suggestions instanceof Collection<?> c && !c.isEmpty()
                                     ? "Use one of the suggestions instead: " + String.join(", ", c.stream()
                                             .map(String::valueOf).toList())
                                       + "."
                                     : "Ask with other arguments instead.")
                             + " You asked this " + times + " times now with the same arguments, and the answer is the"
                             + " same each time.");
            if (suggestions != null) {
                note.put("suggestions", suggestions);
            }
            return note;
        }
        // the next step first, then the fact; the count is timesAsked, this call included (CAMEL-26875)
        note.put("note", (tool.repeatHint() != null ? tool.repeatHint() : NEXT_STEP) + " You asked this " + times
                         + " times now with the same arguments, and the answer is the same each time: it is in your"
                         + " earlier tool result.");
        return note;
    }

    /**
     * Records that a tool answered a call in full (i.e. did not throw). Only successful answers count against the
     * {@value #FULL_ANSWERS} budget; calls that throw an exception do not.
     */
    public synchronized void recordSuccessfulAnswer(ToolDescriptor tool, Map<String, ?> args) {
        recordSuccessfulAnswer(tool, args, null);
    }

    /**
     * As {@link #recordSuccessfulAnswer(ToolDescriptor, Map)}, with the answer: an answer with an {@code error}, such
     * as a name the catalog has not got, is a miss, and the note for repeating it says so instead of the hint of the
     * tool. A miss still counts, so the model is still stopped from asking the same again and again (CAMEL-26875).
     *
     * @param answer the answer of the tool, or null when it is not JSON
     */
    public synchronized void recordSuccessfulAnswer(ToolDescriptor tool, Map<String, ?> args, JsonObject answer) {
        if (tool == null || !tool.isDeterministic(args)) {
            return;
        }
        if (counts.size() >= MAX_TRACKED) {
            counts.clear();
            misses.clear();
        }
        String key = key(tool.name(), args, tool.deterministicWhen());
        counts.merge(key, 1, Integer::sum);
        if (answer != null && answer.get("error") instanceof String) {
            misses.put(key, answer);
        } else {
            misses.remove(key);
        }
    }

    /** Forgets the calls so far: a new session starts. */
    public synchronized void reset() {
        counts.clear();
        misses.clear();
    }

    /**
     * The tool name with its arguments in name order, leaving out the empty ones: a model that sends an optional
     * argument as an empty string is asking the same question as one that leaves it out.
     */
    static String key(String tool, Map<String, ?> args) {
        return key(tool, args, null);
    }

    /**
     * As {@link #key(String, Map)}, with the argument that makes the call deterministic (the source to validate) keyed
     * by a digest of its exact text: whitespace can decide the answer there (a newline before {@code <?xml ...?>}), and
     * the key does not hold a copy of the source.
     */
    static String key(String tool, Map<String, ?> args, String exact) {
        Map<String, String> sorted = new TreeMap<>();
        if (args != null) {
            for (Map.Entry<String, ?> e : args.entrySet()) {
                Object v = e.getValue();
                String s = v != null ? v.toString() : "";
                if (s.isBlank()) {
                    continue;
                }
                sorted.put(e.getKey(), e.getKey().equals(exact) ? digest(s) : s.trim());
            }
        }
        return tool + sorted;
    }

    private static String digest(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // every JVM has SHA-256; the text itself is an exact key too
            return text;
        }
    }
}
