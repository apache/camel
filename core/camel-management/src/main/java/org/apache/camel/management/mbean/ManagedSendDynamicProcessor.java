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
import org.apache.camel.api.management.mbean.ManagedSendDynamicProcessorMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.URISupport;

@ManagedResource(description = "Managed SendDynamicProcessor")
public class ManagedSendDynamicProcessor extends ManagedProcessor implements ManagedSendDynamicProcessorMBean {
    private String uri;
    private boolean sanitize;

    public ManagedSendDynamicProcessor(CamelContext context, SendDynamicProcessor processor,
                                       ProcessorDefinition<?> definition) {
        super(context, processor, definition);
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        this.sanitize = strategy.getManagementAgent().getMask() != null ? strategy.getManagementAgent().getMask() : true;
        if (sanitize) {
            uri = URISupport.sanitizeUri(getProcessor().getUri());
        } else {
            uri = getProcessor().getUri();
        }
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
    public SendDynamicProcessor getProcessor() {
        return (SendDynamicProcessor) super.getProcessor();
    }

    @Override
    public String getDestination() {
        return uri;
    }

    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public String getVariableSend() {
        return getProcessor().getVariableSend();
    }

    @Override
    public String getVariableReceive() {
        return getProcessor().getVariableReceive();
    }

    @Override
    public String getMessageExchangePattern() {
        if (getProcessor().getPattern() != null) {
            return getProcessor().getPattern().name();
        } else {
            return null;
        }
    }

    @Override
    public Integer getCacheSize() {
        return getProcessor().getCacheSize();
    }

    @Override
    public Boolean isIgnoreInvalidEndpoint() {
        return getProcessor().isIgnoreInvalidEndpoint();
    }

    @Override
    public Boolean isAllowOptimisedComponents() {
        return getProcessor().isAllowOptimisedComponents();
    }

    @Override
    public Boolean isOptimised() {
        return getProcessor().getDynamicAware() != null;
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
