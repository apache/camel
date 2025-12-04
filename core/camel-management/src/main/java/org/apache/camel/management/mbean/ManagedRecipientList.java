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
import org.apache.camel.api.management.mbean.ManagedRecipientListMBean;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.URISupport;

@ManagedResource(description = "Managed RecipientList")
public class ManagedRecipientList extends ManagedProcessor implements ManagedRecipientListMBean {

    private String uri;
    private boolean sanitize;

    public ManagedRecipientList(CamelContext context, RecipientList processor, RecipientListDefinition<?> definition) {
        super(context, processor, definition);
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        sanitize = strategy.getManagementAgent().getMask() != null
                ? strategy.getManagementAgent().getMask()
                : true;
        uri = getDefinition().getExpression().getExpression();
        if (sanitize) {
            uri = URISupport.sanitizeUri(uri);
        }
    }

    @Override
    public RecipientList getProcessor() {
        return (RecipientList) super.getProcessor();
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
    public RecipientListDefinition<?> getDefinition() {
        return (RecipientListDefinition<?>) super.getDefinition();
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
    public String getUriDelimiter() {
        return getProcessor().getDelimiter();
    }

    @Override
    public Integer getCacheSize() {
        return getProcessor().getCacheSize();
    }

    @Override
    public Boolean isParallelAggregate() {
        return getProcessor().isParallelAggregate();
    }

    @Override
    public Boolean isParallelProcessing() {
        return getProcessor().isParallelProcessing();
    }

    @Override
    public Boolean isStreaming() {
        return getProcessor().isStreaming();
    }

    @Override
    public Boolean isStopOnException() {
        return getProcessor().isStopOnException();
    }

    @Override
    public Boolean isShareUnitOfWork() {
        return getProcessor().isShareUnitOfWork();
    }

    @Override
    public Long getTimeout() {
        return getProcessor().getTimeout();
    }

    @Override
    public TabularData extendedInformation() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.endpointsUtilizationTabularType());

            EndpointUtilizationStatistics stats = getProcessor().getEndpointUtilizationStatistics();
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

                    CompositeData data =
                            new CompositeDataSupport(ct, new String[] {"url", "hits"}, new Object[] {url, hits});
                    answer.put(data);
                }
            }
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
