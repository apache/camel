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
package org.apache.camel.bam;

import java.util.Date;

import org.apache.camel.Endpoint;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.bam.model.ActivityState;
import org.apache.camel.bam.model.ProcessInstance;
import org.apache.camel.bam.rules.ActivityRules;

/**
 * @version 
 */
public class ActivityBuilder {
    private final ProcessBuilder processBuilder;
    private final Endpoint endpoint;
    private final ActivityRules activityRules;
    private Expression correlationExpression;
    private volatile Processor processor;

    public ActivityBuilder(ProcessBuilder processBuilder, Endpoint endpoint) {
        this.processBuilder = processBuilder;
        this.endpoint = endpoint;
        this.activityRules = new ActivityRules(processBuilder);
        this.activityRules.setActivityName(endpoint.getEndpointUri());
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public Processor createProcessor() throws Exception {
        return processBuilder.createActivityProcessor(this);
    }

    /**
     * Returns the processor of the route
     */
    public synchronized Processor getProcessor() throws Exception {
        if (processor == null) {
            processor = createProcessor();
        }
        if (processor == null) {
            throw new IllegalArgumentException("No processor created for ActivityBuilder: " + this);
        }
        return processor;
    }

    // Builder methods
    //-----------------------------------------------------------------------
    public ActivityBuilder correlate(Expression correlationExpression) {
        this.correlationExpression = correlationExpression;
        return this;
    }

    public ActivityBuilder name(String name) {
        activityRules.setActivityName(name);
        return this;
    }

    /**
     * Create a temporal rule for when this step starts
     */
    public TimeExpression starts() {
        return new TimeExpression(this, ActivityLifecycle.Started) {
            public Date evaluate(ProcessInstance instance, ActivityState state) {
                return state.getTimeStarted();
            }
        };
    }

    /**
     * Create a temporal rule for when this step completes
     */
    public TimeExpression completes() {
        return new TimeExpression(this, ActivityLifecycle.Completed) {
            public Date evaluate(ProcessInstance instance, ActivityState state) {
                return state.getTimeCompleted();
            }
        };
    }

    // Properties
    //-----------------------------------------------------------------------
    public Expression getCorrelationExpression() {
        return correlationExpression;
    }

    public ActivityRules getActivityRules() {
        return activityRules;
    }

    public ProcessBuilder getProcessBuilder() {
        return processBuilder;
    }
}
