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
import org.apache.camel.Route;
import org.apache.camel.Predicate;
import org.apache.camel.processor.DelegateProcessor;

/**
 * Represents a collection of routes
 *
 * @version $Revision: $
 */
@XmlRootElement(name = "routes")
@XmlAccessorType(XmlAccessType.FIELD)
public class RoutesType implements RouteContainer {
    
    // TODO: not sure how else to use an optional attribute in JAXB2
    @XmlAttribute
    private Boolean inheritErrorHandlerFlag = Boolean.TRUE; 
    @XmlElementRef
    private List<RouteType> routes = new ArrayList<RouteType>();
    @XmlElementRef
    private List<ServiceActivationType> activations = new ArrayList<ServiceActivationType>();
    @XmlTransient
    private List<InterceptorType> interceptors = new ArrayList<InterceptorType>();
    @XmlTransient
    private List<InterceptType> intercepts = new ArrayList<InterceptType>();
    @XmlTransient
    private List<ExceptionType> exceptions = new ArrayList<ExceptionType>();
    @XmlTransient
    private CamelContext camelContext;

    @Override
    public String toString() {
        return "Routes: " + routes;
    }

    public void populateRoutes(List<Route> answer) throws Exception {
        for (RouteType route : routes) {
            route.addRoutes(camelContext, answer);
        }
    }

    // Properties
    //-----------------------------------------------------------------------
    public List<RouteType> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteType> routes) {
        this.routes = routes;
    }

    public List<InterceptorType> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorType> interceptors) {
        this.interceptors = interceptors;
    }

    public List<InterceptType> getIntercepts() {
        return intercepts;
    }

    public void setIntercepts(List<InterceptType> intercepts) {
        this.intercepts = intercepts;
    }

    public List<ExceptionType> getExceptions() {
        return exceptions;
    }

    public void setExceptions(List<ExceptionType> exceptions) {
        this.exceptions = exceptions;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public Boolean getInheritErrorHandlerFlag() {
        return inheritErrorHandlerFlag;
    }

    public void setInheritErrorHandlerFlag(Boolean inheritErrorHandlerFlag) {
        this.inheritErrorHandlerFlag = inheritErrorHandlerFlag;
    }

    // Fluent API
    //-------------------------------------------------------------------------

    /**
     * Creates a new route
     */
    public RouteType route() {
        RouteType route = createRoute();
        return route(route);
    }

    /**
     * Creates a new route from the given URI input
     */
    public RouteType from(String uri) {
        RouteType route = createRoute();
        route.from(uri);
        return route(route);
    }

    /**
     * Creates a new route from the given endpoint
     */
    public RouteType from(Endpoint endpoint) {
        RouteType route = createRoute();
        route.from(endpoint);
        return route(route);
    }

    public RouteType route(RouteType route) {
        // lets configure the route
        route.setCamelContext(getCamelContext());
        route.setInheritErrorHandlerFlag(getInheritErrorHandlerFlag());
        route.getInterceptors().addAll(getInterceptors());
        route.getOutputs().addAll(getIntercepts());
        route.getOutputs().addAll(getExceptions());
        getRoutes().add(route);
        return route;
    }

    public RoutesType intercept(DelegateProcessor interceptor) {
        getInterceptors().add(new InterceptorRef(interceptor));
        return this;
    }
    
    public InterceptType intercept() {
        InterceptType answer = new InterceptType();
        getIntercepts().add(answer);
        return answer;
    }

    public OtherwiseType intercept(Predicate predicate) {
        InterceptType answer = new InterceptType();
        getIntercepts().add(answer);
        return answer.when(predicate);
    }

    public ExceptionType exception(Class exceptionType) {
        ExceptionType answer = new ExceptionType(exceptionType);
        getExceptions().add(answer);
        return answer;
    }


    // Implementation methods
    //-------------------------------------------------------------------------
    protected RouteType createRoute() {
        return new RouteType();
    }

}