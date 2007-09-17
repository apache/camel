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

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.processor.FilterProcessor;

/**
 * A base class for nodes which contain an expression and a number of outputs
 *
 * @version $Revision: $
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class ExpressionNode extends ProcessorType<ProcessorType> {
    @XmlElementRef
    private List<InterceptorType> interceptors = new ArrayList<InterceptorType>();
    @XmlElementRef
    private ExpressionType expression;
    @XmlElementRef
    private List<ProcessorType<?>> outputs = new ArrayList<ProcessorType<?>>();

    public ExpressionNode() {
    }

    public ExpressionNode(ExpressionType expression) {
        this.expression = expression;
    }

    public ExpressionNode(Expression expression) {
        setExpression(new ExpressionType(expression));
    }

    public ExpressionNode(Predicate predicate) {
        setExpression(new ExpressionType(predicate));
    }

    public List<InterceptorType> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorType> interceptors) {
        this.interceptors = interceptors;
    }

    public ExpressionType getExpression() {
        return expression;
    }

    public void setExpression(ExpressionType expression) {
        this.expression = expression;
    }

    public List<ProcessorType<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorType<?>> outputs) {
        this.outputs = outputs;
    }

    @Override
    public String getLabel() {
        if (getExpression() == null) {
            return "";
        }
        return getExpression().getLabel();
    }

    protected FilterProcessor createFilterProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = routeContext.createProcessor(this);
        return new FilterProcessor(getExpression().createPredicate(routeContext), childProcessor);
    }

    @Override
    protected void configureChild(ProcessorType output) {
        super.configureChild(output);
        if (isInheritErrorHandler()) {
            output.setErrorHandlerBuilder(getErrorHandlerBuilder());
        }
    }
}
