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
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Route;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultRouteContext;
import org.apache.camel.processor.interceptor.StreamCachingInterceptor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Represents an XML &lt;route/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "route")
@XmlType(propOrder = {"inputs", "outputs" })
@XmlAccessorType(XmlAccessType.PROPERTY)
public class RouteType extends ProcessorType<ProcessorType> implements CamelContextAware {
    private static final transient Log LOG = LogFactory.getLog(RouteType.class);
    private List<InterceptorType> interceptors = new ArrayList<InterceptorType>();
    private List<FromType> inputs = new ArrayList<FromType>();
    private List<ProcessorType<?>> outputs = new ArrayList<ProcessorType<?>>();
    private String group;
    private CamelContext camelContext;
    private Boolean streamCaching;

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

    public void addRoutes(CamelContext context, Collection<Route> routes) throws Exception {
        setCamelContext(context);

        if (context instanceof DefaultCamelContext) {
            DefaultCamelContext defaultCamelContext = (DefaultCamelContext) context;
            ErrorHandlerBuilder handler = defaultCamelContext.getErrorHandlerBuilder();
            if (handler != null) {
                setErrorHandlerBuilderIfNull(handler);
            }
        }

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

    @XmlTransient
    public void setInterceptors(List<InterceptorType> interceptors) {
        this.interceptors = interceptors;
    }

    public List<FromType> getInputs() {
        return inputs;
    }

    @XmlElementRef
    public void setInputs(List<FromType> inputs) {
        this.inputs = inputs;
    }

    public List<ProcessorType<?>> getOutputs() {
        return outputs;
    }

    @XmlElementRef
    public void setOutputs(List<ProcessorType<?>> outputs) {
        this.outputs = outputs;

        // TODO I don't think this is called when using JAXB!
        if (outputs != null) {
            for (ProcessorType output : outputs) {
                configureChild(output);
            }
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    @XmlTransient
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

    @XmlAttribute
    public void setGroup(String group) {
        this.group = group;
    }

    public Boolean getStreamCaching() {
        return streamCaching;
    }

    /**
     * Enable stream caching on this route
     * @param streamCaching <code>true</code> for enabling stream caching
     */
    @XmlAttribute(required = false)
    public void setStreamCaching(Boolean streamCaching) {
        this.streamCaching = streamCaching;
        if (streamCaching != null && streamCaching) {
            streamCaching();
        } else {
            noStreamCaching();
        }
    }


    // Implementation methods
    // -------------------------------------------------------------------------
    protected void addRoutes(Collection<Route> routes, FromType fromType) throws Exception {
        RouteContext routeContext = new DefaultRouteContext(this, fromType, routes);
        routeContext.getEndpoint(); // force endpoint resolution
        if (camelContext != null) {
            camelContext.getLifecycleStrategy().onRouteContextCreate(routeContext);
        }

        List<ProcessorType<?>> list = new ArrayList<ProcessorType<?>>(outputs);
        for (ProcessorType output : list) {
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

        List<InterceptorType> interceptors = getInterceptors();
        for (InterceptorType interceptor : interceptors) {
            output.addInterceptor(interceptor);
        }
/*
        List<InterceptorType> list = output.getInterceptors();
        if (list == null) {
            LOG.warn("No interceptor collection: " + output);
        } else {
            list.addAll(getInterceptors());
        }
*/
    }

    /**
     * Disable stream caching for this Route.
     */
    public RouteType noStreamCaching() {
        StreamCachingInterceptor.noStreamCaching(interceptors);
        return this;
    }

    /**
     * Enable stream caching for this Route.
     */
    public RouteType streamCaching() {
        addInterceptor(new StreamCachingInterceptor());
        return this;
    }

    @Override
    public void addInterceptor(InterceptorType interceptor) {
        getInterceptors().add(interceptor);
    }
}
