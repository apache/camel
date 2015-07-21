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
import org.apache.camel.api.management.mbean.ManagedSendDynamicProcessorMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.URISupport;

/**
 * @version 
 */
@ManagedResource(description = "Managed SendDynamicProcessor")
public class ManagedSendDynamicProcessor extends ManagedProcessor implements ManagedSendDynamicProcessorMBean {
    private final SendDynamicProcessor processor;
    private String uri;

    public ManagedSendDynamicProcessor(CamelContext context, SendDynamicProcessor processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        boolean sanitize = strategy.getManagementAgent().getMask() != null ? strategy.getManagementAgent().getMask() : false;
        if (sanitize) {
            uri = URISupport.sanitizeUri(processor.getUri());
        } else {
            uri = processor.getUri();
        }
    }

    public SendDynamicProcessor getProcessor() {
        return processor;
    }

    public String getUri() {
        return uri;
    }

    public String getMessageExchangePattern() {
        if (processor.getPattern() != null) {
            return processor.getPattern().name();
        } else {
            return null;
        }
    }

    public Integer getCacheSize() {
        return processor.getCacheSize();
    }

    public Boolean isIgnoreInvalidEndpoint() {
        return processor.isIgnoreInvalidEndpoint();
    }

}
