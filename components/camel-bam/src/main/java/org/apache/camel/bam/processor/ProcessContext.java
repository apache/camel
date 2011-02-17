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
package org.apache.camel.bam.processor;

import org.apache.camel.Exchange;
import org.apache.camel.bam.model.ActivityState;
import org.apache.camel.bam.model.ProcessInstance;
import org.apache.camel.bam.rules.ActivityRules;
import org.apache.camel.bam.rules.ProcessRules;

/**
 * @version 
 */
public class ProcessContext {
    private Exchange exchange;
    private ProcessRules processRules;
    private ActivityRules activityRules;
    private ProcessInstance processInstance;
    private ActivityState activityState;

    public ProcessContext(Exchange exchange, ActivityRules activityRules, ActivityState activityState) {
        this.exchange = exchange;
        this.activityRules = activityRules;
        this.activityState = activityState;
        this.processRules = activityRules.getProcessRules();
        this.processInstance = activityState.getProcessInstance();
    }

    public ActivityRules getActivity() {
        return activityRules;
    }

    public void setActivity(ActivityRules activityRules) {
        this.activityRules = activityRules;
    }

    public ActivityState getActivityState() {
        return activityState;
    }

    public void setActivityState(ActivityState activityState) {
        this.activityState = activityState;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    public ProcessRules getProcessDefinition() {
        return processRules;
    }

    public void setProcessDefinition(ProcessRules processRules) {
        this.processRules = processRules;
    }

    public ProcessInstance getProcessInstance() {
        return processInstance;
    }

    public void setProcessInstance(ProcessInstance processInstance) {
        this.processInstance = processInstance;
    }

    public ActivityState getActivityState(ActivityRules activityRules) {
        return getProcessInstance().getActivityState(activityRules);
    }

    /**
     * Called when the activity is started which may end up creating some timers
     * for dependent actions
     */
    public void onStarted(ActivityState activityState) {
        // noop
    }

    /**
     * Called when the activity is completed which may end up creating some timers
     * for dependent actions
     */
    public void onCompleted(ActivityState activityState) {
        // noop
    }
}
