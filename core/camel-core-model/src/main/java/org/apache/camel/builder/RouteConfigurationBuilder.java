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
package org.apache.camel.builder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;

/**
 * A <a href="http://camel.apache.org/dsl.html">Java DSL</a> which is used for building route configuration(s).
 */
public abstract class RouteConfigurationBuilder extends RouteBuilder implements RouteConfigurationsBuilder {

    private final AtomicBoolean initializedConfiguration = new AtomicBoolean();
    private RouteConfigurationsDefinition routeConfigurationCollection = new RouteConfigurationsDefinition();

    @Override
    public void configure() throws Exception {
        // noop
    }

    @Override
    public abstract void configuration() throws Exception;

    public RouteConfigurationsDefinition getRouteConfigurationCollection() {
        return routeConfigurationCollection;
    }

    public void setRouteConfigurationCollection(RouteConfigurationsDefinition routeConfigurationCollection) {
        this.routeConfigurationCollection = routeConfigurationCollection;
    }

    /**
     * Creates a new route configuration
     *
     * @return the builder
     */
    public RouteConfigurationDefinition routeConfiguration() {
        return routeConfiguration(null);
    }

    /**
     * Creates a new route configuration
     *
     * @return the builder
     */
    public RouteConfigurationDefinition routeConfiguration(String id) {
        getRouteConfigurationCollection().setCamelContext(getCamelContext());
        RouteConfigurationDefinition answer = getRouteConfigurationCollection().routeConfiguration(id);
        configureRouteConfiguration(answer);
        return answer;
    }

    @Override
    public void addRouteConfigurationsToCamelContext(CamelContext context) throws Exception {
        setCamelContext(context);
        routeConfigurationCollection.setCamelContext(context);
        if (initializedConfiguration.compareAndSet(false, true)) {
            configuration();
        }
        populateRoutesConfiguration();
    }

    @Override
    public void updateRouteConfigurationsToCamelContext(CamelContext context) throws Exception {
        setCamelContext(context);
        routeConfigurationCollection.setCamelContext(context);
        if (initializedConfiguration.compareAndSet(false, true)) {
            configuration();
        }
        List<RouteConfigurationDefinition> list = getRouteConfigurationCollection().getRouteConfigurations();
        if (!list.isEmpty()) {
            // remove existing before updating
            for (RouteConfigurationDefinition def : list) {
                context.getCamelContextExtension().getContextPlugin(Model.class).removeRouteConfiguration(def);
            }
            populateRoutesConfiguration();
        }
    }

    protected void populateRoutesConfiguration() throws Exception {
        CamelContext camelContext = getContext();
        if (camelContext == null) {
            throw new IllegalArgumentException("CamelContext has not been injected!");
        }
        getRouteConfigurationCollection().setCamelContext(camelContext);
        if (getResource() != null) {
            getRouteConfigurationCollection().setResource(getResource());
        }
        camelContext.getCamelContextExtension().getContextPlugin(Model.class)
                .addRouteConfigurations(getRouteConfigurationCollection().getRouteConfigurations());
    }

}
