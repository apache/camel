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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.impl.RouteContext;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Represents an XML &lt;route/&gt; element
 *
 * @version $Revision: $
 */
@XmlRootElement(name = "route")
@XmlType(propOrder = {"interceptors", "inputs", "outputs"})
public class RouteType extends ProcessorType implements CamelContextAware {
    private CamelContext camelContext;
    private List<InterceptorRef> interceptors = new ArrayList<InterceptorRef>();
    private List<FromType> inputs = new ArrayList<FromType>();
    private List<ProcessorType> outputs = new ArrayList<ProcessorType>();

    @Override
    public String toString() {
        return "Route[ " + inputs + " -> " + outputs + "]";
    }

    public void addRoutes(CamelContext context) throws Exception {
        setCamelContext(context);

        Collection<Route> routes = new ArrayList<Route>();

        for (FromType fromType : inputs) {
            addRoutes(routes, fromType);
        }

        context.addRoutes(routes);
    }

    public Endpoint resolveEndpoint(String uri) {
        CamelContext context = getCamelContext();
        if (context == null) {
            throw new IllegalArgumentException("No CamelContext has been injected!");
        }
        Endpoint answer = context.getEndpoint(uri);
        if (answer == null) {
            throw new IllegalArgumentException("No Endpoint found for uri: " + uri);
        }
        return answer;
    }


    // Properties
    //-----------------------------------------------------------------------

    @XmlElement(required = false, name = "interceptor")
    public List<InterceptorRef> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorRef> interceptors) {
        this.interceptors = interceptors;
    }

    @XmlElementRef
    public List<FromType> getInputs() {
        return inputs;
    }

    public void setInputs(List<FromType> inputs) {
        this.inputs = inputs;
    }

    @XmlElementRef
    public List<ProcessorType> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorType> outputs) {
        this.outputs = outputs;
    }

    @XmlTransient
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    // Fluent API
    //-----------------------------------------------------------------------
    public RouteType from(String uri) {
        getInputs().add(new FromType(uri));
        return this;
    }

    protected void addRoutes(Collection<Route> routes, FromType fromType) throws Exception {
        Endpoint endpoint = resolveEndpoint(fromType.getUri());
        RouteContext routeContext = new RouteContext(this, fromType, endpoint);
        for (ProcessorType output : outputs) {
            output.addRoutes(routeContext, routes);
        }
    }
}
