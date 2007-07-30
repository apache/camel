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
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.processor.DelegateProcessor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a collection of routes
 *
 * @version $Revision: $
 */
@XmlRootElement(name = "routes")
@XmlAccessorType(XmlAccessType.FIELD)
public class RoutesType implements RouteContainer {
    @XmlAttribute
    private Boolean inheritErrorHandlerFlag = Boolean.TRUE; // TODO not sure how else to use an optional attribute in JAXB2
    @XmlElementRef
    private List<RouteType> routes = new ArrayList<RouteType>();
    @XmlTransient
    private List<InterceptorRef> interceptors = new ArrayList<InterceptorRef>();
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

    public List<InterceptorRef> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<InterceptorRef> interceptors) {
        this.interceptors = interceptors;
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
    public RouteType route() {
        RouteType route = new RouteType();
        return route(route);
    }

    public RouteType from(String uri) {
        RouteType route = new RouteType(uri);
        return route(route);
    }

    public RouteType from(Endpoint endpoint) {
        RouteType route = new RouteType(endpoint);
        return route(route);
    }

    public RouteType route(RouteType route) {
        // lets configure the route
        route.setCamelContext(getCamelContext());
        route.setInheritErrorHandlerFlag(getInheritErrorHandlerFlag());
        route.getInterceptors().addAll(getInterceptors());

        getRoutes().add(route);
        return route;
    }

    public RoutesType intercept(DelegateProcessor interceptor) {
        getInterceptors().add(new InterceptorRef(interceptor));
        return this;
    }
}