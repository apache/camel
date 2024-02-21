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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedThrottlerMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.Throttler;
import org.apache.camel.processor.TotalRequestsThrottler;

import static org.apache.camel.builder.Builder.constant;

@ManagedResource(description = "Managed Concurrent Requests Throttler")
public class ManagedThrottler extends ManagedProcessor implements ManagedThrottlerMBean {
    private final Throttler throttler;

    public ManagedThrottler(CamelContext context, Throttler throttler,
                            ProcessorDefinition<?> definition) {
        super(context, throttler, definition);
        this.throttler = throttler;
    }

    public Throttler getThrottler() {
        return throttler;
    }

    @Override
    public long getMaximumRequests() {
        return throttler.getCurrentMaximumRequests();
    }

    @Override
    public void setMaximumRequests(long maximumConcurrentRequests) {
        throttler.setMaximumRequestsExpression(constant(maximumConcurrentRequests));
    }

    @Override
    public long getTimePeriodMillis() {
        if (throttler instanceof TotalRequestsThrottler t) {
            return t.getTimePeriodMillis();
        }

        return 0;
    }

    @Override
    public void setTimePeriodMillis(long timePeriodMillis) {
        if (throttler instanceof TotalRequestsThrottler t) {
            t.setTimePeriodMillis(timePeriodMillis);
        }
    }

    @Override
    public String getMode() {
        return throttler.getMode();
    }

    @Override
    public Boolean isAsyncDelayed() {
        return throttler.isAsyncDelayed();
    }

    @Override
    public Boolean isCallerRunsWhenRejected() {
        return throttler.isCallerRunsWhenRejected();
    }

    @Override
    public Boolean isRejectExecution() {
        return throttler.isRejectExecution();
    }
}
