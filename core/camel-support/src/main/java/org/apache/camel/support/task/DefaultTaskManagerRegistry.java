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

package org.apache.camel.support.task;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.camel.CamelContext;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Default {@link TaskManagerRegistry}.
 */
public class DefaultTaskManagerRegistry extends ServiceSupport implements TaskManagerRegistry {

    private final CamelContext camelContext;
    private final Set<Task> tasks = new CopyOnWriteArraySet<>();

    public DefaultTaskManagerRegistry(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        tasks.clear();
    }

    @Override
    public void addTask(Task task) {
        tasks.add(task);
    }

    @Override
    public void removeTask(Task task) {
        tasks.remove(task);
    }

    @Override
    public int getSize() {
        return tasks.size();
    }

    @Override
    public Set<Task> getTasks() {
        return Collections.unmodifiableSet(tasks);
    }
}
