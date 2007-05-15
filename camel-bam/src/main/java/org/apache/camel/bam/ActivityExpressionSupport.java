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
import org.apache.camel.bam.Activity;
import org.apache.camel.bam.model.ActivityState;
import org.apache.camel.bam.model.ProcessInstance;

/**
 * @version $Revision: $
 */
public abstract class ActivityExpressionSupport extends ProcessExpressionSupport<ProcessInstance> {
    private Activity activity;

    protected ActivityExpressionSupport(Activity activity) {
        super(ProcessInstance.class);
        this.activity = activity;
    }

    protected Object evaluate(Exchange exchange, ProcessInstance processEntity) {
        ActivityState state = processEntity.getActivityState(activity);
        if (state != null) {
            return evaluateState(exchange, state);
        }
        return null;
    }

    protected abstract Object evaluateState(Exchange exchange, ActivityState state);
}