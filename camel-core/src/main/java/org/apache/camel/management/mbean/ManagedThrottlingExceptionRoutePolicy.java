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
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedThrottlingExceptionRoutePolicyMBean;
import org.apache.camel.impl.ThrottlingExceptionHalfOpenHandler;
import org.apache.camel.impl.ThrottlingExceptionRoutePolicy;

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
    public long getHalfOpenAfter() {
        return getPolicy().getHalfOpenAfter();
    }

    @Override
    public void setHalfOpenAfter(long milliseconds) {
        getPolicy().setHalfOpenAfter(milliseconds);
    }

    @Override
    public long getFailureWindow() {
        return getPolicy().getFailureWindow();
    }

    @Override
    public void setFailureWindow(long milliseconds) {
        getPolicy().setFailureWindow(milliseconds);
    }

    @Override
    public int getFailureThreshold() {
        return getPolicy().getFailureThreshold();
    }

    @Override
    public void setFailureThreshold(int numberOfFailures) {
        getPolicy().setFailureThreshold(numberOfFailures);
    }
    
    @Override
    public String currentState() {
        return getPolicy().dumpState();
    }

    @Override
    public String hasHalfOpenHandler() {
        ThrottlingExceptionHalfOpenHandler obj = getPolicy().getHalfOpenHandler();
        if (obj != null) {
            return obj.getClass().getSimpleName();
        } else {
            return "";
        }
    }

    @Override
    public int currentFailures() {
        return getPolicy().getFailures();
    }

    @Override
    public long getLastFailure() {
        return System.currentTimeMillis() - getPolicy().getLastFailure();
    }

    @Override
    public long getOpenAt() {
        return System.currentTimeMillis() - getPolicy().getOpenedAt();
    }

}
