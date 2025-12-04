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

package org.apache.camel.management.mbean;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedTaskManagerRegistryMBean;
import org.apache.camel.support.task.BackgroundTask;
import org.apache.camel.support.task.Task;
import org.apache.camel.support.task.TaskManagerRegistry;

@ManagedResource(description = "Managed TaskManagerRegistry")
public class ManagedTaskManagerRegistry extends ManagedService implements ManagedTaskManagerRegistryMBean {

    private final TaskManagerRegistry registry;

    public ManagedTaskManagerRegistry(CamelContext context, TaskManagerRegistry registry) {
        super(context, registry);
        this.registry = registry;
    }

    @Override
    public Integer getSize() {
        return registry.getSize();
    }

    @Override
    public TabularData listTasks() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listInternalTaskTabularType());
            for (Task task : registry.getTasks()) {
                String name = task.getName();
                String kind = task instanceof BackgroundTask ? "background" : "foreground";
                String status = task.getStatus().name();
                long attempts = task.iteration();
                long delay = task.getCurrentDelay();
                long elapsed = task.getCurrentElapsedTime();
                long firstTime = task.getFirstAttemptTime();
                long lastTime = task.getLastAttemptTime();
                long nextTime = task.getNextAttemptTime();
                String failure =
                        task.getException() != null ? task.getException().getMessage() : null;
                CompositeType ct = CamelOpenMBeanTypes.listInternalTaskCompositeType();
                CompositeData data = new CompositeDataSupport(
                        ct,
                        new String[] {
                            "name",
                            "kind",
                            "status",
                            "attempts",
                            "delay",
                            "elapsed",
                            "firstTime",
                            "lastTime",
                            "nextTime",
                            "failure"
                        },
                        new Object[] {
                            name, kind, status, attempts, delay, elapsed, firstTime, lastTime, nextTime, failure
                        });
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
