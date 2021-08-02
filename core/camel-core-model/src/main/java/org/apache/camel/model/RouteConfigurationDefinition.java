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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Reusable configuration for Camel route(s).
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "routeConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteConfigurationDefinition extends OptionalIdentifiedDefinition<RouteConfigurationDefinition> {

    // TODO: Model for ErrorHandler

    @XmlElementRef
    private List<OnExceptionDefinition> onExceptions = new ArrayList<>();
    @XmlElementRef
    private List<OnCompletionDefinition> onCompletions = new ArrayList<>();
    @XmlElementRef
    private List<InterceptDefinition> intercepts = new ArrayList<>();
    @XmlElementRef
    private List<InterceptFromDefinition> interceptFroms = new ArrayList<>();
    @XmlElementRef
    private List<InterceptSendToEndpointDefinition> interceptSendTos = new ArrayList<>();

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

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * <a href="http://camel.apache.org/exception-clause.html">Exception clause</a> for catching certain exceptions and
     * handling them.
     *
     * @param  exceptionType the exception to catch
     * @return               the exception builder to configure
     */
    public OnExceptionDefinition onException(Class<? extends Throwable> exceptionType) {
        OnExceptionDefinition answer = new OnExceptionDefinition(exceptionType);
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
        onExceptions.add(answer);
        return answer;
    }

}
