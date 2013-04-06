/**
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
package org.apache.camel.bam.rules;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.bam.model.ActivityState;
import org.apache.camel.bam.model.ProcessDefinition;
import org.apache.camel.bam.model.ProcessInstance;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ServiceHelper;

/**
 * @version 
 */
public class ProcessRules extends ServiceSupport {
    private ProcessDefinition processDefinition;
    private List<ActivityRules> activities = new CopyOnWriteArrayList<ActivityRules>();

    public synchronized void processExpired(ActivityState activityState) throws Exception {
        for (ActivityRules activityRules : activities) {
            activityRules.processExpired(activityState);
        }
    }

    public synchronized void processExchange(Exchange exchange, ProcessInstance process) {
        for (ActivityRules activityRules : activities) {
            activityRules.processExchange(exchange, process);
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public List<ActivityRules> getActivities() {
        return activities;
    }

    public ProcessDefinition getProcessDefinition() {
        return processDefinition;
    }

    public void setProcessDefinition(ProcessDefinition processDefinition) {
        this.processDefinition = processDefinition;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void doStart() throws Exception {
        ServiceHelper.startServices(activities);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(activities);
    }
}



