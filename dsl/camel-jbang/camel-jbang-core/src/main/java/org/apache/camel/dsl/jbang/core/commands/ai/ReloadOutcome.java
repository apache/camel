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

import java.util.List;

import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonObject;

/**
 * What a dev-mode integration did with a file the agent wrote: the log records of the reload, read through
 * {@link LogFileReader} (CAMEL-24859). A write tool answering "written" while the reload failed sends the agent on with
 * a broken route; the outcome in the answer is what it acts on.
 */
public final class ReloadOutcome {

    static final String RELOADED = "Routes reloaded summary";
    static final String FAILED = "Error reloading routes from file";
    static final String PROPERTIES = "Reloading properties";
    static final String REPORT = "did not load";

    private ReloadOutcome() {
    }

    static boolean isReload(JsonObject r) {
        String m = r.getStringOrDefault("message", "");
        return m.contains(RELOADED) || m.contains(FAILED) || m.contains(PROPERTIES);
    }

    static String key(JsonObject r) {
        return r.getStringOrDefault("time", "") + "|" + r.getStringOrDefault("message", "");
    }

    /** The key of the newest reload record, or null: what a later reload is compared against. */
    public static String latestReloadKey(List<JsonObject> newestFirst) {
        for (JsonObject r : newestFirst) {
            if (isReload(r)) {
                return key(r);
            }
        }
        return null;
    }

    /**
     * The outcome in the records newer than the given key: status reloaded, failed or properties with the record's
     * message (and, for a failure, its detail and the validator's report when the runtime printed one), or null when no
     * reload has happened yet.
     */
    public static JsonObject classify(List<JsonObject> newestFirst, String sinceKey) {
        for (int i = 0; i < newestFirst.size(); i++) {
            JsonObject r = newestFirst.get(i);
            if (!isReload(r)) {
                continue;
            }
            if (sinceKey != null && sinceKey.equals(key(r))) {
                return null; // the newest reload is the one from before the write
            }
            String m = r.getStringOrDefault("message", "");
            JsonObject out = new JsonObject();
            if (m.contains(FAILED)) {
                out.put("status", "failed");
                StringBuilder sb = new StringBuilder(m);
                String detail = r.getStringOrDefault("detail", "");
                if (!detail.isEmpty()) {
                    // the first lines of the cause, not the stack
                    for (String line : detail.split("\n")) {
                        if (line.startsWith("\tat ") || line.isBlank()) {
                            continue;
                        }
                        sb.append("\n").append(line);
                        if (sb.length() > 1500) {
                            break;
                        }
                    }
                }
                // the validator's report the runtime logs next to a load failure (CAMEL-24851) is older than the
                // failure record in a newest-first list
                for (int j = i + 1; j < Math.min(newestFirst.size(), i + 4); j++) {
                    String other = newestFirst.get(j).getStringOrDefault("message", "");
                    if (other.contains(REPORT)) {
                        sb.append("\n").append(other);
                        String d = newestFirst.get(j).getStringOrDefault("detail", "");
                        if (!d.isEmpty()) {
                            sb.append("\n").append(d);
                        }
                        break;
                    }
                }
                out.put("message", sb.toString());
            } else if (m.contains(RELOADED)) {
                out.put("status", "reloaded");
                out.put("message", m);
            } else {
                out.put("status", "properties");
                out.put("message", m);
            }
            return out;
        }
        return null;
    }

    /** Polls the integration's log for the reload of a just written file, up to the timeout. */
    public static JsonObject await(long pid, String name, String sinceKey, long timeoutMillis) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < timeoutMillis) {
            JsonObject outcome = classify(records(pid, name), sinceKey);
            if (outcome != null) {
                return outcome;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        JsonObject out = new JsonObject();
        out.put("status", "unknown");
        out.put("message", "no reload seen in the log within " + timeoutMillis / 1000 + "s: the integration may not run"
                           + " in dev mode, or the file is not one it watches; camel_get_log shows what it did");
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<JsonObject> records(long pid, String name) {
        JsonObject log = LogFileReader.read(pid, name, 40, null, null);
        Object lines = log.get("lines");
        return lines instanceof List ? (List<JsonObject>) lines : List.of();
    }
}
