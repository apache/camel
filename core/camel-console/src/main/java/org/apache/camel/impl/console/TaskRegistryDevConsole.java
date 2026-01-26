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

import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.support.task.Task;
import org.apache.camel.support.task.TaskManagerRegistry;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "internal-tasks", displayName = "Internal Tasks", description = "Display information about internal tasks")
public class TaskRegistryDevConsole extends AbstractDevConsole {

    public TaskRegistryDevConsole() {
        super("camel", "internal-tasks", "Internal Tasks", "Display information about internal tasks");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        TaskManagerRegistry reg = PluginHelper.getTaskManagerRegistry(getCamelContext().getCamelContextExtension());
        sb.append(String.format("%nTasks: %s", reg.getSize()));
        int id = 0;
        for (Task task : reg.getTasks()) {
            String failure = task.getException() != null ? task.getException().getMessage() : "";
            sb.append(String.format(
                    "\n    #%d (name=%s status=%s attempts=%d delay=%d elapsed=%d first=%d last=%d next=%d failure=%s",
                    id, task.getName(), task.getStatus().name(), task.iteration(), task.getCurrentDelay(),
                    task.getCurrentElapsedTime(), task.getFirstAttemptTime(), task.getLastAttemptTime(),
                    task.getNextAttemptTime(), failure));
            id++;
        }
        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        root.put("tasks", arr);

        TaskManagerRegistry reg = PluginHelper.getTaskManagerRegistry(getCamelContext().getCamelContextExtension());
        for (Task task : reg.getTasks()) {
            JsonObject jo = new JsonObject();
            jo.put("name", task.getName());
            jo.put("status", task.getStatus().name());
            jo.put("attempts", task.iteration());
            jo.put("delay", task.getCurrentDelay());
            jo.put("elapsed", task.getCurrentElapsedTime());
            jo.put("firstTime", task.getFirstAttemptTime());
            jo.put("lastTime", task.getLastAttemptTime());
            jo.put("nextTime", task.getNextAttemptTime());
            String failure = task.getException() != null ? task.getException().getMessage() : "";
            if (failure != null) {
                jo.put("error", failure);
            }
            arr.add(jo);
        }
        return root;
    }

}
