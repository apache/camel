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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.processor.WireTapProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;

/**
 * Represents an XML &lt;wireTap/&gt; element
 */
@XmlRootElement(name = "wireTap")
@XmlAccessorType(XmlAccessType.FIELD)
public class WireTapDefinition<Type extends ProcessorDefinition> extends NoOutputDefinition implements ExecutorServiceAwareDefinition<ProcessorDefinition> {
    @XmlAttribute
    protected String uri;
    @XmlAttribute
    protected String ref;
    @XmlTransient
    protected Endpoint endpoint;
    @XmlTransient
    private Processor newExchangeProcessor;
    @XmlAttribute(name = "processorRef")
    private String newExchangeProcessorRef;
    @XmlElement(name = "body")
    private ExpressionSubElementDefinition newExchangeExpression;
    @XmlElementRef
    private List<SetHeaderDefinition> headers = new ArrayList<SetHeaderDefinition>();
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    private Boolean copy;
    @XmlAttribute
    private String onPrepareRef;
    @XmlTransient
    private Processor onPrepare;

    public WireTapDefinition() {
    }

    public WireTapDefinition(String uri) {
        setUri(uri);
    }

    public WireTapDefinition(Endpoint endpoint) {
        setEndpoint(endpoint);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Endpoint endpoint = resolveEndpoint(routeContext);

        executorService = ExecutorServiceHelper.getConfiguredExecutorService(routeContext, "WireTap", this);
        if (executorService == null) {
            executorService = routeContext.getCamelContext().getExecutorServiceStrategy().newDefaultThreadPool(this, "WireTap");
        }
        WireTapProcessor answer = new WireTapProcessor(endpoint, getPattern(), executorService);

        answer.setCopy(isCopy());
        if (newExchangeProcessorRef != null) {
            newExchangeProcessor = routeContext.lookup(newExchangeProcessorRef, Processor.class);
        }
        if (newExchangeProcessor != null) {
            answer.addNewExchangeProcessor(newExchangeProcessor);
        }
        if (newExchangeExpression != null) {
            answer.setNewExchangeExpression(newExchangeExpression.createExpression(routeContext));
        }
        if (headers != null && !headers.isEmpty()) {
            for (SetHeaderDefinition header : headers) {
                Processor processor = header.createProcessor(routeContext);
                answer.addNewExchangeProcessor(processor);
            }
        }
        if (onPrepareRef != null) {
            onPrepare = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), onPrepareRef, Processor.class);
        }
        if (onPrepare != null) {
            answer.setOnPrepare(onPrepare);
        }

        return answer;
    }

    public ExchangePattern getPattern() {
        return ExchangePattern.InOnly;
    }

    @Override
    public String toString() {
        return "WireTap[" + getLabel() + "]";
    }

    @Override
    public String getShortName() {
        return "wireTap";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Type end() {
        // allow end() to return to previous type so you can continue in the DSL
        return (Type) super.end();
    }

    @Override
    public void addOutput(ProcessorDefinition output) {
        // add outputs on parent as this wiretap does not support outputs
        getParent().addOutput(output);
    }

    public Endpoint resolveEndpoint(RouteContext context) {
        if (endpoint == null) {
            return context.resolveEndpoint(getUri(), getRef());
        } else {
            return endpoint;
        }
    }

    @Override
    public String getLabel() {
        return FromDefinition.description(getUri(), getRef(), getEndpoint());
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Uses a custom thread pool
     *
     * @param executorService a custom {@link ExecutorService} to use as thread pool
     *                        for sending tapped exchanges
     * @return the builder
     */
    public WireTapDefinition<Type> executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Uses a custom thread pool
     *
     * @param executorServiceRef reference to lookup a custom {@link ExecutorService}
     *                           to use as thread pool for sending tapped exchanges
     * @return the builder
     */
    public WireTapDefinition<Type> executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    /**
     * Uses a copy of the original exchange
     *
     * @return the builder
     */
    public WireTapDefinition<Type> copy() {
        setCopy(true);
        return this;
    }

    /**
     * @deprecated will be removed in Camel 3.0 Instead use {@link #newExchangeBody(org.apache.camel.Expression)}
     */
    @Deprecated
    public WireTapDefinition<Type> newExchange(Expression expression) {
        return newExchangeBody(expression);
    }

    /**
     * Sends a <i>new</i> Exchange, instead of tapping an existing, using {@link ExchangePattern#InOnly}
     *
     * @param expression expression that creates the new body to send
     * @return the builder
     * @see #newExchangeHeader(String, org.apache.camel.Expression)
     */
    public WireTapDefinition<Type> newExchangeBody(Expression expression) {
        setNewExchangeExpression(expression);
        return this;
    }

    /**
     * Sends a <i>new</i> Exchange, instead of tapping an existing, using {@link ExchangePattern#InOnly}
     *
     * @param ref reference to the {@link Processor} to lookup in the {@link org.apache.camel.spi.Registry} to
     *            be used for preparing the new exchange to send
     * @return the builder
     */
    public WireTapDefinition<Type> newExchangeRef(String ref) {
        setNewExchangeProcessorRef(ref);
        return this;
    }

    /**
     * Sends a <i>new</i> Exchange, instead of tapping an existing, using {@link ExchangePattern#InOnly}
     *
     * @param processor  processor preparing the new exchange to send
     * @return the builder
     * @see #newExchangeHeader(String, org.apache.camel.Expression)
     */
    public WireTapDefinition<Type> newExchange(Processor processor) {
        setNewExchangeProcessor(processor);
        return this;
    }

    /**
     * Sets a header on the <i>new</i> Exchange, instead of tapping an existing, using {@link ExchangePattern#InOnly}.
     * <p/>
     * Use this together with the {@link #newExchange(org.apache.camel.Expression)} or {@link #newExchange(org.apache.camel.Processor)}
     * methods.
     *
     * @param headerName  the header name
     * @param expression  the expression setting the header value
     * @return the builder
     */
    public WireTapDefinition<Type> newExchangeHeader(String headerName, Expression expression) {
        headers.add(new SetHeaderDefinition(headerName, expression));
        return this;
    }

    /**
     * Uses the {@link Processor} when preparing the {@link org.apache.camel.Exchange} to be send.
     * This can be used to deep-clone messages that should be send, or any custom logic needed before
     * the exchange is send.
     *
     * @param onPrepare the processor
     * @return the builder
     */
    public WireTapDefinition<Type> onPrepare(Processor onPrepare) {
        setOnPrepare(onPrepare);
        return this;
    }

    /**
     * Uses the {@link Processor} when preparing the {@link org.apache.camel.Exchange} to be send.
     * This can be used to deep-clone messages that should be send, or any custom logic needed before
     * the exchange is send.
     *
     * @param onPrepareRef reference to the processor to lookup in the {@link org.apache.camel.spi.Registry}
     * @return the builder
     */
    public WireTapDefinition<Type> onPrepareRef(String onPrepareRef) {
        setOnPrepareRef(onPrepareRef);
        return this;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public Processor getNewExchangeProcessor() {
        return newExchangeProcessor;
    }

    public void setNewExchangeProcessor(Processor processor) {
        this.newExchangeProcessor = processor;
    }

    public String getNewExchangeProcessorRef() {
        return newExchangeProcessorRef;
    }

    public void setNewExchangeProcessorRef(String ref) {
        this.newExchangeProcessorRef = ref;
    }

    public ExpressionSubElementDefinition getNewExchangeExpression() {
        return newExchangeExpression;
    }

    public void setNewExchangeExpression(ExpressionSubElementDefinition expression) {
        this.newExchangeExpression = expression;
    }

    public void setNewExchangeExpression(Expression expression) {
        this.newExchangeExpression = new ExpressionSubElementDefinition(expression);
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }

    public Boolean getCopy() {
        return copy;
    }

    public void setCopy(Boolean copy) {
        this.copy = copy;
    }

    public boolean isCopy() {
        // should default to true if not configured
        return copy != null ? copy : true;
    }

    public String getOnPrepareRef() {
        return onPrepareRef;
    }

    public void setOnPrepareRef(String onPrepareRef) {
        this.onPrepareRef = onPrepareRef;
    }

    public Processor getOnPrepare() {
        return onPrepare;
    }

    public void setOnPrepare(Processor onPrepare) {
        this.onPrepare = onPrepare;
    }

    public List<SetHeaderDefinition> getHeaders() {
        return headers;
    }

    public void setHeaders(List<SetHeaderDefinition> headers) {
        this.headers = headers;
    }
}
