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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.support.task.Task;
import org.apache.camel.support.task.TaskManagerRegistry;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "internal-tasks", displayName = "Internal Tasks", description = "Display information about internal tasks")
public class TaskRegistryDevConsole extends AbstractDevConsole {

    public record TaskEntry(
            @Metadata(description = "The task name") String name,
            @Metadata(description = "The task status") String status,
            @Metadata(description = "Whether the task is currently attempting") boolean attempting,
            @Metadata(description = "The current number of iterations") int attempts,
            @Metadata(description = "The current computed delay") long delay,
            @Metadata(description = "The current elapsed time") long elapsed,
            @Metadata(description = "The time the first attempt was performed") long firstTime,
            @Metadata(description = "The time the last attempt was performed") long lastTime,
            @Metadata(description = "The time the next attempt will be made") long nextTime,
            @Metadata(description = "The failure message (only present when known)") String error) {
    }

    public record Response(@Metadata(description = "The internal tasks") List<TaskEntry> tasks) {
    }

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
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        List<TaskEntry> tasks = new ArrayList<>();

        TaskManagerRegistry reg = PluginHelper.getTaskManagerRegistry(getCamelContext().getCamelContextExtension());
        for (Task task : reg.getTasks()) {
            String failure = task.getException() != null ? task.getException().getMessage() : "";
            tasks.add(new TaskEntry(
                    task.getName(), task.getStatus().name(), task.isAttempting(), task.iteration(),
                    task.getCurrentDelay(), task.getCurrentElapsedTime(), task.getFirstAttemptTime(),
                    task.getLastAttemptTime(), task.getNextAttemptTime(), failure));
        }

        Response response = new Response(tasks);
        return JsonRecordSupport.toJsonObject(response);
    }

}
