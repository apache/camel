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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Defines a route template (parameterized routes)
 */
@Metadata(label = "configuration")
@XmlRootElement(name = "routeTemplate")
@XmlAccessorType(XmlAccessType.FIELD)
public class RouteTemplateDefinition extends RouteDefinition {

    @XmlAttribute
    private String properties;

    public String getProperties() {
        return properties;
    }

    /**
     * Route properties. Multiple keys can be separated by comma.
     */
    public void setProperties(String properties) {
        if (this.properties == null) {
            this.properties = properties;
        } else {
            this.properties += "," + properties;
        }
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Route properties. Multiple keys can be separated by comma.
     */
    public RouteTemplateDefinition properties(String properties) {
        setProperties(properties);
        return this;
    }

    @Override
    public String getShortName() {
        return "routeTemplate";
    }

    @Override
    public String getLabel() {
        return "RouteTemplate[" + getInput().getLabel() + "]";
    }

    /**
     * Creates a copy of this template as a {@link RouteDefinition} which can be used
     * to add as a new route.
     */
    public RouteDefinition asRouteDefinition() {
        RouteDefinition copy = new RouteDefinition();

        copy.setId(getId());
        copy.setInheritErrorHandler(isInheritErrorHandler());
        copy.setGroup(getGroup());
        copy.setStreamCache(getStreamCache());
        copy.setTrace(getTrace());
        copy.setMessageHistory(getMessageHistory());
        copy.setLogMask(getLogMask());
        copy.setDelayer(getDelayer());
        copy.setStartupOrder(getStartupOrder());
        copy.setRoutePolicies(getRoutePolicies());
        copy.setRoutePolicyRef(getRoutePolicyRef());
        copy.setShutdownRoute(getShutdownRoute());
        copy.setShutdownRunningTask(getShutdownRunningTask());
        copy.setErrorHandlerRef(getErrorHandlerRef());
        copy.setErrorHandlerFactory(getErrorHandlerFactory());
        copy.setInputType(getInputType());
        copy.setOutputType(getOutputType());
        copy.setRouteProperties(getRouteProperties());
        copy.setInput(getInput());
        copy.setOutputs(getOutputs());

        return copy;
    }
}
