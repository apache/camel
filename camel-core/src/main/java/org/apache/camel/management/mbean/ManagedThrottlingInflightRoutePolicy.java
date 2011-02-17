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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.impl.ThrottlingInflightRoutePolicy;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * @version 
 */
@ManagedResource(description = "Managed ThrottlingInflightRoutePolicy")
public class ManagedThrottlingInflightRoutePolicy extends ManagedService {
    private final ThrottlingInflightRoutePolicy policy;

    public ManagedThrottlingInflightRoutePolicy(CamelContext context, ThrottlingInflightRoutePolicy policy) {
        super(context, policy);
        this.policy = policy;
    }

    public ThrottlingInflightRoutePolicy getPolicy() {
        return policy;
    }

    @ManagedAttribute(description = "Maximum inflight exchanges")
    public int getMaxInflightExchanges() {
        return getPolicy().getMaxInflightExchanges();
    }

    @ManagedAttribute(description = "Maximum inflight exchanges")
    public void setMaxInflightExchanges(int maxInflightExchanges) {
        getPolicy().setMaxInflightExchanges(maxInflightExchanges);
    }

    @ManagedAttribute(description = "Resume percentage of maximum inflight exchanges")
    public int getResumePercentOfMax() {
        return getPolicy().getResumePercentOfMax();
    }

    @ManagedAttribute(description = "Resume percentage of maximum inflight exchanges")
    public void setResumePercentOfMax(int resumePercentOfMax) {
        getPolicy().setResumePercentOfMax(resumePercentOfMax);
    }

    @ManagedAttribute(description = "Scope")
    public String getScope() {
        return getPolicy().getScope().name();
    }

    @ManagedAttribute(description = "Scope")
    public void setScope(String scope) {
        getPolicy().setScope(ThrottlingInflightRoutePolicy.ThrottlingScope.valueOf(scope));
    }

    @ManagedAttribute(description = "Logging Level")
    public String getLoggingLevel() {
        return getPolicy().getLoggingLevel().name();
    }

    @ManagedAttribute(description = "Logging Level")
    public void setLoggingLevel(String loggingLevel) {
        LoggingLevel level = LoggingLevel.valueOf(loggingLevel);
        getPolicy().setLoggingLevel(level);
        getPolicy().getLogger().setLevel(level);
    }

}
