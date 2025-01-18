/*
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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.Expression;
import org.apache.camel.ExpressionFactory;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;

/**
 * To use a predicate to determine when to trigger this.
 */
@Metadata(label = "configuration")
@AsPredicate
@XmlRootElement(name = "onWhen")
@XmlAccessorType(XmlAccessType.FIELD)
public class OnWhenDefinition extends OptionalIdentifiedDefinition<OnWhenDefinition>
        implements HasExpressionType, CopyableDefinition<OnWhenDefinition> {

    @XmlElementRef
    private ExpressionDefinition expression;

    public OnWhenDefinition() {
    }

    protected OnWhenDefinition(OnWhenDefinition source) {
        super(source);
        this.expression = source.expression != null ? source.expression.copyDefinition() : null;
    }

    public OnWhenDefinition(Predicate predicate) {
        if (predicate != null) {
            setExpression(ExpressionNodeHelper.toExpressionDefinition(predicate));
        }
    }

    public OnWhenDefinition(ExpressionDefinition expression) {
        // favour using the helper to set the expression as it can unwrap some
        // unwanted builders when using Java DSL
        this.expression = expression;
    }

    @Override
    public OnWhenDefinition copyDefinition() {
        return new OnWhenDefinition(this);
    }

    @Override
    public String toString() {
        return "OnWhen[" + description() + "]";
    }

    protected String description() {
        StringBuilder sb = new StringBuilder(256);
        if (getExpression() != null) {
            String language = getExpression().getLanguage();
            if (language != null) {
                sb.append(language).append("{");
            }
            sb.append(getExpression().getLabel());
            if (language != null) {
                sb.append("}");
            }
        }
        return sb.toString();
    }

    @Override
    public String getShortName() {
        return "onWhen";
    }

    @Override
    public String getLabel() {
        return "onWhen[" + description() + "]";
    }

    public ExpressionDefinition getExpression() {
        return expression;
    }

    public void setExpression(ExpressionDefinition expression) {
        // favour using the helper to set the expression as it can unwrap some
        // unwanted builders when using Java DSL
        this.expression = expression;
    }

    @Override
    public ExpressionDefinition getExpressionType() {
        return getExpression();
    }

    @Override
    public void setExpressionType(ExpressionDefinition expressionType) {
        setExpression(expressionType);
    }

    public void preCreateProcessor() {
        Expression exp = getExpression();
        if (getExpression() != null && getExpression().getExpressionValue() != null) {
            exp = getExpression().getExpressionValue();
        }

        if (exp instanceof ExpressionClause clause) {
            if (clause.getExpressionType() != null) {
                // if using the Java DSL then the expression may have been set
                // using the
                // ExpressionClause which is a fancy builder to define
                // expressions and predicates
                // using fluent builders in the DSL. However we need afterwards
                // a callback to
                // reset the expression to the expression type the
                // ExpressionClause did build for us
                ExpressionFactory model = clause.getExpressionType();
                if (model instanceof ExpressionDefinition expressionDefinition) {
                    setExpression(expressionDefinition);
                }
            }
        }

        if (getExpression() != null && getExpression().getExpression() == null) {
            // use toString from predicate or expression so we have some
            // information to show in the route model
            if (getExpression().getPredicate() != null) {
                getExpression().setExpression(getExpression().getPredicate().toString());
            } else if (getExpression().getExpressionValue() != null) {
                getExpression().setExpression(getExpression().getExpressionValue().toString());
            }
        }
    }

}
