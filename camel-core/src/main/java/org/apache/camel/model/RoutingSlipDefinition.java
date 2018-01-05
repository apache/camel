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

import java.util.Collections;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.processor.RoutingSlip;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

import static org.apache.camel.builder.ExpressionBuilder.headerExpression;

/**
 * Routes a message through a series of steps that are pre-determined (the slip)
 */
@Metadata(label = "eip,endpoint,routing")
@XmlRootElement(name = "routingSlip")
@XmlAccessorType(XmlAccessType.FIELD)
public class RoutingSlipDefinition<Type extends ProcessorDefinition<Type>> extends NoOutputExpressionNode {
    public static final String DEFAULT_DELIMITER = ",";

    @XmlAttribute @Metadata(defaultValue = ",")
    private String uriDelimiter;
    @XmlAttribute
    private Boolean ignoreInvalidEndpoints;
    @XmlAttribute
    private Integer cacheSize;

    public RoutingSlipDefinition() {
        this((String)null, DEFAULT_DELIMITER);
    }

    public RoutingSlipDefinition(String headerName) {
        this(headerName, DEFAULT_DELIMITER);
    }

    public RoutingSlipDefinition(String headerName, String uriDelimiter) {
        super(new HeaderExpression(headerName));
        setUriDelimiter(uriDelimiter);
    }
    
    public RoutingSlipDefinition(Expression expression, String uriDelimiter) {
        super(expression);
        setUriDelimiter(uriDelimiter);
    }
    
    public RoutingSlipDefinition(Expression expression) {
        this(expression, DEFAULT_DELIMITER);
    }

    @Override
    public String toString() {
        return "RoutingSlip[" + getExpression() + "]";
    }

    @Override
    public String getLabel() {
        return "routingSlip[" + getExpression() + "]";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Expression expression = getExpression().createExpression(routeContext);
        String delimiter = getUriDelimiter() != null ? getUriDelimiter() : DEFAULT_DELIMITER;

        RoutingSlip routingSlip = new RoutingSlip(routeContext.getCamelContext(), expression, delimiter);
        if (getIgnoreInvalidEndpoints() != null) {
            routingSlip.setIgnoreInvalidEndpoints(getIgnoreInvalidEndpoints());
        }
        if (getCacheSize() != null) {
            routingSlip.setCacheSize(getCacheSize());
        }

        // and wrap this in an error handler
        ErrorHandlerFactory builder = routeContext.getRoute().getErrorHandlerBuilder();
        // create error handler (create error handler directly to keep it light weight,
        // instead of using ProcessorDefinition.wrapInErrorHandler)
        AsyncProcessor errorHandler = (AsyncProcessor) builder.createErrorHandler(routeContext, routingSlip.newRoutingSlipProcessorForErrorHandler());
        routingSlip.setErrorHandler(errorHandler);

        return routingSlip;
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return Collections.emptyList();
    }

    /**
     * Expression to define the routing slip, which defines which endpoints to route the message in a pipeline style.
     * Notice the expression is evaluated once, if you want a more dynamic style, then the dynamic router eip is a better choice.
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    public void setUriDelimiter(String uriDelimiter) {
        this.uriDelimiter = uriDelimiter;
    }

    public String getUriDelimiter() {
        return uriDelimiter;
    }
    
    public void setIgnoreInvalidEndpoints(Boolean ignoreInvalidEndpoints) {
        this.ignoreInvalidEndpoints = ignoreInvalidEndpoints;
    }
    
    public Boolean getIgnoreInvalidEndpoints() {
        return ignoreInvalidEndpoints;
    }

    public Integer getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(Integer cacheSize) {
        this.cacheSize = cacheSize;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public Type end() {
        // allow end() to return to previous type so you can continue in the DSL
        return (Type) super.end();
    }
    
    /**
     * Ignore the invalidate endpoint exception when try to create a producer with that endpoint
     *
     * @return the builder
     */
    public RoutingSlipDefinition<Type> ignoreInvalidEndpoints() {
        setIgnoreInvalidEndpoints(true);
        return this;
    }

    /**
     * Sets the uri delimiter to use
     *
     * @param uriDelimiter the delimiter
     * @return the builder
     */
    public RoutingSlipDefinition<Type> uriDelimiter(String uriDelimiter) {
        setUriDelimiter(uriDelimiter);
        return this;
    }

    /**
     * Sets the maximum size used by the {@link org.apache.camel.impl.ProducerCache} which is used
     * to cache and reuse producers when using this routing slip, when uris are reused.
     *
     * @param cacheSize  the cache size, use <tt>0</tt> for default cache size, or <tt>-1</tt> to turn cache off.
     * @return the builder
     */
    public RoutingSlipDefinition<Type> cacheSize(int cacheSize) {
        setCacheSize(cacheSize);
        return this;
    }

}
