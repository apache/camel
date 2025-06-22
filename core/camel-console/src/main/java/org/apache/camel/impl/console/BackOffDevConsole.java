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

import java.util.Map;
import java.util.Set;

import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.backoff.BackOffTimer;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "backoff", displayName = "BackOff", description = "Display information about BackOff tasks")
public class BackOffDevConsole extends AbstractDevConsole {

    public BackOffDevConsole() {
        super("camel", "backoff", "BackOff", "Display information about BackOff tasks");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        Set<BackOffTimer> timers = getCamelContext().hasServices(BackOffTimer.class);
        for (BackOffTimer timer : timers) {
            sb.append(String.format("\nTimer: %s", timer.getName()));
            sb.append(String.format("\nTasks: %s", timer.size()));
            int id = 0;
            for (BackOffTimer.Task task : timer.getTasks()) {
                String failure = task.getException() != null ? task.getException().getMessage() : "";
                sb.append(String.format(
                        "\n    #%d (name=%s status=%s attempts=%d delay=%d elapsed=%d first=%d last=%d next=%d failure=%s",
                        id, task.getName(), task.getStatus().name(), task.getCurrentAttempts(), task.getCurrentDelay(),
                        task.getCurrentElapsedTime(), task.getFirstAttemptTime(), task.getLastAttemptTime(),
                        task.getNextAttemptTime(), failure));
                id++;
            }
        }
        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        root.put("timers", arr);

        Set<BackOffTimer> timers = getCamelContext().hasServices(BackOffTimer.class);
        for (BackOffTimer timer : timers) {
            JsonObject jo = new JsonObject();
            jo.put("name", timer.getName());
            jo.put("size", timer.size());
            arr.add(jo);
            if (timer.size() > 0) {
                JsonArray arr2 = new JsonArray();
                jo.put("tasks", arr2);
                for (BackOffTimer.Task task : timer.getTasks()) {
                    String failure = task.getException() != null ? task.getException().getMessage() : "";
                    JsonObject jo2 = new JsonObject();
                    jo2.put("name", task.getName());
                    jo2.put("status", task.getStatus().name());
                    jo2.put("attempts", task.getCurrentAttempts());
                    jo2.put("delay", task.getCurrentDelay());
                    jo2.put("elapsed", task.getCurrentElapsedTime());
                    jo2.put("firstTime", task.getFirstAttemptTime());
                    jo2.put("lastTime", task.getLastAttemptTime());
                    jo2.put("nextTime", task.getNextAttemptTime());
                    if (failure != null) {
                        jo2.put("error", failure);
                    }
                    arr2.add(jo2);
                }
            }
        }
        return root;
    }

}
