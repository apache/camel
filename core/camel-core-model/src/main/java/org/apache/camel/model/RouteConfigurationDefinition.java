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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.model.errorhandler.RefErrorHandlerDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;

/**
 * Reusable configuration for Camel route(s).
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "routeConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteConfigurationDefinition extends OptionalIdentifiedDefinition<RouteConfigurationDefinition>
        implements PreconditionContainer, ResourceAware {

    @XmlTransient
    private Resource resource;
    @XmlElement
    private ErrorHandlerDefinition errorHandler;
    @XmlElement(name = "intercept")
    private List<InterceptDefinition> intercepts = new ArrayList<>();
    @XmlElement(name = "interceptFrom")
    private List<InterceptFromDefinition> interceptFroms = new ArrayList<>();
    @XmlElement(name = "interceptSendToEndpoint")
    private List<InterceptSendToEndpointDefinition> interceptSendTos = new ArrayList<>();
    @XmlElement(name = "onException")
    private List<OnExceptionDefinition> onExceptions = new ArrayList<>();
    @XmlElement(name = "onCompletion")
    private List<OnCompletionDefinition> onCompletions = new ArrayList<>();
    @XmlAttribute
    @Metadata(label = "advanced")
    private String precondition;

    public RouteConfigurationDefinition() {
    }

    @Override
    public String toString() {
        return "RoutesConfiguration: " + getId();
    }

    @Override
    public String getShortName() {
        return "routesConfiguration";
    }

    @Override
    public String getLabel() {
        return "RoutesConfiguration " + getId();
    }

    @Override
    public Resource getResource() {
        return resource;
    }

    @Override
    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public ErrorHandlerDefinition getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(ErrorHandlerDefinition errorHandler) {
        this.errorHandler = errorHandler;
    }

    public List<OnExceptionDefinition> getOnExceptions() {
        return onExceptions;
    }

    public void setOnExceptions(List<OnExceptionDefinition> onExceptions) {
        this.onExceptions = onExceptions;
    }

    public List<OnCompletionDefinition> getOnCompletions() {
        return onCompletions;
    }

    public void setOnCompletions(List<OnCompletionDefinition> onCompletions) {
        this.onCompletions = onCompletions;
    }

    public List<InterceptDefinition> getIntercepts() {
        return intercepts;
    }

    public void setIntercepts(List<InterceptDefinition> intercepts) {
        this.intercepts = intercepts;
    }

    public List<InterceptFromDefinition> getInterceptFroms() {
        return interceptFroms;
    }

    public void setInterceptFroms(List<InterceptFromDefinition> interceptFroms) {
        this.interceptFroms = interceptFroms;
    }

    public List<InterceptSendToEndpointDefinition> getInterceptSendTos() {
        return interceptSendTos;
    }

    public void setInterceptSendTos(List<InterceptSendToEndpointDefinition> interceptSendTos) {
        this.interceptSendTos = interceptSendTos;
    }

    /**
     * The predicate of the precondition in simple language to evaluate in order to determine if this route
     * configuration should be included or not.
     */
    @Override
    public String getPrecondition() {
        return precondition;
    }

    /**
     * The predicate of the precondition in simple language to evaluate in order to determine if this route
     * configuration should be included or not.
     */
    @Override
    public void setPrecondition(String precondition) {
        this.precondition = precondition;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the error handler to use, for routes that has not already been configured with an error handler.
     *
     * @param  ref reference to existing error handler
     * @return     the builder
     */
    public RouteConfigurationDefinition errorHandler(String ref) {
        ErrorHandlerDefinition def = new ErrorHandlerDefinition();
        def.setErrorHandlerType(new RefErrorHandlerDefinition(ref));
        setErrorHandler(def);
        return this;
    }

    /**
     * Sets the error handler to use, for routes that has not already been configured with an error handler.
     *
     * @param  errorHandler the error handler
     * @return              the builder
     */
    public RouteConfigurationDefinition errorHandler(ErrorHandlerFactory errorHandler) {
        ErrorHandlerDefinition def = new ErrorHandlerDefinition();
        def.setErrorHandlerType(errorHandler);
        setErrorHandler(def);
        return this;
    }

    /**
     * Sets the predicate of the precondition in simple language to evaluate in order to determine if this route
     * configuration should be included or not.
     *
     * @param  precondition the predicate corresponding to the test to evaluate.
     * @return              the builder
     */
    public RouteConfigurationDefinition precondition(String precondition) {
        setPrecondition(precondition);
        return this;
    }

    /**
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a> for catching certain exceptions and
     * handling them.
     *
     * @param  exceptionType the exception to catch
     * @return               the exception builder to configure
     */
    public OnExceptionDefinition onException(Class<? extends Throwable> exceptionType) {
        OnExceptionDefinition answer = new OnExceptionDefinition(exceptionType);
        answer.setRouteConfiguration(this);
        onExceptions.add(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a> for catching certain exceptions and
     * handling them.
     *
     * @param  exceptions list of exceptions to catch
     * @return            the exception builder to configure
     */
    public OnExceptionDefinition onException(Class<? extends Throwable>... exceptions) {
        OnExceptionDefinition answer = new OnExceptionDefinition(Arrays.asList(exceptions));
        answer.setRouteConfiguration(this);
        onExceptions.add(answer);
        return answer;
    }

    /**
     * <a href="http://camel.apache.org/oncompletion.html">On completion</a> callback for doing custom routing when the
     * {@link org.apache.camel.Exchange} is complete.
     *
     * @return the on completion builder to configure
     */
    public OnCompletionDefinition onCompletion() {
        OnCompletionDefinition answer = new OnCompletionDefinition();
        answer.setRouteConfiguration(this);
        // is global scoped by default
        answer.setRouteScoped(false);
        onCompletions.add(answer);
        return answer;
    }

    /**
     * Adds a route for an interceptor that intercepts every processing step.
     *
     * @return the builder
     */
    public InterceptDefinition intercept() {
        InterceptDefinition answer = new InterceptDefinition();
        answer.setRouteConfiguration(this);
        intercepts.add(answer);
        return answer;
    }

    /**
     * Adds a route for an interceptor that intercepts incoming messages on any inputs in this route
     *
     * @return the builder
     */
    public InterceptFromDefinition interceptFrom() {
        InterceptFromDefinition answer = new InterceptFromDefinition();
        answer.setRouteConfiguration(this);
        interceptFroms.add(answer);
        return answer;
    }

    /**
     * Adds a route for an interceptor that intercepts incoming messages on the given endpoint.
     *
     * @param  uri endpoint uri
     * @return     the builder
     */
    public InterceptFromDefinition interceptFrom(String uri) {
        InterceptFromDefinition answer = new InterceptFromDefinition(uri);
        answer.setRouteConfiguration(this);
        interceptFroms.add(answer);
        return answer;
    }

    /**
     * Applies a route for an interceptor if an exchange is send to the given endpoint
     *
     * @param  uri endpoint uri
     * @return     the builder
     */
    public InterceptSendToEndpointDefinition interceptSendToEndpoint(String uri) {
        InterceptSendToEndpointDefinition answer = new InterceptSendToEndpointDefinition(uri);
        answer.setRouteConfiguration(this);
        interceptSendTos.add(answer);
        return answer;
    }

}
