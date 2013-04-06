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

import org.apache.camel.bam.model.ActivityState;
import org.apache.camel.bam.model.ProcessInstance;
import org.apache.camel.bam.rules.ActivityRules;
import org.apache.camel.bam.rules.TemporalRule;
import static org.apache.camel.util.ObjectHelper.equal;

/**
 * @version 
 */
public abstract class TimeExpression {
    private ActivityRules activityRules;
    private ActivityBuilder builder;
    private ActivityLifecycle lifecycle;

    public TimeExpression(ActivityBuilder builder, ActivityLifecycle lifecycle) {
        this.lifecycle = lifecycle;
        this.builder = builder;
        this.activityRules = builder.getActivityRules();
    }

    public boolean isActivityLifecycle(ActivityRules activityRules, ActivityLifecycle lifecycle) {
        return equal(activityRules, this.activityRules) && equal(lifecycle, this.lifecycle);
    }

    /**
     * Creates a new temporal rule on this expression and the other expression
     */
    public TemporalRule after(TimeExpression expression) {
        TemporalRule rule = new TemporalRule(expression, this);
        rule.getSecond().getActivityRules().addRule(rule);
        return rule;
    }

    public Date evaluate(ProcessInstance processInstance) {
        ActivityState state = processInstance.getActivityState(activityRules);
        if (state != null) {
            return evaluate(processInstance, state);
        }
        return null;
    }

    public abstract Date evaluate(ProcessInstance instance, ActivityState state);

    // Properties
    //-------------------------------------------------------------------------

    public ActivityBuilder getBuilder() {
        return builder;
    }

    public ActivityRules getActivityRules() {
        return activityRules;
    }

    public ActivityLifecycle getLifecycle() {
        return lifecycle;
    }

    public ActivityState getActivityState(ProcessInstance instance) {
        return instance.getActivityState(activityRules);
    }

    public ActivityState getOrCreateActivityState(ProcessInstance instance) {
        return instance.getOrCreateActivityState(activityRules);
    }
}
