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
import org.apache.camel.impl.RouteContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
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
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteType extends ProcessorType implements CamelContextAware {
    private static final transient Log log = LogFactory.getLog(RouteType.class);
    @XmlElement(required = false, name = "interceptor")
    private List<InterceptorRef> interceptors = new ArrayList<InterceptorRef>();
    @XmlElementRef
    private List<FromType> inputs = new ArrayList<FromType>();
    @XmlElementRef
    private List<ProcessorType> outputs = new ArrayList<ProcessorType>();
    @XmlTransient
    private CamelContext camelContext;

    public RouteType() {
    }

    public RouteType(String uri) {
        getInputs().add(new FromType(uri));
    }

    public RouteType(Endpoint endpoint) {
        getInputs().add(new FromType(endpoint));
    }

    @Override
    public String toString() {
        return "Route[ " + inputs + " -> " + outputs + "]";
    }

    // TODO should we zap this and replace with next method?
    public void addRoutes(CamelContext context) throws Exception {
        Collection<Route> routes = new ArrayList<Route>();

        addRoutes(context, routes);

        context.addRoutes(routes);
    }

    public void addRoutes(CamelContext context, Collection<Route> routes) throws Exception {
        setCamelContext(context);

        for (FromType fromType : inputs) {
            addRoutes(routes, fromType);
        }
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

    // Fluent API
    //-----------------------------------------------------------------------
    public RouteType from(String uri) {
        getInputs().add(new FromType(uri));
        return this;
    }

    // Properties
    //-----------------------------------------------------------------------

    public List<InterceptorRef> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorRef> interceptors) {
        this.interceptors = interceptors;
    }

    public List<FromType> getInputs() {
        return inputs;
    }

    public void setInputs(List<FromType> inputs) {
        this.inputs = inputs;
    }

    public List<ProcessorType> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorType> outputs) {
        this.outputs = outputs;

        if (outputs != null) {
            for (ProcessorType output : outputs) {
                configureChild(output);
            }
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected void addRoutes(Collection<Route> routes, FromType fromType) throws Exception {
        RouteContext routeContext = new RouteContext(this, fromType, routes);
        Endpoint endpoint = routeContext.getEndpoint();

        for (ProcessorType output : outputs) {
            output.addRoutes(routeContext, routes);
        }

        routeContext.commit();
    }

    @Override
    protected void configureChild(ProcessorType output) {
        if (isInheritErrorHandler()) {
            output.setErrorHandlerBuilder(getErrorHandlerBuilder());
        }
        List<InterceptorRef> list = output.getInterceptors();
        if (list == null) {
            log.warn("No interceptor collection: " + output);
        }
        else {
            list.addAll(getInterceptors());
        }
    }
}
