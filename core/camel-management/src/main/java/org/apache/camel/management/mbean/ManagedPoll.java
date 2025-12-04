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
import org.apache.camel.api.management.mbean.ManagedPollMBean;
import org.apache.camel.model.PollDefinition;
import org.apache.camel.processor.PollProcessor;
import org.apache.camel.spi.EndpointUtilizationStatistics;
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
        sanitize = strategy.getManagementAgent().getMask() != null
                ? strategy.getManagementAgent().getMask()
                : true;
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
