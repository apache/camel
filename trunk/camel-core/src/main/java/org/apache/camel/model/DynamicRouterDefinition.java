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

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.processor.DynamicRouter;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;dynamicRouter/&gt; element
 */
@XmlRootElement(name = "dynamicRouter")
@XmlAccessorType(XmlAccessType.FIELD)
public class DynamicRouterDefinition<Type extends ProcessorDefinition> extends NoOutputExpressionNode {

    public static final String DEFAULT_DELIMITER = ",";

    @XmlAttribute
    private String uriDelimiter;
    @XmlAttribute
    private Boolean ignoreInvalidEndpoints;

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
    public String getShortName() {
        return "dynamicRouter";
    }

    @Override
    public List<ProcessorDefinition> getOutputs() {
        return Collections.emptyList();
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Expression expression = getExpression().createExpression(routeContext);
        String delimiter = getUriDelimiter() != null ? getUriDelimiter() : DEFAULT_DELIMITER;

        DynamicRouter dynamicRouter = new DynamicRouter(routeContext.getCamelContext(), expression, delimiter);
        if (getIgnoreInvalidEndpoint() != null) {
            dynamicRouter.setIgnoreInvalidEndpoints(getIgnoreInvalidEndpoint());
        }
        return dynamicRouter;
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

    public Boolean getIgnoreInvalidEndpoint() {
        return ignoreInvalidEndpoints;
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

}
