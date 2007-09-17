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

import org.apache.camel.*;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Represents an XML &lt;route/&gt; element
 * 
 * @version $Revision: $
 */
@XmlRootElement(name = "route")
@XmlType(propOrder = {"interceptors", "inputs", "outputs" })
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteType extends ProcessorType implements CamelContextAware {
    private static final transient Log LOG = LogFactory.getLog(RouteType.class);
    @XmlElementRef
    private List<InterceptorType> interceptors = new ArrayList<InterceptorType>();
    @XmlElementRef
    private List<FromType> inputs = new ArrayList<FromType>();
    @XmlElementRef
    private List<ProcessorType> outputs = new ArrayList<ProcessorType>();
    @XmlAttribute
    private String group;
    @XmlTransient
    private CamelContext camelContext;

    public RouteType() {
    }

    public RouteType(String uri) {
        from(uri);
    }

    public RouteType(Endpoint endpoint) {
        from(endpoint);
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

    public Endpoint resolveEndpoint(String uri) throws NoSuchEndpointException {
        CamelContext context = getCamelContext();
        if (context == null) {
            throw new IllegalArgumentException("No CamelContext has been injected!");
        }
        return CamelContextHelper.getMandatoryEndpoint(context, uri);
    }

    // Fluent API
    // -----------------------------------------------------------------------

    /**
     * Creates an input to the route
     */
    public RouteType from(String uri) {
        getInputs().add(new FromType(uri));
        return this;
    }

    /**
     * Creates an input to the route
     */
    public RouteType from(Endpoint endpoint) {
        getInputs().add(new FromType(endpoint));
        return this;
    }

    /**
     * Set the group name for this route
     */
    public RouteType group(String name) {
        setGroup(name);
        return this;
    }

    // Properties
    // -----------------------------------------------------------------------

    public List<InterceptorType> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorType> interceptors) {
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

    /**
     * The group that this route belongs to; could be the name of the RouteBuilder class
     * or be explicitly configured in the XML.
     *
     * May be null.
     */
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

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
        super.configureChild(output);

        if (isInheritErrorHandler()) {
            output.setErrorHandlerBuilder(getErrorHandlerBuilder());
        }
        List<InterceptorType> list = output.getInterceptors();
        if (list == null) {
            LOG.warn("No interceptor collection: " + output);
        } else {
            list.addAll(getInterceptors());
        }
    }
}
