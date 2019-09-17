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
import org.apache.camel.api.management.mbean.ManagedThrottlingExceptionRoutePolicyMBean;
import org.apache.camel.throttling.ThrottlingExceptionHalfOpenHandler;
import org.apache.camel.throttling.ThrottlingExceptionRoutePolicy;

@ManagedResource(description = "Managed ThrottlingExceptionRoutePolicy")
public class ManagedThrottlingExceptionRoutePolicy extends ManagedService implements ManagedThrottlingExceptionRoutePolicyMBean {
    
    private final ThrottlingExceptionRoutePolicy policy;

    public ManagedThrottlingExceptionRoutePolicy(CamelContext context, ThrottlingExceptionRoutePolicy policy) {
        super(context, policy);
        this.policy = policy;
    }

    public ThrottlingExceptionRoutePolicy getPolicy() {
        return policy;
    }
    
    @Override
    public Long getHalfOpenAfter() {
        return getPolicy().getHalfOpenAfter();
    }

    @Override
    public void setHalfOpenAfter(Long milliseconds) {
        getPolicy().setHalfOpenAfter(milliseconds);
    }

    @Override
    public Long getFailureWindow() {
        return getPolicy().getFailureWindow();
    }

    @Override
    public void setFailureWindow(Long milliseconds) {
        getPolicy().setFailureWindow(milliseconds);
    }

    @Override
    public Integer getFailureThreshold() {
        return getPolicy().getFailureThreshold();
    }

    @Override
    public void setFailureThreshold(Integer numberOfFailures) {
        getPolicy().setFailureThreshold(numberOfFailures);
    }
    
    @Override
    public String currentState() {
        return getPolicy().dumpState();
    }

    @Override
    public String getHalfOpenHandlerName() {
        ThrottlingExceptionHalfOpenHandler obj = getPolicy().getHalfOpenHandler();
        if (obj != null) {
            return obj.getClass().getSimpleName();
        } else {
            return "";
        }
    }

    @Override
    public Integer getCurrentFailures() {
        return getPolicy().getFailures();
    }

    @Override
    public Long getLastFailure() {
        if (getPolicy().getLastFailure() == 0) {
            return 0L;
        } else {
            return System.currentTimeMillis() - getPolicy().getLastFailure();
        }
    }

    @Override
    public Long getOpenAt() {
        if (getPolicy().getOpenedAt() == 0) {
            return 0L;
        } else {
            return System.currentTimeMillis() - getPolicy().getOpenedAt();
        }
    }

}
