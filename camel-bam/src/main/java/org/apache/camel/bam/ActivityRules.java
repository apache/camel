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
package org.apache.camel.bam;

import org.apache.camel.bam.model.ActivityDefinition;
import org.apache.camel.bam.model.ActivityState;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a activity which is typically a system or could be an endpoint
 *
 * @version $Revision: $
 */
public class ActivityRules {
    private static final transient Log log = LogFactory.getLog(ActivityRules.class);
    private int expectedMessages = 1;
    private ActivityDefinition activity;
    private ProcessRules process;
    private List<TemporalRule> rules = new ArrayList<TemporalRule>();
    private String activityName;

    public ActivityRules(ProcessRules process) {
        this.process = process;
        process.getActivities().add(this);
    }

    public ActivityDefinition getActivity() {
        return activity;
    }

    public void setActivity(ActivityDefinition activity) {
        this.activity = activity;
    }

    public int getExpectedMessages() {
        return expectedMessages;
    }

    public void setExpectedMessages(int expectedMessages) {
        this.expectedMessages = expectedMessages;
    }

    public ProcessRules getProcess() {
        return process;
    }

    /**
     * Perform any assertions after the state has been updated
     */
    public void processExchange(ActivityState activityState, ProcessContext context) {

        log.info("Received state: " + activityState
                + " message count " + activityState.getReceivedMessageCount()
                + " started: " + activityState.getTimeStarted()
                + " completed: " + activityState.getTimeCompleted());

/*
        process.fireRules(activityState, context);

        for (TemporalRule rule : rules) {
            rule.evaluate(context, activityState);
        }
*/
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public void addRule(TemporalRule rule) {
        rules.add(rule);
    }

    /**
     * Handles overdue activities
     */
    public void processExpired(ActivityState activityState) throws Exception {
        for (TemporalRule rule : rules) {
            rule.processExpired(activityState);
        }
    }
}
