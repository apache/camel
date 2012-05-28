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
import org.apache.camel.spi.Language;
import org.apache.camel.spi.Required;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.ExpressionToPredicateAdapter;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A useful base class for an expression
 *
 * @version 
 */
@XmlRootElement
@XmlType(name = "expression")
@XmlAccessorType(XmlAccessType.FIELD)
public class ExpressionDefinition implements Expression, Predicate {
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    private String id;
    @XmlValue
    private String expression;
    @XmlAttribute
    private Boolean trim;
    @XmlTransient
    private Predicate predicate;
    @XmlTransient
    private Expression expressionValue;
    @XmlTransient
    private ExpressionDefinition expressionType;

    public ExpressionDefinition() {
    }

    public ExpressionDefinition(String expression) {
        this.expression = expression;
    }

    public ExpressionDefinition(Predicate predicate) {
        this.predicate = predicate;
    }

    public ExpressionDefinition(Expression expression) {
        this.expressionValue = expression;
    }

    public static String getLabel(List<ExpressionDefinition> expressions) {
        CollectionStringBuffer buffer = new CollectionStringBuffer();
        for (ExpressionDefinition expression : expressions) {
            buffer.append(expression.getLabel());
        }
        return buffer.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (getLanguage() != null) {
            sb.append(getLanguage()).append("{");
        }
        if (getPredicate() != null) {
            sb.append(getPredicate().toString());
        }
        if (getExpressionValue() != null) {
            sb.append(getExpressionValue().toString());
        }
        if (getPredicate() == null && getExpressionValue() == null && getExpression() != null) {
            sb.append(getExpression());
        }
        if (getLanguage() != null) {
            sb.append("}");
        }
        return sb.toString();
    }

    public Object evaluate(Exchange exchange) {
        return evaluate(exchange, Object.class);
    }

    public <T> T evaluate(Exchange exchange, Class<T> type) {
        if (expressionValue == null) {
            expressionValue = createExpression(exchange.getContext());
        }
        ObjectHelper.notNull(expressionValue, "expressionValue");
        return expressionValue.evaluate(exchange, type);
    }

    public void assertMatches(String text, Exchange exchange) throws AssertionError {
        if (!matches(exchange)) {
            throw new AssertionError(text + getExpression() + " for exchange: " + exchange);
        }
    }

    public boolean matches(Exchange exchange) {
        if (predicate == null) {
            predicate = createPredicate(exchange.getContext());
        }
        ObjectHelper.notNull(predicate, "predicate");
        return predicate.matches(exchange);
    }

    public String getLanguage() {
        return "";
    }

    public final Predicate createPredicate(RouteContext routeContext) {
        return createPredicate(routeContext.getCamelContext());
    }

    public Predicate createPredicate(CamelContext camelContext) {
        if (predicate == null) {
            if (getExpressionType() != null) {
                predicate = getExpressionType().createPredicate(camelContext);
            } else if (getExpressionValue() != null) {
                predicate = new ExpressionToPredicateAdapter(getExpressionValue());
            } else if (getExpression() != null) {
                ObjectHelper.notNull("language", getLanguage());
                Language language = camelContext.resolveLanguage(getLanguage());
                String exp = getExpression();
                // trim if configured to trim
                if (exp != null && isTrim()) {
                    exp = exp.trim();
                }
                predicate = language.createPredicate(exp);
                configurePredicate(camelContext, predicate);
            }
        }
        return predicate;
    }

    public final Expression createExpression(RouteContext routeContext) {
        return createExpression(routeContext.getCamelContext());
    }

    public Expression createExpression(CamelContext camelContext) {
        if (getExpressionValue() == null) {
            if (getExpressionType() != null) {
                setExpressionValue(getExpressionType().createExpression(camelContext));
            } else if (getExpression() != null) {
                ObjectHelper.notNull("language", getLanguage());
                Language language = camelContext.resolveLanguage(getLanguage());
                String exp = getExpression();
                // trim if configured to trim
                if (exp != null && isTrim()) {
                    exp = exp.trim();
                }
                setExpressionValue(language.createExpression(exp));
                configureExpression(camelContext, getExpressionValue());
            }
        }
        return getExpressionValue();
    }

    public String getExpression() {
        return expression;
    }

    @Required
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

    public ExpressionDefinition getExpressionType() {
        return expressionType;
    }

    public Boolean getTrim() {
        return trim;
    }

    public void setTrim(Boolean trim) {
        this.trim = trim;
    }

    public boolean isTrim() {
        // trim by default
        return trim == null || trim;
    }

    /**
     * Returns some descriptive text to describe this node
     */
    public String getLabel() {
        String language = getExpression();
        if (ObjectHelper.isEmpty(language)) {
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
     * such as if using the {@link org.apache.camel.builder.ExpressionClause}
     */
    protected void setExpressionType(ExpressionDefinition expressionType) {
        this.expressionType = expressionType;
    }

    protected void configurePredicate(CamelContext camelContext, Predicate predicate) {
    }

    protected void configureExpression(CamelContext camelContext, Expression expression) {
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
