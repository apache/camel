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
package org.apache.camel.component.binding;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ServiceHelper;

/**
 * Applies a {@link org.apache.camel.spi.Binding} to a consumer
 */
public class BindingConsumerProcessor extends ServiceSupport implements Processor {
    private final BindingEndpoint endpoint;
    private final Processor delegateProcessor;
    private final Processor bindingProcessor;

    public BindingConsumerProcessor(BindingEndpoint endpoint, Processor delegateProcessor) {
        this.endpoint = endpoint;
        this.delegateProcessor = delegateProcessor;
        this.bindingProcessor = endpoint.getBinding().createConsumeProcessor();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        endpoint.pipelineBindingProcessor(bindingProcessor, exchange, delegateProcessor);
    }

    @Override
    protected void doStart() throws Exception {
        // inject CamelContext
        if (bindingProcessor instanceof CamelContextAware) {
            ((CamelContextAware) bindingProcessor).setCamelContext(endpoint.getCamelContext());
        }
        if (delegateProcessor instanceof CamelContextAware) {
            ((CamelContextAware) delegateProcessor).setCamelContext(endpoint.getCamelContext());
        }
        ServiceHelper.startServices(bindingProcessor, delegateProcessor);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(delegateProcessor, bindingProcessor);
    }
}
