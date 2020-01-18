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
    private final SendProcessor processor;
    private String destination;

    public ManagedSendProcessor(CamelContext context, SendProcessor processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        boolean sanitize = strategy.getManagementAgent().getMask() != null ? strategy.getManagementAgent().getMask() : false;
        if (sanitize) {
            destination = URISupport.sanitizeUri(processor.getDestination().getEndpointUri());
        } else {
            destination = processor.getDestination().getEndpointUri();
        }
    }

    @Override
    public Boolean getSupportExtendedInformation() {
        return true;
    }

    @Override
    public void reset() {
        super.reset();
        processor.reset();
    }

    @Override
    public SendProcessor getProcessor() {
        return processor;
    }

    @Override
    public String getDestination() {
        return destination;
    }

    @Override
    public String getMessageExchangePattern() {
        if (processor.getPattern() != null) {
            return processor.getPattern().name();
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
            long hits = processor.getCounter();

            CompositeData data = new CompositeDataSupport(ct, new String[]{"url", "hits"}, new Object[]{url, hits});
            answer.put(data);
            return answer;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
