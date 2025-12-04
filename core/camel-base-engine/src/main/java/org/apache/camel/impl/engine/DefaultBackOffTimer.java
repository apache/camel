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

package org.apache.camel.impl.engine;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.backoff.BackOff;
import org.apache.camel.util.backoff.BackOffTimer;
import org.apache.camel.util.backoff.BackOffTimerTask;
import org.apache.camel.util.function.ThrowingFunction;

/**
 * Default {@link BackOffTimer}.
 */
public class DefaultBackOffTimer extends ServiceSupport implements BackOffTimer {

    private final CamelContext camelContext;
    private final ScheduledExecutorService scheduler;
    private final String name;
    private final Set<Task> tasks = new CopyOnWriteArraySet<>();

    public DefaultBackOffTimer(CamelContext camelContext, String name, ScheduledExecutorService scheduler) {
        this.camelContext = camelContext;
        this.scheduler = scheduler;
        this.name = name;
    }

    @Override
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

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void remove(Task task) {
        tasks.remove(task);
    }

    @Override
    public Set<Task> getTasks() {
        return Collections.unmodifiableSet(tasks);
    }

    @Override
    public int size() {
        return tasks.size();
    }

    @Override
    protected void doStart() throws Exception {
        camelContext.addService(this);
    }

    @Override
    protected void doStop() throws Exception {
        tasks.clear();
        camelContext.removeService(this);
    }
}
