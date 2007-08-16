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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.spi.Language;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.CollectionStringBuffer;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.List;

/**
 * A useful base class for an expression
 *
 * @version $Revision: 1.1 $
 */
@XmlType(name = "expressionType")
@XmlAccessorType(XmlAccessType.FIELD)
public class ExpressionType {
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

    public static String getLabel(List<ExpressionType> expressions) {
        CollectionStringBuffer buffer = new CollectionStringBuffer();
        for (ExpressionType expression : expressions) {
            buffer.append(expression.getLabel());
        }
        return buffer.toString();
    }

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

    @Override
    public String toString() {
        return getLanguage() + "Expression[" + getExpression() + "]";
    }

    public String getLanguage() {
        return "";
    }

    public Predicate<Exchange> createPredicate(RouteContext route) {
        if (predicate == null) {
            CamelContext camelContext = route.getCamelContext();
            Language language = camelContext.resolveLanguage(getLanguage());
            predicate = language.createPredicate(getExpression());
        }
        return predicate;
    }

    public Expression createExpression(RouteContext routeContext) {
        if (expressionValue == null) {
            CamelContext camelContext = routeContext.getCamelContext();
            Language language = camelContext.resolveLanguage(getLanguage());
            expressionValue = language.createExpression(getExpression());
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
     *
     * @return possible object is
     *         {@link String }
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     *
     * @param value allowed object is
     *              {@link String }
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
        }
        else {
            return language;
        }
        return "";
    }
}
