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
package org.apache.camel.bam.model;

import org.apache.camel.Exchange;
import org.apache.camel.bam.*;
import org.apache.camel.bam.Activity;
import org.apache.camel.util.ObjectHelper;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;

/**
 * The default state for a specific activity within a process
 *
 * @version $Revision: $
 */
@Entity
public class ActivityState extends TemporalEntity implements TimerEventHandler {
    private ProcessInstance process;
    private int receivedMessageCount;
    private String activityName;

    public synchronized void process(org.apache.camel.bam.Activity activity, Exchange exchange) throws Exception {
        int messageCount = getReceivedMessageCount() + 1;
        setReceivedMessageCount(messageCount);

        if (messageCount == 1) {
            onFirstMessage(exchange);
        }
        int expectedMessages = activity.getExpectedMessages();
        if (messageCount == expectedMessages) {
            onExpectedMessage(exchange);
        }
        else if (messageCount > expectedMessages) {
            onExcessMessage(exchange);
        }

        // now lets fire any assertions on the activity
        activity.process(this, exchange);
    }

    /**
     * Returns true if this state is for the given activity
     */
    public boolean isActivity(Activity activity) {
        return ObjectHelper.equals(getActivityName(), activity.getName());
    }

    /**
     * Invoked by the timer firing
     */
    public void onTimerEvent(TimerEvent event) {
        // TODO do check on this entity
    }

    // Properties
    //-----------------------------------------------------------------------
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST})
    public ProcessInstance getProcess() {
        return process;
    }

    public void setProcess(ProcessInstance process) {
        this.process = process;
        process.getActivityStates().add(this);
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public int getReceivedMessageCount() {
        return receivedMessageCount;
    }

    public void setReceivedMessageCount(int receivedMessageCount) {
        this.receivedMessageCount = receivedMessageCount;
    }

    // Implementation methods
    //-----------------------------------------------------------------------


    /**
     * Called when the first message is reached
     */
    protected void onFirstMessage(Exchange exchange) {
        setTimeStarted(currentTime());
    }

    /**
     * Called when the expected number of messages are is reached
     */
    protected void onExpectedMessage(Exchange exchange) {
        setTimeCompleted(currentTime());
        setCompleted(true);
    }

    /**
     * Called when an excess message (after the expected number of messages)
     * are received
     */
    protected void onExcessMessage(Exchange exchange) {
        // TODO
    }

    protected long currentTime() {
        return System.currentTimeMillis();
    }

}