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
import org.apache.camel.api.management.mbean.ManagedWireTapMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.WireTapProcessor;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.URISupport;

@ManagedResource(description = "Managed WireTapProcessor")
public class ManagedWireTapProcessor extends ManagedProcessor implements ManagedWireTapMBean {

    private String uri;
    private boolean sanitize;

    public ManagedWireTapProcessor(CamelContext context, WireTapProcessor processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        sanitize = strategy.getManagementAgent().getMask() != null ? strategy.getManagementAgent().getMask() : true;
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
    public String getDestination() {
        return uri;
    }

    @Override
    public WireTapProcessor getProcessor() {
        return (WireTapProcessor) super.getProcessor();
    }

    @Override
    public String getUri() {
        return uri;
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
    public Boolean isCopy() {
        return getProcessor().isCopy();
    }

    @Override
    public Boolean isDynamicUri() {
        return getProcessor().isDynamicUri();
    }

    @Override
    public Integer getTaskSize() {
        return getProcessor().getPendingExchangesSize();
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
