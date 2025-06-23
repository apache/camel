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

import java.util.Set;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedBackoffTimerMBean;
import org.apache.camel.util.backoff.BackOffTimer;

@ManagedResource(description = "Managed BackoffTimer")
public class ManagedBackoffTimer extends ManagedService implements ManagedBackoffTimerMBean {

    private final BackOffTimer timer;

    public ManagedBackoffTimer(CamelContext context, BackOffTimer timer) {
        super(context, (Service) timer);
        this.timer = timer;
    }

    @Override
    public String getName() {
        return timer.getName();
    }

    @Override
    public Integer getSize() {
        return timer.size();
    }

    @Override
    public TabularData listTasks() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.listBackoffTTaskTabularType());
            Set<BackOffTimer.Task> tasks = timer.getTasks();
            for (BackOffTimer.Task task : tasks) {
                String name = task.getName();
                String status = task.getStatus().name();
                long attempts = task.getCurrentAttempts();
                long delay = task.getCurrentDelay();
                long elapsed = task.getCurrentElapsedTime();
                long firstTime = task.getFirstAttemptTime();
                long lastTime = task.getLastAttemptTime();
                long nextTime = task.getNextAttemptTime();
                String failure = task.getException() != null ? task.getException().getMessage() : null;
                CompositeType ct = CamelOpenMBeanTypes.listBackoffTaskCompositeType();
                CompositeData data = new CompositeDataSupport(
                        ct,
                        new String[] {
                                "name", "status", "attempts", "delay", "elapsed", "firstTime", "lastTime", "nextTime",
                                "failure" },
                        new Object[] { name, status, attempts, delay, elapsed, firstTime, lastTime, nextTime, failure });
                answer.put(data);
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

}
