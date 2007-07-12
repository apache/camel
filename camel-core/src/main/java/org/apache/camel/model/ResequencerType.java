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
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.processor.Resequencer;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "resequencer")
public class ResequencerType extends ProcessorType {
    private ExpressionType expression;

    protected List<ProcessorType> outputs = new ArrayList<ProcessorType>();
    private List<InterceptorRef> interceptors = new ArrayList<InterceptorRef>();

    @Override
    public String toString() {
        return "Resequencer[ " + getExpression() + " -> " + getOutputs() + "]";
    }

    public void addRoutes(RouteContext routeContext, Collection<Route> routes) {
        Endpoint from = routeContext.getEndpoint();
        final Processor processor = routeContext.createProcessor(getOutputs());
        final Resequencer resequencer = new Resequencer(from, processor, getExpression().createExpression(routeContext));

        Route<Exchange> route = new Route<Exchange>(from) {
            protected void addServices(List<Service> list) throws Exception {
                list.add(resequencer);
            }

            @Override
            public String toString() {
                return "ResequencerRoute[" + getEndpoint() + " -> " + processor + "]";
            }
        };
        routes.add(route);
    }

    @XmlElementRef
    public ExpressionType getExpression() {
        return expression;
    }

    public void setExpression(ExpressionType expression) {
        this.expression = expression;
    }

    @XmlElementRef
    public List<ProcessorType> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorType> outputs) {
        this.outputs = outputs;
    }

    @XmlElement(required = false)
    public List<InterceptorRef> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorRef> interceptors) {
        this.interceptors = interceptors;
    }

}