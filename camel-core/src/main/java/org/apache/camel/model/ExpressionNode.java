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
import javax.xml.bind.annotation.XmlElementRef;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.spi.RouteContext;

/**
 * A base class for nodes which contain an expression
 * <p/>
 * This node is to be extended by definitions which need to support an expression but the definition should not
 * contain any outputs, such as {@link org.apache.camel.model.TransformDefinition}.
 *
 * @version 
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ExpressionNode extends ProcessorDefinition<OutputExpressionNode> {

    @XmlElementRef
    protected ExpressionDefinition expression;

    public ExpressionNode() {
    }

    public ExpressionNode(ExpressionDefinition expression) {
        this.expression = expression;
    }

    public ExpressionNode(Expression expression) {
        if (expression != null) {
            setExpression(new ExpressionDefinition(expression));
        }
    }

    public ExpressionNode(Predicate predicate) {
        if (predicate != null) {
            setExpression(new ExpressionDefinition(predicate));
        }
    }

    @Override
    public String getShortName() {
        return "exp";
    }

    public ExpressionDefinition getExpression() {
        return expression;
    }

    public void setExpression(ExpressionDefinition expression) {
        this.expression = expression;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ProcessorDefinition> getOutputs() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public boolean isOutputSupported() {
        return false;
    }

    @Override
    public void addOutput(ProcessorDefinition output) {
        // add it to the parent as we do not support outputs
        getParent().addOutput(output);
    }

    @Override
    public String getLabel() {
        if (getExpression() == null) {
            return "";
        }
        return getExpression().getLabel();
    }

    protected FilterProcessor createFilterProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, false);
        return new FilterProcessor(getExpression().createPredicate(routeContext), childProcessor);
    }

}
