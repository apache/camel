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
import org.apache.camel.Endpoint;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedThroughputLoggerMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.support.processor.ThroughputLogger;
import org.apache.camel.util.URISupport;

@ManagedResource(description = "Managed ThroughputLogger")
public class ManagedThroughputLogger extends ManagedProcessor implements ManagedThroughputLoggerMBean {
    private final Endpoint endpoint;
    private String destination;

    public ManagedThroughputLogger(CamelContext context, ThroughputLogger logger, ProcessorDefinition<?> definition) {
        this(context, logger, definition, null);
    }

    public ManagedThroughputLogger(CamelContext context, ThroughputLogger logger, ProcessorDefinition<?> definition,
                                   Endpoint endpoint) {
        super(context, logger, definition);
        this.endpoint = endpoint;
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        if (endpoint != null) {
            boolean sanitize = strategy.getManagementAgent().getMask() != null ? strategy.getManagementAgent().getMask() : true;
            if (sanitize) {
                destination = URISupport.sanitizeUri(endpoint.getEndpointUri());
            } else {
                destination = endpoint.getEndpointUri();
            }
        }
    }

    @Override
    public ThroughputLogger getProcessor() {
        return (ThroughputLogger) super.getProcessor();
    }

    @Override
    public String getDestination() {
        return destination;
    }

    @Override
    public void reset() {
        super.reset();
        getProcessor().reset();
    }

    @Override
    public long getReceivedCounter() {
        return getProcessor().getReceivedCounter();
    }

    @Override
    public double getAverage() {
        return getProcessor().getAverage();
    }

    @Override
    public double getRate() {
        return getProcessor().getRate();
    }

    @Override
    public String getLastLogMessage() {
        return getProcessor().getLastLogMessage();
    }

    @Override
    public void resetThroughputLogger() {
        getProcessor().reset();
    }
}
