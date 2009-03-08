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
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.ErrorHandlerBuilder;
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
public class RouteDefinition extends ProcessorDefinition<ProcessorDefinition> implements CamelContextAware {
    private static final transient Log LOG = LogFactory.getLog(RouteDefinition.class);
    private List<AbstractInterceptorDefinition> interceptors = new ArrayList<AbstractInterceptorDefinition>();
    private List<FromDefinition> inputs = new ArrayList<FromDefinition>();
    private List<ProcessorDefinition> outputs = new ArrayList<ProcessorDefinition>();
    private String group;
    private CamelContext camelContext;
    private Boolean streamCaching;

    public RouteDefinition() {
    }

    public RouteDefinition(String uri) {
        from(uri);
    }

    public RouteDefinition(Endpoint endpoint) {
        from(endpoint);
    }

    @Override
    public String toString() {
        return "Route[" + inputs + " -> " + outputs + "]";
    }


    /**
     * Returns the status of the route if it has been registered with a {@link CamelContext}
     */
    public ServiceStatus getStatus() {
        if (camelContext != null) {
            ServiceStatus answer = camelContext.getRouteStatus(this);
            if (answer == null) {
                answer = ServiceStatus.Stopped;
            }
            return answer;
        }
        return null;
    }

    public boolean isStartable() {
        ServiceStatus status = getStatus();
        if (status == null) {
            return true;
        } else {
            return status.isStartable();
        }
    }

    public boolean isStoppable() {
        ServiceStatus status = getStatus();
        if (status == null) {
            return false;
        } else {
            return status.isStoppable();
        }
    }
    
    public List<RouteContext> addRoutes(CamelContext context, Collection<Route> routes) throws Exception {
        List<RouteContext> answer = new ArrayList<RouteContext>();
        setCamelContext(context);

        ErrorHandlerBuilder handler = context.getErrorHandlerBuilder();
        if (handler != null) {
            setErrorHandlerBuilderIfNull(handler);
        }

        for (FromDefinition fromType : inputs) {
            RouteContext routeContext = addRoutes(routes, fromType);
            answer.add(routeContext);
        }
        return answer;
    }


    public Endpoint resolveEndpoint(String uri) throws NoSuchEndpointException {
        CamelContext context = getCamelContext();
        if (context == null) {
            throw new IllegalArgumentException("CamelContext has not been injected!");
        }
        return CamelContextHelper.getMandatoryEndpoint(context, uri);
    }

    // Fluent API
    // -----------------------------------------------------------------------

    /**
     * Creates an input to the route
     *
     * @param uri  the from uri
     * @return the builder
     */
    public RouteDefinition from(String uri) {
        getInputs().add(new FromDefinition(uri));
        return this;
    }

    /**
     * Creates an input to the route
     *
     * @param endpoint  the from endpoint
     * @return the builder
     */
    public RouteDefinition from(Endpoint endpoint) {
        getInputs().add(new FromDefinition(endpoint));
        return this;
    }

    /**
     * Creates inputs to the route
     *
     * @param uris  the from uris
     * @return the builder
     */
    public RouteDefinition from(String... uris) {
        for (String uri : uris) {
            getInputs().add(new FromDefinition(uri));
        }
        return this;
    }

    /**
     * Creates inputs to the route
     *
     * @param endpoints  the from endpoints
     * @return the builder
     */
    public RouteDefinition from(Endpoint... endpoints) {
        for (Endpoint endpoint : endpoints) {
            getInputs().add(new FromDefinition(endpoint));
        }
        return this;
    }

    /**
     * Set the group name for this route
     *
     * @param name  the group name
     * @return the builder
     */
    public RouteDefinition group(String name) {
        setGroup(name);
        return this;
    }

    /**
     * Disable stream caching for this route
     *
     * @return the builder
     */
    public RouteDefinition noStreamCaching() {
        StreamCachingInterceptor.noStreamCaching(interceptors);
        return this;
    }

    /**
     * Enable stream caching for this route
     *
     * @return the builder
     */
    public RouteDefinition streamCaching() {
        addInterceptor(new StreamCachingInterceptor());
        return this;
    }

    // Properties
    // -----------------------------------------------------------------------

    public List<AbstractInterceptorDefinition> getInterceptors() {
        return interceptors;
    }

    @XmlTransient
    public void setInterceptors(List<AbstractInterceptorDefinition> interceptors) {
        this.interceptors = interceptors;
    }

    public List<FromDefinition> getInputs() {
        return inputs;
    }

    @XmlElementRef
    public void setInputs(List<FromDefinition> inputs) {
        this.inputs = inputs;
    }

    public List<ProcessorDefinition> getOutputs() {
        return outputs;
    }

    @XmlElementRef
    public void setOutputs(List<ProcessorDefinition> outputs) {
        this.outputs = outputs;

        // TODO I don't think this is called when using JAXB!
        if (outputs != null) {
            for (ProcessorDefinition output : outputs) {
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
    protected RouteContext addRoutes(Collection<Route> routes, FromDefinition fromType) throws Exception {
        RouteContext routeContext = new DefaultRouteContext(this, fromType, routes);
        routeContext.getEndpoint(); // force endpoint resolution
        if (camelContext != null) {
            camelContext.getLifecycleStrategy().onRouteContextCreate(routeContext);
        }

        List<ProcessorDefinition> list = new ArrayList<ProcessorDefinition>(outputs);
        for (ProcessorDefinition output : list) {
            output.addRoutes(routeContext, routes);
        }

        routeContext.commit();
        return routeContext;
    }

    @Override
    protected void configureChild(ProcessorDefinition output) {
        super.configureChild(output);

        if (isInheritErrorHandler()) {
            output.setErrorHandlerBuilder(getErrorHandlerBuilder());
        }

        List<AbstractInterceptorDefinition> interceptors = getInterceptors();
        for (AbstractInterceptorDefinition interceptor : interceptors) {
            output.addInterceptor(interceptor);
        }
    }

    @Override
    public void addInterceptor(AbstractInterceptorDefinition interceptor) {
        getInterceptors().add(interceptor);
    }

}
