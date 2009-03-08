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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Predicate;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.processor.DelegateProcessor;

/**
 * Represents a collection of routes
 *
 * @version $Revision$
 */
@XmlRootElement(name = "routes")
@XmlAccessorType(XmlAccessType.FIELD)
public class RoutesDefinition extends OptionalIdentifiedType<RoutesDefinition> implements RouteContainer {
    // TODO: not sure how else to use an optional attribute in JAXB2
    @XmlAttribute
    private Boolean inheritErrorHandlerFlag;
    @XmlElementRef
    private List<RouteDefinition> routes = new ArrayList<RouteDefinition>();
    @XmlTransient
    private List<AbstractInterceptorDefinition> interceptors = new ArrayList<AbstractInterceptorDefinition>();
    @XmlTransient
    private List<InterceptDefinition> intercepts = new ArrayList<InterceptDefinition>();
    @XmlTransient
    private List<OnExceptionDefinition> exceptions = new ArrayList<OnExceptionDefinition>();
    @XmlTransient
    private CamelContext camelContext;
    @XmlTransient
    private ErrorHandlerBuilder errorHandlerBuilder;

    @Override
    public String toString() {
        return "Routes: " + routes;
    }

    // Properties
    //-----------------------------------------------------------------------
    public List<RouteDefinition> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteDefinition> routes) {
        this.routes = routes;
    }

    public List<AbstractInterceptorDefinition> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<AbstractInterceptorDefinition> interceptors) {
        this.interceptors = interceptors;
    }

    public List<InterceptDefinition> getIntercepts() {
        return intercepts;
    }

    public void setIntercepts(List<InterceptDefinition> intercepts) {
        this.intercepts = intercepts;
    }

    public List<OnExceptionDefinition> getExceptions() {
        return exceptions;
    }

    public void setExceptions(List<OnExceptionDefinition> exceptions) {
        this.exceptions = exceptions;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isInheritErrorHandler() {
        return ProcessorDefinition.isInheritErrorHandler(getInheritErrorHandlerFlag());
    }

    public Boolean getInheritErrorHandlerFlag() {
        return inheritErrorHandlerFlag;
    }

    public void setInheritErrorHandlerFlag(Boolean inheritErrorHandlerFlag) {
        this.inheritErrorHandlerFlag = inheritErrorHandlerFlag;
    }

    public ErrorHandlerBuilder getErrorHandlerBuilder() {
        return errorHandlerBuilder;
    }

    public void setErrorHandlerBuilder(ErrorHandlerBuilder errorHandlerBuilder) {
        this.errorHandlerBuilder = errorHandlerBuilder;
    }

    // Fluent API
    //-------------------------------------------------------------------------

    /**
     * Creates a new route
     *
     * @return the builder
     */
    public RouteDefinition route() {
        RouteDefinition route = createRoute();
        return route(route);
    }

    /**
     * Creates a new route from the given URI input
     *
     * @param uri  the from uri
     * @return the builder
     */
    public RouteDefinition from(String uri) {
        RouteDefinition route = createRoute();
        route.from(uri);
        return route(route);
    }

    /**
     * Creates a new route from the given endpoint
     *
     * @param endpoint  the from endpoint
     * @return the builder
     */
    public RouteDefinition from(Endpoint endpoint) {
        RouteDefinition route = createRoute();
        route.from(endpoint);
        return route(route);
    }

    /**
     * Creates a new route from the given URI inputs
     *
     * @param uris  the from uri
     * @return the builder
     */
    public RouteDefinition from(String... uris) {
        RouteDefinition route = createRoute();
        route.from(uris);
        return route(route);
    }

    /**
     * Creates a new route from the given endpoints
     *
     * @param endpoints  the from endpoints
     * @return the builder
     */
    public RouteDefinition from(Endpoint... endpoints) {
        RouteDefinition route = createRoute();
        route.from(endpoints);
        return route(route);
    }

    /**
     * Creates a new route using the given route
     *
     * @param route the route
     * @return the builder
     */
    public RouteDefinition route(RouteDefinition route) {
        // lets configure the route
        route.setCamelContext(getCamelContext());
        route.setInheritErrorHandlerFlag(getInheritErrorHandlerFlag());
        List<AbstractInterceptorDefinition> list = getInterceptors();
        for (AbstractInterceptorDefinition interceptorType : list) {
            route.addInterceptor(interceptorType);
        }
        List<InterceptDefinition> intercepts = getIntercepts();
        for (InterceptDefinition intercept : intercepts) {
            // need to create a proxy for this one and use the
            // proceed of the proxy which will be local to this route
            InterceptDefinition proxy = intercept.createProxy();
            route.addOutput(proxy);
            route.pushBlock(proxy.getProceed());
        }
        route.getOutputs().addAll(getExceptions());
        getRoutes().add(route);
        return route;
    }

    /**
     * Adds an interceptor
     *
     * @param interceptor  the interceptor
     * @return the builder
     */
    public RoutesDefinition intercept(DelegateProcessor interceptor) {
        getInterceptors().add(new InterceptorDefinition(interceptor));
        return this;
    }

    /**
     * Creates and adds an interceptor
     *
     * @return the interceptor builder to configure
     */
    public InterceptDefinition intercept() {
        InterceptDefinition answer = new InterceptDefinition();
        getIntercepts().add(answer);
        return answer;
    }

    /**
     * Creates and adds an interceptor that is attached with a predicate
     *
     * @param predicate  the predicate
     * @return the builder
     */
    public ChoiceDefinition intercept(Predicate predicate) {
        InterceptDefinition answer = new InterceptDefinition();
        getIntercepts().add(answer);
        return answer.when(predicate);
    }

    /**
     * Adds an on exception
     * 
     * @param exception  the exception
     * @return the builder
     */
    public OnExceptionDefinition onException(Class exception) {
        OnExceptionDefinition answer = new OnExceptionDefinition(exception);
        getExceptions().add(answer);
        return answer;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected RouteDefinition createRoute() {
        RouteDefinition route = new RouteDefinition();
        ErrorHandlerBuilder handler = getErrorHandlerBuilder();
        if (isInheritErrorHandler() && handler != null) {
            route.setErrorHandlerBuilderIfNull(handler);
        }
        return route;
    }
}
