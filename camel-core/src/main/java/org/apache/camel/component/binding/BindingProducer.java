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
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ServiceHelper;

/**
 * A {@link Producer} which applies a {@link org.apache.camel.spi.Binding} before invoking the underlying {@link Producer} on the {@link Endpoint}
 */
public class BindingProducer extends DefaultProducer {
    private final BindingEndpoint endpoint;
    private final Processor bindingProcessor;
    private final Producer delegateProducer;

    public BindingProducer(BindingEndpoint endpoint) throws Exception {
        super(endpoint);
        this.endpoint = endpoint;
        this.bindingProcessor = endpoint.getBinding().createProduceProcessor();
        this.delegateProducer = endpoint.getDelegate().createProducer();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        endpoint.pipelineBindingProcessor(bindingProcessor, exchange, delegateProducer);
    }

    @Override
    protected void doStart() throws Exception {
        // inject CamelContext
        if (bindingProcessor instanceof CamelContextAware) {
            ((CamelContextAware) bindingProcessor).setCamelContext(getEndpoint().getCamelContext());
        }
        if (delegateProducer instanceof CamelContextAware) {
            ((CamelContextAware) delegateProducer).setCamelContext(getEndpoint().getCamelContext());
        }
        ServiceHelper.startServices(bindingProcessor, delegateProducer);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(delegateProducer, bindingProcessor);
        super.doStop();
    }
}
