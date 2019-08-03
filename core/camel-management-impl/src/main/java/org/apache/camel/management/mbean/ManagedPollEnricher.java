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

import java.util.Map;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedPollEnricherMBean;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.processor.PollEnricher;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.URISupport;

@ManagedResource(description = "Managed PollEnricher")
public class ManagedPollEnricher extends ManagedProcessor implements ManagedPollEnricherMBean {
    private final PollEnricher processor;
    private String uri;
    private boolean sanitize;

    public ManagedPollEnricher(CamelContext context, PollEnricher processor, PollEnrichDefinition definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        sanitize = strategy.getManagementAgent().getMask() != null ? strategy.getManagementAgent().getMask() : false;
        uri = getDefinition().getExpression().getExpression();
        if (sanitize) {
            uri = URISupport.sanitizeUri(uri);
        }
    }

    @Override
    public void reset() {
        super.reset();
        if (processor.getEndpointUtilizationStatistics() != null) {
            processor.getEndpointUtilizationStatistics().clear();
        }
    }

    @Override
    public PollEnrichDefinition getDefinition() {
        return (PollEnrichDefinition) super.getDefinition();
    }

    @Override
    public PollEnricher getProcessor() {
        return processor;
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
    public Long getTimeout() {
        return processor.getTimeout();
    }

    @Override
    public Integer getCacheSize() {
        return processor.getCacheSize();
    }

    @Override
    public Boolean isIgnoreInvalidEndpoint() {
        return processor.isIgnoreInvalidEndpoint();
    }

    @Override
    public Boolean isAggregateOnException() {
        return processor.isAggregateOnException();
    }

    @Override
    public TabularData extendedInformation() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.endpointsUtilizationTabularType());

            EndpointUtilizationStatistics stats = processor.getEndpointUtilizationStatistics();
            if (stats != null) {
                for (Map.Entry<String, Long> entry : stats.getStatistics().entrySet()) {
                    CompositeType ct = CamelOpenMBeanTypes.endpointsUtilizationCompositeType();
                    String url = entry.getKey();
                    if (sanitize) {
                        url = URISupport.sanitizeUri(url);
                    }

                    Long hits = entry.getValue();
                    if (hits == null) {
                        hits = 0L;
                    }

                    CompositeData data = new CompositeDataSupport(ct, new String[]{"url", "hits"}, new Object[]{url, hits});
                    answer.put(data);
                }
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

}
