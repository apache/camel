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
package org.apache.camel.bam.model;

import java.util.Date;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

import org.apache.camel.bam.processor.ProcessContext;
import org.apache.camel.bam.rules.ActivityRules;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default state for a specific activity within a process
 *
 * @version 
 */
@Entity
@Table(
    name = "CAMEL_ACTIVITYSTATE"
)
public class ActivityState extends TemporalEntity {
    private static final Logger LOG = LoggerFactory.getLogger(ActivityState.class);
    private ProcessInstance processInstance;
    private Integer receivedMessageCount = 0;
    private ActivityDefinition activityDefinition;
    private Date timeExpected;
    private Date timeOverdue;
    private Integer escalationLevel = 0;

    @Override
    public String toString() {
        return "ActivityState[" + getId() + " on " + getProcessInstance() + " " + getActivityDefinition() + "]";
    }

    public synchronized void processExchange(ActivityRules activityRules, ProcessContext context) throws Exception {
        int messageCount = 0;
        Integer count = getReceivedMessageCount();
        if (count != null) {
            messageCount = count.intValue();
        }
        setReceivedMessageCount(++messageCount);

        if (messageCount == 1) {
            onFirstMessage(context);
        }
        int expectedMessages = activityRules.getExpectedMessages();
        if (messageCount == expectedMessages) {
            onExpectedMessage(context);
        } else if (messageCount > expectedMessages) {
            onExcessMessage(context);
        }
    }

    /**
     * Returns true if this state is for the given activity
     */
    public boolean isActivity(ActivityRules activityRules) {
        return ObjectHelper.equal(getActivityDefinition(), activityRules.getActivityDefinition());
    }

    // Properties
    // -----------------------------------------------------------------------
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE })
    public ProcessInstance getProcessInstance() {
        return processInstance;
    }

    public void setProcessInstance(ProcessInstance processInstance) {
        this.processInstance = processInstance;
        processInstance.getActivityStates().add(this);
    }

    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE })
    public ActivityDefinition getActivityDefinition() {
        return activityDefinition;
    }

    public void setActivityDefinition(ActivityDefinition activityDefinition) {
        this.activityDefinition = activityDefinition;
    }

    public Integer getEscalationLevel() {
        return escalationLevel;
    }

    public void setEscalationLevel(Integer escalationLevel) {
        this.escalationLevel = escalationLevel;
    }

    public Integer getReceivedMessageCount() {
        return receivedMessageCount;
    }

    public void setReceivedMessageCount(Integer receivedMessageCount) {
        this.receivedMessageCount = receivedMessageCount;
    }

    @Temporal(TemporalType.TIME)
    public Date getTimeExpected() {
        return timeExpected;
    }

    public void setTimeExpected(Date timeExpected) {
        this.timeExpected = timeExpected;
    }

    @Temporal(TemporalType.TIME)
    public Date getTimeOverdue() {
        return timeOverdue;
    }

    public void setTimeOverdue(Date timeOverdue) {
        this.timeOverdue = timeOverdue;
    }

    public void setTimeCompleted(Date timeCompleted) {
        super.setTimeCompleted(timeCompleted);
        if (timeCompleted != null) {
            setEscalationLevel(-1);
        }
    }

    @Transient
    public String getCorrelationKey() {
        ProcessInstance pi = getProcessInstance();
        if (pi == null) {
            return null;
        }
        return pi.getCorrelationKey();
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    /**
     * Called when the first message is reached
     */
    protected void onFirstMessage(ProcessContext context) {
        if (!isStarted()) {
            setTimeStarted(currentTime());
            context.onStarted(this);
            LOG.debug("Activity first message: {}", this);
        }
    }

    /**
     * Called when the expected number of messages are is reached
     */
    protected void onExpectedMessage(ProcessContext context) {
        if (!isCompleted()) {
            setTimeCompleted(currentTime());
            // must also clear overdue otherwise we will get failures
            setTimeOverdue(null);
            context.onCompleted(this);
            LOG.debug("Activity complete: {}", this);
        }
    }

    /**
     * Called when an excess message (after the expected number of messages) are
     * received
     */
    protected void onExcessMessage(ProcessContext context) {
        // TODO
    }

    protected Date currentTime() {
        return new Date();
    }
}
