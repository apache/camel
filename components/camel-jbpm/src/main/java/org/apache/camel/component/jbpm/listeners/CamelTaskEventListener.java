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
package org.apache.camel.component.jbpm.listeners;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.component.jbpm.JBPMCamelConsumerAware;
import org.apache.camel.component.jbpm.JBPMConsumer;
import org.kie.api.task.TaskEvent;
import org.kie.api.task.TaskLifeCycleEventListener;
import org.kie.internal.runtime.Cacheable;


public class CamelTaskEventListener implements Cacheable, TaskLifeCycleEventListener, JBPMCamelConsumerAware {

    private Set<JBPMConsumer> consumers = new LinkedHashSet<>();
    
    @Override
    public void beforeTaskActivatedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskActivatedEvent", event);
    }

    @Override
    public void beforeTaskClaimedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskClaimedEvent", event);
    }

    @Override
    public void beforeTaskSkippedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskSkippedEvent", event);
    }

    @Override
    public void beforeTaskStartedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskStartedEvent", event);

    }

    @Override
    public void beforeTaskStoppedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskStoppedEvent", event);

    }

    @Override
    public void beforeTaskCompletedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskCompletedEvent", event);

    }

    @Override
    public void beforeTaskFailedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskFailedEvent", event);

    }

    @Override
    public void beforeTaskAddedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskAddedEvent", event);

    }

    @Override
    public void beforeTaskExitedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskExitedEvent", event);

    }

    @Override
    public void beforeTaskReleasedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskReleasedEvent", event);

    }

    @Override
    public void beforeTaskResumedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskResumedEvent", event);

    }

    @Override
    public void beforeTaskSuspendedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskSuspendedEvent", event);

    }

    @Override
    public void beforeTaskForwardedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskForwardedEvent", event);

    }

    @Override
    public void beforeTaskDelegatedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskDelegatedEvent", event);

    }

    @Override
    public void beforeTaskNominatedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeTaskNominatedEvent", event);

    }

    @Override
    public void afterTaskActivatedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskActivatedEvent", event);

    }

    @Override
    public void afterTaskClaimedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskClaimedEvent", event);

    }

    @Override
    public void afterTaskSkippedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskSkippedEvent", event);

    }

    @Override
    public void afterTaskStartedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskStartedEvent", event);

    }

    @Override
    public void afterTaskStoppedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskStoppedEvent", event);

    }

    @Override
    public void afterTaskCompletedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskCompletedEvent", event);

    }

    @Override
    public void afterTaskFailedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskFailedEvent", event);

    }

    @Override
    public void afterTaskAddedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskAddedEvent", event);

    }

    @Override
    public void afterTaskExitedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskExitedEvent", event);

    }

    @Override
    public void afterTaskReleasedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskReleasedEvent", event);

    }

    @Override
    public void afterTaskResumedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskResumedEvent", event);

    }

    @Override
    public void afterTaskSuspendedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskSuspendedEvent", event);

    }

    @Override
    public void afterTaskForwardedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskForwardedEvent", event);

    }

    @Override
    public void afterTaskDelegatedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskDelegatedEvent", event);

    }

    @Override
    public void afterTaskNominatedEvent(TaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterTaskNominatedEvent", event);

    }

    @Override
    public void close() {        

    }

    @Override
    public void addConsumer(JBPMConsumer consumer) {
        this.consumers.add(consumer);        
    }

    @Override
    public void removeConsumer(JBPMConsumer consumer) {
        this.consumers.remove(consumer);
    }
    
    protected void sendMessage(String eventType, Object event) {
        this.consumers.stream().filter(c -> c.getStatus().isStarted()).forEach(c -> c.sendMessage(eventType, event));
    }


}
