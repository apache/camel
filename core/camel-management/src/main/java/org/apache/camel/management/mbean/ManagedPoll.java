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

import javax.management.openmbean.TabularData;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedPollMBean;
import org.apache.camel.model.PollDefinition;
import org.apache.camel.processor.PollProcessor;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.URISupport;

@ManagedResource(description = "Managed Poll")
public class ManagedPoll extends ManagedProcessor implements ManagedPollMBean {

    private String uri;
    private boolean sanitize;

    public ManagedPoll(CamelContext context, PollProcessor processor, PollDefinition definition) {
        super(context, processor, definition);
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        sanitize = strategy.getManagementAgent().getMask() != null ? strategy.getManagementAgent().getMask() : true;
        uri = getProcessor().getUri();
        if (sanitize) {
            uri = URISupport.sanitizeUri(uri);
        }
    }

    @Override
    public PollProcessor getProcessor() {
        return (PollProcessor) super.getProcessor();
    }

    @Override
    public void reset() {
        super.reset();
        if (getProcessor().getEndpointUtilizationStatistics() != null) {
            getProcessor().getEndpointUtilizationStatistics().clear();
        }
    }

    @Override
    public Boolean getSupportExtendedInformation() {
        return true;
    }

    @Override
    public PollDefinition getDefinition() {
        return (PollDefinition) super.getDefinition();
    }

    @Override
    public String getDestination() {
        return uri;
    }

    @Override
    public String getVariableReceive() {
        return getProcessor().getVariableReceive();
    }

    @Override
    public Long getTimeout() {
        return getProcessor().getTimeout();
    }

    @Override
    public TabularData extendedInformation() {
        try {
            return EndpointUtilizationHelper.toTabularData(getProcessor().getEndpointUtilizationStatistics(), sanitize);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

}
