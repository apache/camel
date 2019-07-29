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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.BacklogTracerEventMessage;
import org.apache.camel.api.management.mbean.ManagedBacklogTracerMBean;
import org.apache.camel.api.management.mbean.ManagedTracerMBean;
import org.apache.camel.processor.interceptor.BacklogTracer;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.Tracer;

@ManagedResource(description = "Managed Tracer")
public class ManagedTracer implements ManagedTracerMBean {
    private final CamelContext camelContext;
    private final Tracer tracer;

    public ManagedTracer(CamelContext camelContext, Tracer tracer) {
        this.camelContext = camelContext;
        this.tracer = tracer;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return camelContext;
    }

    public Tracer getTracer() {
        return tracer;
    }

    public boolean getEnabled() {
        return tracer.isEnabled();
    }

    public String getCamelId() {
        return camelContext.getName();
    }

    public String getCamelManagementName() {
        return camelContext.getManagementName();
    }

    public void setEnabled(boolean enabled) {
        tracer.setEnabled(enabled);
    }

    public boolean isEnabled() {
        return tracer.isEnabled();
    }

    public void setTracePattern(String pattern) {
        tracer.setTracePattern(pattern);
    }

    public String getTracePattern() {
        return tracer.getTracePattern();
    }

    public long getTraceCounter() {
        return tracer.getTraceCounter();
    }

    public void resetTraceCounter() {
        tracer.resetTraceCounter();
    }

}
