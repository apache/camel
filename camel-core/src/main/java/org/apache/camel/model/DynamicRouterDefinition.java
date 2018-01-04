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
import org.apache.camel.processor.DynamicRouter;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;

import static org.apache.camel.builder.ExpressionBuilder.headerExpression;

/**
 * Routes messages based on dynamic rules
 */
@Metadata(label = "eip,endpoint,routing")
@XmlRootElement(name = "dynamicRouter")
@XmlAccessorType(XmlAccessType.FIELD)
public class DynamicRouterDefinition<Type extends ProcessorDefinition<Type>> extends NoOutputExpressionNode {

    public static final String DEFAULT_DELIMITER = ",";

    @XmlAttribute @Metadata(defaultValue = ",")
    private String uriDelimiter;
    @XmlAttribute
    private Boolean ignoreInvalidEndpoints;
    @XmlAttribute
    private Integer cacheSize; 

    public DynamicRouterDefinition() {
    }

    public DynamicRouterDefinition(Expression expression) {
        super(expression);
    }

    @Override
    public String toString() {
        return "DynamicRouter[" + getExpression() + "]";
    }
    
    @Override
    public String getLabel() {
        return "dynamicRouter[" + getExpression() + "]";
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return Collections.emptyList();
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Expression expression = getExpression().createExpression(routeContext);
        String delimiter = getUriDelimiter() != null ? getUriDelimiter() : DEFAULT_DELIMITER;

        DynamicRouter dynamicRouter = new DynamicRouter(routeContext.getCamelContext(), expression, delimiter);
        if (getIgnoreInvalidEndpoints() != null) {
            dynamicRouter.setIgnoreInvalidEndpoints(getIgnoreInvalidEndpoints());
        }
        if (getCacheSize() != null) {
            dynamicRouter.setCacheSize(getCacheSize());
        }

        // and wrap this in an error handler
        ErrorHandlerFactory builder = routeContext.getRoute().getErrorHandlerBuilder();
        // create error handler (create error handler directly to keep it light weight,
        // instead of using ProcessorDefinition.wrapInErrorHandler)
        AsyncProcessor errorHandler = (AsyncProcessor) builder.createErrorHandler(routeContext, dynamicRouter.newRoutingSlipProcessorForErrorHandler());
        dynamicRouter.setErrorHandler(errorHandler);

        return dynamicRouter;
    }

    /**
     * Expression to call that returns the endpoint(s) to route to in the dynamic routing.
     * <p/>
     * <b>Important:</b> The expression will be called in a while loop fashion, until the expression returns <tt>null</tt>
     * which means the dynamic router is finished.
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

    // Fluent API
    // -------------------------------------------------------------------------

    public Integer getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(Integer cacheSize) {
        this.cacheSize = cacheSize;
    }

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
    public DynamicRouterDefinition<Type> ignoreInvalidEndpoints() {
        setIgnoreInvalidEndpoints(true);
        return this;
    }

    /**
     * Sets the uri delimiter to use
     *
     * @param uriDelimiter the delimiter
     * @return the builder
     */
    public DynamicRouterDefinition<Type> uriDelimiter(String uriDelimiter) {
        setUriDelimiter(uriDelimiter);
        return this;
    }
    
    /**
     * Sets the maximum size used by the {@link org.apache.camel.impl.ProducerCache} which is used
     * to cache and reuse producers when using this dynamic router, when uris are reused.
     *
     * @param cacheSize  the cache size, use <tt>0</tt> for default cache size, or <tt>-1</tt> to turn cache off.
     * @return the builder
     */
    public DynamicRouterDefinition<Type> cacheSize(int cacheSize) {
        setCacheSize(cacheSize);
        return this;
    }

}
