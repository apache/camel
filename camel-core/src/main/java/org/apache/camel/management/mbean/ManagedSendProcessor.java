/**
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
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedSendProcessorMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.URISupport;

/**
 * @version 
 */
@ManagedResource(description = "Managed SendProcessor")
public class ManagedSendProcessor extends ManagedProcessor implements ManagedSendProcessorMBean {
    private final SendProcessor processor;
    private String destination;

    public ManagedSendProcessor(CamelContext context, SendProcessor processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        boolean sanitize = strategy.getManagementAgent().getSanitize() != null ? strategy.getManagementAgent().getSanitize() : false;
        if (sanitize) {
            destination = URISupport.sanitizeUri(processor.getDestination().getEndpointUri());
        } else {
            destination = processor.getDestination().getEndpointUri();
        }
    }

    public SendProcessor getProcessor() {
        return processor;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String uri) {
        // noop
    }

    public String getMessageExchangePattern() {
        if (processor.getPattern() != null) {
            return processor.getPattern().name();
        } else {
            return null;
        }
    }

}
