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
package org.apache.camel.impl.console;

import java.io.LineNumberReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

public final class ConsoleHelper {

    private ConsoleHelper() {
    }

    public static JsonArray loadSourceAsJson(CamelContext camelContext, String location) {
        if (location == null) {
            return null;
        }
        Integer lineNumber = extractSourceLocationLineNumber(location);
        try {
            location = LoggerHelper.stripSourceLocationLineNumber(location);
            Resource resource = PluginHelper.getResourceLoader(camelContext).resolveResource(location);
            if (resource != null) {
                return loadSourceAsJson(resource.getReader(), lineNumber);
            }
        } catch (Exception e) {
            // ignore
        }

        return new JsonArray();
    }

    public static JsonArray loadSourceAsJson(Reader reader, Integer lineNumber) {
        JsonArray code = new JsonArray();
        try {
            LineNumberReader lnr = new LineNumberReader(reader);
            int i = 0;
            String t;
            do {
                t = lnr.readLine();
                if (t != null) {
                    i++;
                    JsonObject c = new JsonObject();
                    c.put("line", i);
                    c.put("code", Jsoner.escape(t));
                    if (lineNumber != null && lineNumber == i) {
                        c.put("match", true);
                    }
                    code.add(c);
                }
            } while (t != null);
            IOHelper.close(lnr);
        } catch (Exception e) {
            // ignore
        }

        return code.isEmpty() ? null : code;
    }

    public static String loadSourceLine(CamelContext camelContext, String location, Integer lineNumber) {
        List<String> lines = loadSourceLines(camelContext, location, lineNumber, lineNumber + 1);
        if (lines.size() == 1) {
            return lines.get(0);
        }
        return null;
    }

    public static List<String> loadSourceLines(CamelContext camelContext, String location, Integer start, Integer end) {
        if (location == null || start == null) {
            return Collections.emptyList();
        }

        List<String> answer = new ArrayList<>();
        try {
            location = LoggerHelper.stripSourceLocationLineNumber(location);
            Resource resource = PluginHelper.getResourceLoader(camelContext).resolveResource(location);
            if (resource != null) {
                LineNumberReader reader = new LineNumberReader(resource.getReader());
                int i = 0;
                String t;
                do {
                    t = reader.readLine();
                    if (t != null) {
                        i++;
                        if (i >= start && (end == null || i < end)) {
                            answer.add(t);
                        }
                    }
                } while (t != null);
                IOHelper.close(reader);
            }
        } catch (Exception e) {
            // ignore
        }

        return answer;
    }

    public static Integer extractSourceLocationLineNumber(String location) {
        int cnt = StringHelper.countChar(location, ':');
        if (cnt > 0) {
            int pos = location.lastIndexOf(':');
            // in case pos is end of line
            if (pos < location.length() - 1) {
                String num = location.substring(pos + 1);
                try {
                    return Integer.valueOf(num);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    public static String extractSourceLocationNoLineNumber(String location) {
        Integer line = extractSourceLocationLineNumber(location);
        if (line != null) {
            int pos = location.lastIndexOf(':');
            return location.substring(0, pos);
        }
        return location;
    }

    /**
     * Adds the element to the bounded queue, removing the oldest elements to make room.
     */
    static <T> void offerLast(Queue<T> queue, T element) {
        while (!queue.offer(element)) {
            queue.poll();
        }
    }

    /**
     * Creates a bounded queue of the given capacity holding the newest elements of the given queue.
     */
    static <T> Queue<T> resize(Queue<T> queue, int capacity) {
        Queue<T> answer = new LinkedBlockingQueue<>(Math.max(1, capacity));
        for (T element : queue) {
            offerLast(answer, element);
        }
        return answer;
    }

    /**
     * Creates a ring buffer of the given capacity holding the newest entries of the given ring buffer, so that slot 0
     * is the next one to write.
     *
     * @param ring the ring buffer
     * @param next the next slot to write in the given ring buffer
     */
    static <T> T[] resize(T[] ring, int next, int capacity) {
        int length = Math.max(1, capacity);
        List<T> entries = new ArrayList<>(ring.length);
        for (int i = 0; i < ring.length; i++) {
            T entry = ring[(next + i) % ring.length];
            if (entry != null) {
                entries.add(entry);
            }
        }
        T[] answer = Arrays.copyOf(ring, length);
        Arrays.fill(answer, null);
        // the newest entries, oldest first, at the end: slot 0 holds the oldest (or nothing) and is written next
        int kept = Math.min(entries.size(), length);
        for (int i = 0; i < kept; i++) {
            answer[length - kept + i] = entries.get(entries.size() - kept + i);
        }
        return answer;
    }

    /**
     * Returns the slot to write in a ring buffer of the given length, and moves the position to the next slot.
     */
    static int nextSlot(AtomicInteger pos, int length) {
        // the position may come from a larger ring buffer that has just been resized
        return pos.getAndUpdate(operand -> (operand + 1) % length) % length;
    }

}
