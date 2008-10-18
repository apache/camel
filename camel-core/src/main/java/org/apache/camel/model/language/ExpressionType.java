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
package org.apache.camel.model.language;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.impl.DefaultRouteContext;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A useful base class for an expression
 *
 * @version $Revision$
 */
@XmlRootElement
@XmlType(name = "expressionType")
@XmlAccessorType(XmlAccessType.FIELD)
public class ExpressionType implements Expression<Exchange>, Predicate<Exchange> {
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    private String id;
    @XmlValue
    private String expression;
    @XmlTransient
    private Predicate predicate;
    @XmlTransient
    private Expression expressionValue;
    @XmlTransient
    private ExpressionType expressionType;

    public ExpressionType() {
    }

    public ExpressionType(String expression) {
        this.expression = expression;
    }

    public ExpressionType(Predicate predicate) {
        this.predicate = predicate;
    }

    public ExpressionType(Expression expression) {
        this.expressionValue = expression;
    }

    public static String getLabel(List<ExpressionType> expressions) {
        CollectionStringBuffer buffer = new CollectionStringBuffer();
        for (ExpressionType expression : expressions) {
            buffer.append(expression.getLabel());
        }
        return buffer.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (getLanguage() != null) {
            sb.append(getLanguage() + "{");
        }
        if (getExpression() != null) {
            sb.append(getExpression());
        }
        if (getPredicate() != null) {
            sb.append(getPredicate().toString());
        }
        if (getExpressionValue() != null) {
            sb.append(getExpressionValue().toString());
        }
        if (getLanguage() != null) {
            sb.append("}");
        }
        return sb.toString();
    }

    public Object evaluate(Exchange exchange) {
        if (expressionValue == null) {
            RouteContext routeContext = new DefaultRouteContext(exchange.getContext());
            expressionValue = createExpression(routeContext);
        }
        ObjectHelper.notNull(expressionValue, "expressionValue");
        return expressionValue.evaluate(exchange);
    }

    public void assertMatches(String text, Exchange exchange) throws AssertionError {
        if (!matches(exchange)) {
            throw new AssertionError(text + getExpression() + " for exchange: " + exchange);
        }
    }

    public boolean matches(Exchange exchange) {
        if (predicate == null) {
            RouteContext routeContext = new DefaultRouteContext(exchange.getContext());
            predicate = createPredicate(routeContext);
        }
        ObjectHelper.notNull(predicate, "predicate");
        return predicate.matches(exchange);
    }

    public String getLanguage() {
        return "";
    }

    public Predicate<Exchange> createPredicate(RouteContext routeContext) {
        if (predicate == null) {
            if (expressionType != null) {
                predicate = expressionType.createPredicate(routeContext);
            } else {
                CamelContext camelContext = routeContext.getCamelContext();
                Language language = camelContext.resolveLanguage(getLanguage());
                predicate = language.createPredicate(getExpression());
                configurePredicate(routeContext, predicate);
            }
        }
        return predicate;
    }

    public Expression createExpression(RouteContext routeContext) {
        if (expressionValue == null) {
            if (expressionType != null) {
                expressionValue = expressionType.createExpression(routeContext);
            } else {
                CamelContext camelContext = routeContext.getCamelContext();
                Language language = camelContext.resolveLanguage(getLanguage());
                expressionValue = language.createExpression(getExpression());
                configureExpression(routeContext, expressionValue);
            }
        }
        return expressionValue;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    /**
     * Gets the value of the id property.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     */
    public void setId(String value) {
        this.id = value;
    }

    public Predicate getPredicate() {
        return predicate;
    }

    public Expression getExpressionValue() {
        return expressionValue;
    }

    protected void setExpressionValue(Expression expressionValue) {
        this.expressionValue = expressionValue;
    }

    /**
     * Returns some descriptive text to describe this node
     */
    public String getLabel() {
        String language = getExpression();
        if (ObjectHelper.isNullOrBlank(language)) {
            Predicate predicate = getPredicate();
            if (predicate != null) {
                return predicate.toString();
            }
            Expression expressionValue = getExpressionValue();
            if (expressionValue != null) {
                return expressionValue.toString();
            }
        } else {
            return language;
        }
        return "";
    }

    /**
     * Allows derived classes to set a lazily created expressionType instance
     * such as if using the {@link ExpressionClause}
     */
    protected void setExpressionType(ExpressionType expressionType) {
        this.expressionType = expressionType;
    }

    protected void configurePredicate(RouteContext routeContext, Predicate predicate) {
    }

    protected void configureExpression(RouteContext routeContext, Expression expression) {
    }

    /**
     * Sets a named property on the object instance using introspection
     */
    protected void setProperty(Object bean, String name, Object value) {
        try {
            IntrospectionSupport.setProperty(bean, name, value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to set property " + name + " on " + bean
                                               + ". Reason: " + e, e);
        }
    }
}
