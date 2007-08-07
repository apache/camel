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

import java.util.ArrayList;
import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.bam.TimeExpression;
import org.apache.camel.bam.model.ActivityState;
import org.apache.camel.bam.model.ProcessInstance;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.model.OutputType;
import org.apache.camel.model.RouteType;
import org.apache.camel.util.Time;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.camel.util.ServiceHelper.startServices;
import static org.apache.camel.util.ServiceHelper.stopServices;

/**
 * A temporal rule for use within BAM
 *
 * @version $Revision: $
 */
public class TemporalRule extends ServiceSupport {
    private static final transient Log LOG = LogFactory.getLog(TemporalRule.class);
    private TimeExpression first;
    private TimeExpression second;
    private long expectedMillis;
    private long overdueMillis;
    private Processor overdueAction;
    private OutputType overdueProcessors = new OutputType();

    public TemporalRule(TimeExpression first, TimeExpression second) {
        this.first = first;
        this.second = second;
    }

    public TemporalRule expectWithin(Time builder) {
        return expectWithin(builder.toMillis());
    }

    public TemporalRule expectWithin(long millis) {
        expectedMillis = millis;
        return this;
    }

    public OutputType errorIfOver(Time builder) {
        return errorIfOver(builder.toMillis());
    }

    public OutputType errorIfOver(long millis) {
        overdueMillis = millis;
        if (overdueProcessors == null) {
            overdueProcessors = new OutputType();
        }
        return overdueProcessors;
    }

    public TimeExpression getFirst() {
        return first;
    }

    public TimeExpression getSecond() {
        return second;
    }

    public Processor getOverdueAction() throws Exception {
        if (overdueAction == null && overdueProcessors != null) {

            // TOOD refactor to avoid this messyness...
            ArrayList<Route> list = new ArrayList<Route>();
            RouteType route = new RouteType();
            route.setCamelContext(first.getBuilder().getProcessBuilder().getContext());
            RouteContext routeContext = new RouteContext(route, null, list);

            overdueAction = overdueProcessors.createOutputsProcessor(routeContext);
        }
        return overdueAction;
    }

    public void processExchange(Exchange exchange, ProcessInstance instance) {
        Date firstTime = first.evaluate(instance);
        if (firstTime == null) {
            // ignore as first event has not accurred yet
            return;
        }

        // TODO now we might need to set the second activity state
        // to 'grey' to indicate it now could happen?

        // lets force the lazy creation of the second state
        ActivityState secondState = second.getOrCreateActivityState(instance);
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
    }

    public void processExpired(ActivityState activityState) throws Exception {
        Processor processor = getOverdueAction();
        if (processor != null) {
            Date now = new Date();
/*
            TODO this doesn't work and returns null for some strange reason
            ProcessInstance instance = activityState.getProcessInstance();
            ActivityState secondState = second.getActivityState(instance);
            if (secondState == null) {
                log.error("Could not find the second state! Process is: " 
                + instance + " with first state: " + first.getActivityState(instance) 
                + " and the state I was called with was: " + activityState);
            }
*/

            ActivityState secondState = activityState;
            Date overdue = secondState.getTimeOverdue();
            if (now.compareTo(overdue) >= 0) {
                Exchange exchange = createExchange();
                exchange.getIn().setBody(activityState);
                processor.process(exchange);
            } else {
                LOG.warn("Process has not actually expired; the time is: " + now + " but the overdue time is: " + overdue);
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

    protected void doStart() throws Exception {
        startServices(getOverdueAction());
    }

    protected void doStop() throws Exception {
        stopServices(getOverdueAction());
    }
}
