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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.bam.model.ActivityState;
import org.apache.camel.bam.model.ProcessInstance;
import org.apache.camel.builder.FromBuilder;
import org.apache.camel.builder.ProcessorFactory;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.util.Time;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;

/**
 * A temporal rule
 *
 * @version $Revision: $
 */
public class TemporalRule {
    private static final transient Log log = LogFactory.getLog(TemporalRule.class);
    private TimeExpression first;
    private TimeExpression second;
    private long expectedMillis;
    private long overdueMillis;
    private Processor overdueAction;
    private ProcessorFactory overdueProcessorFactory;

    public TemporalRule(TimeExpression left, TimeExpression right) {
        this.first = left;
        this.second = right;
    }

    public TemporalRule expectWithin(Time builder) {
        return expectWithin(builder.toMillis());
    }

    public TemporalRule expectWithin(long millis) {
        expectedMillis = millis;
        return this;
    }

    public FromBuilder errorIfOver(Time builder) {
        return errorIfOver(builder.toMillis());
    }

    public FromBuilder errorIfOver(long millis) {
        overdueMillis = millis;

        FromBuilder builder = new FromBuilder(second.getBuilder().getProcessBuilder(), null);
        overdueProcessorFactory = builder;
        return builder;
    }

    public TimeExpression getFirst() {
        return first;
    }

    public TimeExpression getSecond() {
        return second;
    }

    public void evaluate(ProcessContext context, ActivityState activityState) {
        ProcessInstance instance = context.getProcessInstance();

        Date firstTime = first.evaluateState(instance);
        if (firstTime == null) {
            // ignore as first event has not accurred yet
            return;
        }

        // TODO now we might need to set the second activity state
        // to 'grey' to indicate it now could happen?
        // if the second activity state is not created yet we might wanna create it

        ActivityState secondState = second.getActivityState(instance);
        if (expectedMillis > 0L) {
            Date expected = secondState.getTimeExpected();
            if (expected == null) {
                expected = add(firstTime, expectedMillis);
                secondState.setTimeExpected(expected);
            }
        }
        if (overdueMillis > 0L) {
            Date overdue = secondState.getTimeOverdue();
            if (overdue == null) {
                overdue = add(firstTime, overdueMillis);
                secondState.setTimeOverdue(overdue);
            }
        }

        Date secondTime = second.evaluateState(instance);
        if (secondTime == null) {
            // TODO add test that things have expired
        }
        else {

/*
            if (secondTime.delta(firstTime.plus(gap)) > 0) {
                // TODO
            }
*/
        }
    }

    public void processExpired(ActivityState activityState) throws Exception {
        if (overdueAction == null && overdueProcessorFactory != null) {
            overdueAction = overdueProcessorFactory.createProcessor();
        }
        if (overdueAction != null) {
            Date now = new Date();
            ProcessInstance instance = activityState.getProcess();
            ActivityState secondState = second.getActivityState(instance);
            Date overdue = secondState.getTimeOverdue();
            if (now.compareTo(overdue) >= 0) {
                Exchange exchange = createExchange();
                exchange.getIn().setBody(activityState);
                overdueAction.process(exchange);
            }
            else {
                log.warn("Process has not actually expired; the time is: " + now + " but the overdue time is: " + overdue);
            }
        }
    }

    protected Exchange createExchange() {
        return new DefaultExchange(second.getBuilder().getProcessBuilder().getContext());
    }

    /**
     * Returns the date in the future adding the given number of millis
     *
     * @param date
     * @param millis
     * @return the date in the future
     */
    protected Date add(Date date, long millis) {
        return new Date(date.getTime() + millis);
    }

    /*
    public void onActivityLifecycle(ActivityState state, ActivityRules activityRules, ActivityLifecycle lifecycle) {
        if (first.isActivityLifecycle(activityRules, lifecycle)) {
            // lets create the expected and error timers

            // TODO we could use a single timer event; then keep incrementing its type
            // counter to escalate & use different times each time to reduce some DB work
            createTimer(state, expectedMillis);
            createTimer(state, overdueMillis);
        }
    }
    */
}
