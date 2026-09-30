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
import org.apache.camel.api.management.mbean.ManagedEnricherMBean;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.processor.Enricher;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.URISupport;

@ManagedResource(description = "Managed Enricher")
public class ManagedEnricher extends ManagedProcessor implements ManagedEnricherMBean {

    private String uri;
    private boolean sanitize;

    public ManagedEnricher(CamelContext context, Enricher processor, EnrichDefinition definition) {
        super(context, processor, definition);
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        sanitize = strategy.getManagementAgent().getMask() != null ? strategy.getManagementAgent().getMask() : true;
        uri = getDefinition().getExpression().getExpression();
        if (sanitize) {
            uri = URISupport.sanitizeUri(uri);
        }
    }

    @Override
    public Enricher getProcessor() {
        return (Enricher) super.getProcessor();
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
    public EnrichDefinition getDefinition() {
        return (EnrichDefinition) super.getDefinition();
    }

    @Override
    public String getDestination() {
        return uri;
    }

    @Override
    public String getExpressionLanguage() {
        return getDefinition().getExpression().getLanguage();
    }

    @Override
    public String getExpression() {
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
    public Integer getCacheSize() {
        return getProcessor().getCacheSize();
    }

    @Override
    public Boolean isIgnoreInvalidEndpoint() {
        return getProcessor().isIgnoreInvalidEndpoint();
    }

    @Override
    public Boolean isShareUnitOfWork() {
        return getProcessor().isShareUnitOfWork();
    }

    @Override
    public Boolean isAggregateOnException() {
        return getProcessor().isAggregateOnException();
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
