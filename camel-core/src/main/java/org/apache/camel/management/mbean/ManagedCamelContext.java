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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.spi.ManagementStrategy;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * @version $Revision$
 */
@ManagedResource(description = "Managed CamelContext")
public class ManagedCamelContext {

    private CamelContext context;

    public ManagedCamelContext(CamelContext context) {
        this.context = context;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    public CamelContext getContext() {
        return context;
    }

    @ManagedAttribute(description = "Camel id")
    public String getCamelId() {
        return context.getName();
    }

    @ManagedAttribute(description = "Camel Version")
    public String getCamelVersion() {
        return context.getVersion();
    }

    @ManagedAttribute(description = "Camel State")
    public String getState() {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        ServiceStatus status = (context).getStatus();
        // if no status exists then its stopped
        if (status == null) {
            status = ServiceStatus.Stopped;
        }
        return status.name();
    }

    @ManagedAttribute(description = "Is Camel suspended")
    public Boolean getSuspended() {
        return context.isSuspended();
    }

    @ManagedAttribute(description = "Uptime")
    public String getUptime() {
        return context.getUptime();
    }

    @ManagedAttribute(description = "Camel Properties")
    public Map<String, String> getProperties() {
        if (context.getProperties().isEmpty()) {
            return null;
        }
        return context.getProperties();
    }

    @ManagedAttribute(description = "Tracing")
    public Boolean getTracing() {
        return context.isTracing();
    }

    @ManagedAttribute(description = "Tracing")
    public void setTracing(Boolean tracing) {
        context.setTracing(tracing);
    }

    @ManagedAttribute(description = "Current number of inflight Exchanges")
    public Integer getInflightExchanges() {
        return context.getInflightRepository().size();
    }

    @ManagedAttribute(description = "Shutdown timeout")
    public void setTimeout(long timeout) {
        context.getShutdownStrategy().setTimeout(timeout);
    }

    @ManagedAttribute(description = "Shutdown timeout")
    public long getTimeout() {
        return context.getShutdownStrategy().getTimeout();
    }

    @ManagedAttribute(description = "Shutdown timeout time unit")
    public void setTimeUnit(TimeUnit timeUnit) {
        context.getShutdownStrategy().setTimeUnit(timeUnit);
    }

    @ManagedAttribute(description = "Shutdown timeout time unit")
    public TimeUnit getTimeUnit() {
        return context.getShutdownStrategy().getTimeUnit();
    }

    @ManagedAttribute(description = "Whether to force shutdown now when a timeout occurred")
    public void setShutdownNowOnTimeout(boolean shutdownNowOnTimeout) {
        context.getShutdownStrategy().setShutdownNowOnTimeout(shutdownNowOnTimeout);
    }

    @ManagedAttribute(description = "Whether to force shutdown now when a timeout occurred")
    public boolean isShutdownNowOnTimeout() {
        return context.getShutdownStrategy().isShutdownNowOnTimeout();
    }

    @ManagedOperation(description = "Start Camel")
    public void start() throws Exception {
        context.start();
    }

    @ManagedOperation(description = "Stop Camel")
    public void stop() throws Exception {
        context.stop();
    }

    @ManagedOperation(description = "Suspend Camel")
    public void suspend() throws Exception {
        context.suspend();
    }

    @ManagedOperation(description = "Resume Camel")
    public void resume() throws Exception {
        context.resume();
    }

    @ManagedOperation(description = "Send body (in only)")
    public void sendBody(String endpointUri, String body) throws Exception {
        ProducerTemplate template = context.createProducerTemplate();
        try {
            template.sendBody(endpointUri, body);
        } finally {
            template.stop();
        }
    }

    @ManagedOperation(description = "Request body (in out)")
    public Object requestBody(String endpointUri, String body) throws Exception {
        ProducerTemplate template = context.createProducerTemplate();
        Object answer = null;
        try {
            answer = template.requestBody(endpointUri, body);
        } finally {
            template.stop();
        }
        return answer;
    }

}
