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

import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.processor.WireTapProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;

/**
 * Routes a copy of a message (or creates a new message) to a secondary destination while continue routing the original message.
 */
@Metadata(label = "eip,endpoint,routing")
@XmlRootElement(name = "wireTap")
@XmlAccessorType(XmlAccessType.FIELD)
public class WireTapDefinition<Type extends ProcessorDefinition<Type>> extends ToDynamicDefinition implements ExecutorServiceAwareDefinition<WireTapDefinition<Type>> {
    @XmlTransient
    private Processor newExchangeProcessor;
    @XmlAttribute(name = "processorRef")
    private String newExchangeProcessorRef;
    @XmlElement(name = "body")
    private ExpressionSubElementDefinition newExchangeExpression;
    @XmlElementRef
    private List<SetHeaderDefinition> headers = new ArrayList<>();
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean copy;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean dynamicUri;
    @XmlAttribute
    private String onPrepareRef;
    @XmlTransient
    private Processor onPrepare;

    public WireTapDefinition() {
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        // executor service is mandatory for wire tap
        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, this, true);
        ExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredExecutorService(routeContext, "WireTap", this, true);

        // must use InOnly for WireTap
        setPattern(ExchangePattern.InOnly);

        // create the send dynamic producer to send to the wire tapped endpoint
        SendDynamicProcessor dynamicTo = (SendDynamicProcessor) super.createProcessor(routeContext);

        // create error handler we need to use for processing the wire tapped
        Processor target = wrapInErrorHandler(routeContext, dynamicTo);

        // and wrap in unit of work
        CamelInternalProcessor internal = new CamelInternalProcessor(target);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(routeContext));

        // is true bt default
        boolean isCopy = getCopy() == null || getCopy();

        WireTapProcessor answer = new WireTapProcessor(dynamicTo, internal, getPattern(), threadPool, shutdownThreadPool, isDynamic());
        answer.setCopy(isCopy);
        if (newExchangeProcessorRef != null) {
            newExchangeProcessor = routeContext.mandatoryLookup(newExchangeProcessorRef, Processor.class);
        }
        if (newExchangeProcessor != null) {
            answer.addNewExchangeProcessor(newExchangeProcessor);
        }
        if (newExchangeExpression != null) {
            answer.setNewExchangeExpression(newExchangeExpression.createExpression(routeContext));
        }
        if (headers != null && !headers.isEmpty()) {
            for (SetHeaderDefinition header : headers) {
                Processor processor = createProcessor(routeContext, header);
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

    @Override
    protected Expression createExpression(RouteContext routeContext) {
        // whether to use dynamic or static uri
        if (isDynamic()) {
            return super.createExpression(routeContext);
        } else {
            return ExpressionBuilder.constantExpression(getUri());
        }
    }

    private boolean isDynamic() {
        // its dynamic by default
        return dynamicUri == null || dynamicUri;
    }

    public ExchangePattern getPattern() {
        return ExchangePattern.InOnly;
    }

    @Override
    public String toString() {
        return "WireTap[" + getUri() + "]";
    }
    
    @Override
    public String getLabel() {
        return "wireTap[" + getUri() + "]";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Type end() {
        // allow end() to return to previous type so you can continue in the DSL
        return (Type) super.end();
    }

    @Override
    public void addOutput(ProcessorDefinition<?> output) {
        // add outputs on parent as this wiretap does not support outputs
        getParent().addOutput(output);
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
     * Uses a copy of the original exchange
     *
     * @param copy if it is true camel will copy the original exchange,
     *             if it is false camel will not copy the original exchange 
     * @return the builder
     */
    public WireTapDefinition<Type> copy(boolean copy) {
        setCopy(copy);
        return this;
    }

    /**
     * Whether the uri is dynamic or static.
     * If the uri is dynamic then the simple language is used to evaluate a dynamic uri to use as the wire-tap destination,
     * for each incoming message. This works similar to how the <tt>toD</tt> EIP pattern works.
     * If static then the uri is used as-is as the wire-tap destination.
     *
     * @param dynamicUri  whether to use dynamic or static uris
     * @return the builder
     */
    public WireTapDefinition<Type> dynamicUri(boolean dynamicUri) {
        setDynamicUri(dynamicUri);
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
        setNewExchangeExpression(new ExpressionSubElementDefinition(expression));
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

    /**
     * Sets the maximum size used by the {@link org.apache.camel.impl.ProducerCache} which is used
     * to cache and reuse producers, when uris are reused.
     *
     * @param cacheSize  the cache size, use <tt>0</tt> for default cache size, or <tt>-1</tt> to turn cache off.
     * @return the builder
     */
    @Override
    public WireTapDefinition<Type> cacheSize(int cacheSize) {
        setCacheSize(cacheSize);
        return this;
    }

    /**
     * Ignore the invalidate endpoint exception when try to create a producer with that endpoint
     *
     * @return the builder
     */
    @Override
    public WireTapDefinition<Type> ignoreInvalidEndpoint() {
        setIgnoreInvalidEndpoint(true);
        return this;
    }

    // Properties
    //-------------------------------------------------------------------------

    @Override
    public String getUri() {
        return super.getUri();
    }

    /**
     * The uri of the endpoint to wiretap to. The uri can be dynamic computed using the {@link org.apache.camel.language.simple.SimpleLanguage} expression.
     */
    @Override
    public void setUri(String uri) {
        super.setUri(uri);
    }

    public Processor getNewExchangeProcessor() {
        return newExchangeProcessor;
    }

    /**
     * To use a Processor for creating a new body as the message to use for wire tapping
     */
    public void setNewExchangeProcessor(Processor processor) {
        this.newExchangeProcessor = processor;
    }

    public String getNewExchangeProcessorRef() {
        return newExchangeProcessorRef;
    }

    /**
     * Reference to a Processor to use for creating a new body as the message to use for wire tapping
     */
    public void setNewExchangeProcessorRef(String ref) {
        this.newExchangeProcessorRef = ref;
    }

    public ExpressionSubElementDefinition getNewExchangeExpression() {
        return newExchangeExpression;
    }

    /**
     * Uses the expression for creating a new body as the message to use for wire tapping
     */
    public void setNewExchangeExpression(ExpressionSubElementDefinition newExchangeExpression) {
        this.newExchangeExpression = newExchangeExpression;
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

    public Boolean getDynamicUri() {
        return dynamicUri;
    }

    public void setDynamicUri(Boolean dynamicUri) {
        this.dynamicUri = dynamicUri;
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
