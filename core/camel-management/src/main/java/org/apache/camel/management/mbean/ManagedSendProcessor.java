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

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.CamelOpenMBeanTypes;
import org.apache.camel.api.management.mbean.ManagedSendProcessorMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.URISupport;

@ManagedResource(description = "Managed SendProcessor")
public class ManagedSendProcessor extends ManagedProcessor implements ManagedSendProcessorMBean {
    private String destination;

    public ManagedSendProcessor(CamelContext context, SendProcessor processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        boolean sanitize = strategy.getManagementAgent().getMask() != null
                ? strategy.getManagementAgent().getMask()
                : true;
        if (sanitize) {
            destination = URISupport.sanitizeUri(getProcessor().getDestination().getEndpointUri());
        } else {
            destination = getProcessor().getDestination().getEndpointUri();
        }
    }

    @Override
    public Boolean getSupportExtendedInformation() {
        return true;
    }

    @Override
    public void reset() {
        super.reset();
        getProcessor().reset();
    }

    @Override
    public SendProcessor getProcessor() {
        return (SendProcessor) super.getProcessor();
    }

    @Override
    public String getDestination() {
        return destination;
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
    public TabularData extendedInformation() {
        try {
            TabularData answer = new TabularDataSupport(CamelOpenMBeanTypes.endpointsUtilizationTabularType());

            // we only have 1 endpoint
            CompositeType ct = CamelOpenMBeanTypes.endpointsUtilizationCompositeType();
            String url = getDestination();
            long hits = getProcessor().getCounter();

            CompositeData data = new CompositeDataSupport(ct, new String[] {"url", "hits"}, new Object[] {url, hits});
            answer.put(data);
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
