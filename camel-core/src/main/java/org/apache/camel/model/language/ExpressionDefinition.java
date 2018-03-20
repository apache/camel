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
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyAttribute;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.namespace.QName;

import org.apache.camel.AfterPropertiesConfigured;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.Predicate;
import org.apache.camel.model.OtherAttributesAware;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.ExpressionToPredicateAdapter;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;

/**
 * A useful base class for an expression
 */
@Metadata(label = "language", title = "Expression")
@XmlRootElement
@XmlType(name = "expression") // must be named expression
@XmlAccessorType(XmlAccessType.FIELD)
public class ExpressionDefinition implements Expression, Predicate, OtherAttributesAware {
    @XmlAttribute
    @XmlID
    private String id;
    @XmlValue @Metadata(required = "true")
    private String expression;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean trim;
    @XmlTransient
    private Predicate predicate;
    @XmlTransient
    private Expression expressionValue;
    @XmlTransient
    private ExpressionDefinition expressionType;
    // use xs:any to support optional property placeholders
    @XmlAnyAttribute
    private Map<QName, Object> otherAttributes;

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
                if (language == null) {
                    throw new NoSuchLanguageException(getLanguage());
                }
                String exp = getExpression();
                // should be true by default
                boolean isTrim = getTrim() == null || getTrim();
                // trim if configured to trim
                if (exp != null && isTrim) {
                    exp = exp.trim();
                }
                // resolve the expression as it may be an external script from the classpath/file etc
                exp = ResourceHelper.resolveOptionalExternalScript(camelContext, exp);

                predicate = language.createPredicate(exp);
                configurePredicate(camelContext, predicate);
            }
        }
        // inject CamelContext if its aware
        if (predicate instanceof CamelContextAware) {
            ((CamelContextAware) predicate).setCamelContext(camelContext);
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
                if (language == null) {
                    throw new NoSuchLanguageException(getLanguage());
                }
                String exp = getExpression();
                // should be true by default
                boolean isTrim = getTrim() == null || getTrim();
                // trim if configured to trim
                if (exp != null && isTrim) {
                    exp = exp.trim();
                }
                // resolve the expression as it may be an external script from the classpath/file etc
                exp = ResourceHelper.resolveOptionalExternalScript(camelContext, exp);

                setExpressionValue(language.createExpression(exp));
                configureExpression(camelContext, getExpressionValue());
            }
        }
        // inject CamelContext if its aware
        if (getExpressionValue() instanceof CamelContextAware) {
            ((CamelContextAware) getExpressionValue()).setCamelContext(camelContext);
        }
        return getExpressionValue();
    }

    public String getExpression() {
        return expression;
    }

    /**
     * The expression value in your chosen language syntax
     */
    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getId() {
        return id;
    }

    /**
     * Sets the id of this node
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

    /**
     * Whether to trim the value to remove leading and trailing whitespaces and line breaks
     */
    public void setTrim(Boolean trim) {
        this.trim = trim;
    }

    @Override
    public Map<QName, Object> getOtherAttributes() {
        return otherAttributes;
    }

    @Override
    public void setOtherAttributes(Map<QName, Object> otherAttributes) {
        this.otherAttributes = otherAttributes;
    }

    /**
     * Returns some descriptive text to describe this node
     */
    public String getLabel() {
        Predicate predicate = getPredicate();
        if (predicate != null) {
            return predicate.toString();
        }
        Expression expressionValue = getExpressionValue();
        if (expressionValue != null) {
            return expressionValue.toString();
        }

        String exp = getExpression();
        return exp != null ? exp : "";
    }

    /**
     * Allows derived classes to set a lazily created expressionType instance
     * such as if using the {@link org.apache.camel.builder.ExpressionClause}
     */
    protected void setExpressionType(ExpressionDefinition expressionType) {
        this.expressionType = expressionType;
    }

    protected void configurePredicate(CamelContext camelContext, Predicate predicate) {
        // allows to perform additional logic after the properties has been configured which may be needed
        // in the various camel components outside camel-core
        if (predicate instanceof AfterPropertiesConfigured) {
            ((AfterPropertiesConfigured) predicate).afterPropertiesConfigured(camelContext);
        }
    }

    protected void configureExpression(CamelContext camelContext, Expression expression) {
        // allows to perform additional logic after the properties has been configured which may be needed
        // in the various camel components outside camel-core
        if (expression instanceof AfterPropertiesConfigured) {
            ((AfterPropertiesConfigured) expression).afterPropertiesConfigured(camelContext);
        }
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
