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
import org.jbpm.casemgmt.api.event.CaseCancelEvent;
import org.jbpm.casemgmt.api.event.CaseCloseEvent;
import org.jbpm.casemgmt.api.event.CaseCommentEvent;
import org.jbpm.casemgmt.api.event.CaseDataEvent;
import org.jbpm.casemgmt.api.event.CaseDestroyEvent;
import org.jbpm.casemgmt.api.event.CaseDynamicSubprocessEvent;
import org.jbpm.casemgmt.api.event.CaseDynamicTaskEvent;
import org.jbpm.casemgmt.api.event.CaseEventListener;
import org.jbpm.casemgmt.api.event.CaseReopenEvent;
import org.jbpm.casemgmt.api.event.CaseRoleAssignmentEvent;
import org.jbpm.casemgmt.api.event.CaseStartEvent;
import org.kie.internal.runtime.Cacheable;


public class CamelCaseEventListener implements CaseEventListener, Cacheable, JBPMCamelConsumerAware {    
    
    private Set<JBPMConsumer> consumers = new LinkedHashSet<>();
    
    @Override
    public void beforeCaseStarted(CaseStartEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeCaseStarted", event);
    }

    @Override
    public void afterCaseStarted(CaseStartEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterCaseStarted", event);
    }

    @Override
    public void beforeCaseClosed(CaseCloseEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeCaseClosed", event);
    }

    @Override
    public void afterCaseClosed(CaseCloseEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterCaseClosed", event);
    }

    @Override
    public void beforeCaseCancelled(CaseCancelEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeCaseCancelled", event);
    }

    @Override
    public void afterCaseCancelled(CaseCancelEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterCaseCancelled", event);
    }

    @Override
    public void beforeCaseDestroyed(CaseDestroyEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeCaseDestroyed", event);
    }

    @Override
    public void afterCaseDestroyed(CaseDestroyEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterCaseDestroyed", event);
    }

    @Override
    public void beforeCaseReopen(CaseReopenEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeCaseReopen", event);
    }

    @Override
    public void afterCaseReopen(CaseReopenEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterCaseReopen", event);
    }

    @Override
    public void beforeCaseCommentAdded(CaseCommentEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeCaseCommentAdded", event);
    }

    @Override
    public void afterCaseCommentAdded(CaseCommentEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterCaseCommentAdded", event);
    }

    @Override
    public void beforeCaseCommentUpdated(CaseCommentEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeCaseCommentUpdated", event);
    }

    @Override
    public void afterCaseCommentUpdated(CaseCommentEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterCaseCommentUpdated", event);
    }

    @Override
    public void beforeCaseCommentRemoved(CaseCommentEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeCaseCommentRemoved", event);
    }

    @Override
    public void afterCaseCommentRemoved(CaseCommentEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterCaseCommentRemoved", event);
    }

    @Override
    public void beforeCaseRoleAssignmentAdded(CaseRoleAssignmentEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeCaseRoleAssignmentAdded", event);
    }

    @Override
    public void afterCaseRoleAssignmentAdded(CaseRoleAssignmentEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterCaseRoleAssignmentAdded", event);
    }

    @Override
    public void beforeCaseRoleAssignmentRemoved(CaseRoleAssignmentEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeCaseRoleAssignmentRemoved", event);
    }

    @Override
    public void afterCaseRoleAssignmentRemoved(CaseRoleAssignmentEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterCaseRoleAssignmentRemoved", event);
    }

    @Override
    public void beforeCaseDataAdded(CaseDataEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeCaseDataAdded", event);
    }

    @Override
    public void afterCaseDataAdded(CaseDataEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterCaseDataAdded", event);
    }

    @Override
    public void beforeCaseDataRemoved(CaseDataEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeCaseDataRemoved", event);
    }

    @Override
    public void afterCaseDataRemoved(CaseDataEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterCaseDataRemoved", event);
    }

    @Override
    public void beforeDynamicTaskAdded(CaseDynamicTaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeDynamicTaskAdded", event);
    }

    @Override
    public void afterDynamicTaskAdded(CaseDynamicTaskEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterDynamicTaskAdded", event);
    }

    @Override
    public void beforeDynamicProcessAdded(CaseDynamicSubprocessEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("beforeDynamicProcessAdded", event);
    }

    @Override
    public void afterDynamicProcessAdded(CaseDynamicSubprocessEvent event) {
        if (consumers.isEmpty()) {
            return;
        }
        sendMessage("afterDynamicProcessAdded", event);
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
