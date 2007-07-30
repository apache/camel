/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.model;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.processor.Resequencer;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "resequencer")
public class ResequencerType extends ProcessorType {
    @XmlElement(required = false)
    private List<InterceptorRef> interceptors = new ArrayList<InterceptorRef>();
    @XmlElementRef
    private List<ExpressionType> expressions = new ArrayList<ExpressionType>();
    @XmlElementRef
    private List<ProcessorType> outputs = new ArrayList<ProcessorType>();
    @XmlTransient
    private List<Expression> expressionList;

    public ResequencerType() {
    }

    public ResequencerType(List<Expression> expressions) {
        this.expressionList = expressions;
    }

    @Override
    public String toString() {
        return "Resequencer[ " + getExpressions() + " -> " + getOutputs() + "]";
    }

    public List<ExpressionType> getExpressions() {
        return expressions;
    }

    public List<InterceptorRef> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorRef> interceptors) {
        this.interceptors = interceptors;
    }

    public List<ProcessorType> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorType> outputs) {
        this.outputs = outputs;
    }

    public void addRoutes(RouteContext routeContext, Collection<Route> routes) throws Exception {
        Endpoint from = routeContext.getEndpoint();
        final Processor processor = routeContext.createProcessor(this);
        final Resequencer resequencer = new Resequencer(from, processor, resolveExpressionList(routeContext));

        Route route = new Route<Exchange>(from, resequencer) {
            @Override
            public String toString() {
                return "ResequencerRoute[" + getEndpoint() + " -> " + processor + "]";
            }
        };

        routes.add(route);
    }

    private List<Expression> resolveExpressionList(RouteContext routeContext) {
        if (expressionList == null) {
            expressionList = new ArrayList<Expression>();
            for (ExpressionType expression : expressions) {
                expressionList.add(expression.createExpression(routeContext));
            }
        }
        if (expressionList.isEmpty()) {
            throw new IllegalArgumentException("No expressions configured for: " + this);
        }
        return expressionList;
    }
}