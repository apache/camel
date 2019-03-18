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
import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.event.process.ProcessStartedEvent;
import org.kie.api.event.process.ProcessVariableChangedEvent;
import org.kie.internal.runtime.Cacheable;


public class CamelProcessEventListener implements ProcessEventListener, Cacheable, JBPMCamelConsumerAware {

    private Set<JBPMConsumer> consumers = new LinkedHashSet<>();

    @Override
    public void beforeProcessStarted(ProcessStartedEvent event) {
        if (consumers.isEmpty()) {
            return;
        }

        sendMessage("beforeProcessStarted", event);
    }

    @Override
    public void afterProcessStarted(ProcessStartedEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        
        sendMessage("afterProcessStarted", event);
    }

    @Override
    public void beforeProcessCompleted(ProcessCompletedEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeProcessCompleted", event);
    }

    @Override
    public void afterProcessCompleted(ProcessCompletedEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterProcessCompleted", event);
    }

    @Override
    public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeNodeTriggered", event);
    }

    @Override
    public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterNodeTriggered", event);
    }

    @Override
    public void beforeNodeLeft(ProcessNodeLeftEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeNodeLeft", event);
    }

    @Override
    public void afterNodeLeft(ProcessNodeLeftEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterNodeLeft", event);
    }

    @Override
    public void beforeVariableChanged(ProcessVariableChangedEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeVariableChanged", event);
    }

    @Override
    public void afterVariableChanged(ProcessVariableChangedEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterVariableChanged", event);
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
