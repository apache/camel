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

package org.apache.camel.util.backoff;

import java.io.Closeable;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.util.function.ThrowingFunction;

/**
 * A simple timer utility that use a linked {@link BackOff} to determine when a task should be executed.
 */
public class SimpleBackOffTimer implements BackOffTimer, Closeable {
    private final ScheduledExecutorService scheduler;
    private final String name;
    private final Set<BackOffTimerTask> tasks = new CopyOnWriteArraySet<>();

    public SimpleBackOffTimer(ScheduledExecutorService scheduler) {
        this("SimpleBackOffTimer", scheduler);
    }

    public SimpleBackOffTimer(String name, ScheduledExecutorService scheduler) {
        this.name = name;
        this.scheduler = scheduler;
    }

    /**
     * Schedule the given function/task to be executed some time in the future according to the given backOff.
     */
    public Task schedule(BackOff backOff, ThrowingFunction<Task, Boolean, Exception> function) {
        final BackOffTimerTask task = new BackOffTimerTask(this, backOff, scheduler, function);

        long delay = task.next();
        if (delay != BackOff.NEVER) {
            tasks.add(task);
            scheduler.schedule(task, delay, TimeUnit.MILLISECONDS);
        } else {
            task.cancel();
        }

        return task;
    }

    /**
     * Gets the name of this timer.
     */
    public String getName() {
        return name;
    }

    /**
     * Removes the task
     */
    public void remove(Task task) {
        tasks.remove(task);
    }

    /**
     * Access to unmodifiable set of all the tasks
     */
    public Set<Task> getTasks() {
        return Collections.unmodifiableSet(tasks);
    }

    /**
     * Number of tasks
     */
    public int size() {
        return tasks.size();
    }

    /**
     * Stops and closes this timer.
     */
    public void close() {
        tasks.clear();
    }
}
