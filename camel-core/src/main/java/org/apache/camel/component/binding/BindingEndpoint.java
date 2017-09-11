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
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.processor.PipelineHelper;
import org.apache.camel.spi.Binding;
import org.apache.camel.spi.HasBinding;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ServiceHelper;

import static org.apache.camel.util.CamelContextHelper.getMandatoryEndpoint;

/**
 * The binding component is used for as a of wrapping an Endpoint in a contract with a data format.
 *
 * In Camel terms a binding is a way of wrapping an Endpoint in a contract; such as a Data Format,
 * a Content Enricher or validation step. Bindings are completely optional and you can choose to use
 * them on any camel endpoint.
 * Bindings are inspired by the work of SwitchYard project adding service contracts to various technologies
 * like Camel and many others. But rather than the SwitchYard approach of wrapping Camel in SCA,
 * Camel Bindings provide a way of wrapping Camel endpoints with contracts inside the Camel framework itself;
 * so you can use them easily inside any Camel route.
 *
 * Applies a {@link org.apache.camel.spi.Binding} to an underlying {@link Endpoint} so that the binding processes messages
 * before its sent to the endpoint and processes messages received by the endpoint consumer before its passed
 * to the real consumer.
 *
 * @deprecated use {@link org.apache.camel.spi.Contract} instead
 */
@Deprecated @Metadata(deprecationNode = "Use org.apache.camel.spi.Contract instead")
@UriEndpoint(firstVersion = "2.11.0", scheme = "binding", title = "Binding", syntax = "binding:bindingName:delegateUri",
    consumerClass = BindingConsumerProcessor.class, label = "core,transformation")
public class BindingEndpoint extends DefaultEndpoint implements HasBinding {

    @UriPath @Metadata(required = "true")
    private final String bindingName;
    @UriPath @Metadata(required = "true")
    private final String delegateUri;
    private Binding binding;
    private Endpoint delegate;

    @Deprecated
    public BindingEndpoint(String uri, Component component, Binding binding, Endpoint delegate) {
        super(uri, component);
        this.binding = binding;
        this.delegate = delegate;
        this.bindingName = null;
        this.delegateUri = null;
    }

    public BindingEndpoint(String uri, Component component, String bindingName, String delegateUri) {
        super(uri, component);
        this.bindingName = bindingName;
        this.delegateUri = delegateUri;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new BindingProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Processor bindingProcessor = new BindingConsumerProcessor(this, processor);
        return delegate.createConsumer(bindingProcessor);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public Binding getBinding() {
        return binding;
    }

    public Endpoint getDelegate() {
        return delegate;
    }

    /**
     * Name of the binding to lookup in the Camel registry.
     */
    public String getBindingName() {
        return bindingName;
    }

    /**
     * Uri of the delegate endpoint.
     */
    public String getDelegateUri() {
        return delegateUri;
    }

    /**
     * Applies the {@link Binding} processor to the given exchange before passing it on to the delegateProcessor (either a producer or consumer)
     */
    public void pipelineBindingProcessor(Processor bindingProcessor, Exchange exchange, Processor delegateProcessor) throws Exception {
        bindingProcessor.process(exchange);

        Exchange delegateExchange = PipelineHelper.createNextExchange(exchange);
        delegateProcessor.process(delegateExchange);
    }

    @Override
    protected void doStart() throws Exception {
        if (binding == null) {
            binding = CamelContextHelper.mandatoryLookup(getCamelContext(), bindingName, Binding.class);
        }
        if (delegate == null) {
            delegate = getMandatoryEndpoint(getCamelContext(), delegateUri);
        }

        // inject CamelContext
        if (binding instanceof CamelContextAware) {
            ((CamelContextAware) binding).setCamelContext(getCamelContext());
        }
        ServiceHelper.startServices(delegate, binding);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(delegate, binding);
        super.doStop();
    }
}
