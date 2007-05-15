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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.bam.model.Activity;
import org.apache.camel.bam.model.ActivityState;
import org.apache.camel.builder.ProcessorFactory;

/**
 * @version $Revision: $
 */
public class ActivityBuilder implements ProcessorFactory {
    private ProcessBuilder processBuilder;
    private Endpoint endpoint;
    private Activity activity;
    private Expression correlationExpression;

    public ActivityBuilder(ProcessBuilder processBuilder, Endpoint endpoint) {
        this.processBuilder = processBuilder;
        this.endpoint = endpoint;
        this.activity = new Activity(processBuilder.getProcess());
        this.activity.setName(endpoint.getEndpointUri());
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public Processor createProcessor() throws Exception {
        return processBuilder.createActivityProcessor(this);
    }

    // Builder methods
    //-----------------------------------------------------------------------
    public ActivityBuilder correlate(Expression correlationExpression) {
        this.correlationExpression = correlationExpression;
        return this;
    }

    public ActivityBuilder name(String name) {
        activity.setName(name);
        return this;
    }

    /**
     * Create a temporal rule for when this step starts
     */
    public TimeExpression starts() {
        return createTimeExpression(new ActivityExpressionSupport(activity) {
            protected Object evaluateState(Exchange exchange, ActivityState state) {
                return state.getStartTime();
            }
        });
    }

    /**
     * Create a temporal rule for when this step completes
     */
    public TimeExpression completes() {
        return createTimeExpression(new ActivityExpressionSupport(activity) {
            protected Object evaluateState(Exchange exchange, ActivityState state) {
                return state.getCompleteTime();
            }
        });
    }


    // Properties
    //-----------------------------------------------------------------------
    public Expression getCorrelationExpression() {
        return correlationExpression;
    }

    public Activity getActivity() {
        return activity;
    }

    public ProcessBuilder getProcessBuilder() {
        return processBuilder;
    }

    // Implementation methods
    //-----------------------------------------------------------------------
    protected TimeExpression createTimeExpression(ActivityExpressionSupport expression) {
        return new TimeExpression(activity, expression);
    }


}
