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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.language.ExpressionDefinition;

/**
 * A base class for nodes which contain an expression and a number of outputs
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlTransient
public abstract class ExpressionNode extends ProcessorDefinition<ExpressionNode> {
    @XmlElementRef
    private ExpressionDefinition expression;
    @XmlElementRef
    private List<ProcessorDefinition<?>> outputs = new ArrayList<>();

    public ExpressionNode() {
    }

    public ExpressionNode(ExpressionDefinition expression) {
        this.expression = expression;
    }

    public ExpressionNode(Expression expression) {
        if (expression != null) {
            setExpression(ExpressionNodeHelper.toExpressionDefinition(expression));
        }
    }

    public ExpressionNode(Predicate predicate) {
        if (predicate != null) {
            setExpression(ExpressionNodeHelper.toExpressionDefinition(predicate));
        }
    }

    public ExpressionDefinition getExpression() {
        return expression;
    }

    public void setExpression(ExpressionDefinition expression) {
        // favour using the helper to set the expression as it can unwrap some unwanted builders when using Java DSL
        this.expression = expression;
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        this.outputs = outputs;
    }

    @Override
    public boolean isOutputSupported() {
        return true;
    }

    @Override
    public String getLabel() {
        if (getExpression() == null) {
            return "";
        }
        return getExpression().getLabel();
    }

    @Override
    public void configureChild(ProcessorDefinition<?> output) {
        // reuse the logic from pre create processor
        preCreateProcessor();
    }

    @Override
    public void preCreateProcessor() {
        Expression exp = getExpression();
        if (getExpression() != null && getExpression().getExpressionValue() != null) {
            exp = getExpression().getExpressionValue();
        }

        if (exp instanceof ExpressionClause) {
            ExpressionClause<?> clause = (ExpressionClause<?>) exp;
            if (clause.getExpressionType() != null) {
                // if using the Java DSL then the expression may have been set using the
                // ExpressionClause which is a fancy builder to define expressions and predicates
                // using fluent builders in the DSL. However we need afterwards a callback to
                // reset the expression to the expression type the ExpressionClause did build for us
                setExpression(clause.getExpressionType());
            }
        }

        if (getExpression() != null && getExpression().getExpression() == null) {
            // use toString from predicate or expression so we have some information to show in the route model
            if (getExpression().getPredicate() != null) {
                getExpression().setExpression(getExpression().getPredicate().toString());
            } else if (getExpression().getExpressionValue() != null) {
                getExpression().setExpression(getExpression().getExpressionValue().toString());
            }
        }
    }

}
