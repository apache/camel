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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.support.PatternHelper;

/**
 * Model interface
 */
public interface Model {

    /**
     * Returns a list of the current route definitions
     *
     * @return list of the current route definitions
     */
    List<RouteDefinition> getRouteDefinitions();

    /**
     * Gets the route definition with the given id
     *
     * @param id id of the route
     * @return the route definition or <tt>null</tt> if not found
     */
    RouteDefinition getRouteDefinition(String id);

    /**
     * Adds a collection of route definitions to the context
     * <p/>
     * <b>Important: </b> Each route in the same {@link CamelContext} must have
     * an <b>unique</b> route id. If you use the API from {@link CamelContext}
     * or {@link Model} to add routes, then any new routes which has a route id
     * that matches an old route, then the old route is replaced by the new
     * route.
     *
     * @param routeDefinitions the route(s) definition to add
     * @throws Exception if the route definitions could not be added for
     *             whatever reason
     */
    void addRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception;

    /**
     * Add a route definition to the context
     * <p/>
     * <b>Important: </b> Each route in the same {@link CamelContext} must have
     * an <b>unique</b> route id. If you use the API from {@link CamelContext}
     * or {@link Model} to add routes, then any new routes which has a route id
     * that matches an old route, then the old route is replaced by the new
     * route.
     *
     * @param routeDefinition the route definition to add
     * @throws Exception if the route definition could not be added for whatever
     *             reason
     */
    void addRouteDefinition(RouteDefinition routeDefinition) throws Exception;

    /**
     * Removes a collection of route definitions from the context - stopping any
     * previously running routes if any of them are actively running
     *
     * @param routeDefinitions route(s) definitions to remove
     * @throws Exception if the route definitions could not be removed for
     *             whatever reason
     */
    void removeRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception;

    /**
     * Removes a route definition from the context - stopping any previously
     * running routes if any of them are actively running
     *
     * @param routeDefinition route definition to remove
     * @throws Exception if the route definition could not be removed for
     *             whatever reason
     */
    void removeRouteDefinition(RouteDefinition routeDefinition) throws Exception;

    /**
     * Returns a list of the current REST definitions
     *
     * @return list of the current REST definitions
     */
    List<RestDefinition> getRestDefinitions();

    /**
     * Adds a collection of rest definitions to the context
     *
     * @param restDefinitions the rest(s) definition to add
     * @param addToRoutes whether the rests should also automatically be added
     *            as routes
     * @throws Exception if the rest definitions could not be created for
     *             whatever reason
     */
    void addRestDefinitions(Collection<RestDefinition> restDefinitions, boolean addToRoutes) throws Exception;

    /**
     * Sets the data formats that can be referenced in the routes.
     *
     * @param dataFormats the data formats
     */
    void setDataFormats(Map<String, DataFormatDefinition> dataFormats);

    /**
     * Gets the data formats that can be referenced in the routes.
     *
     * @return the data formats available
     */
    Map<String, DataFormatDefinition> getDataFormats();

    /**
     * Resolve a data format definition given its name
     *
     * @param name the data format definition name or a reference to it in the
     *            {@link org.apache.camel.spi.Registry}
     * @return the resolved data format definition, or <tt>null</tt> if not
     *         found
     */
    DataFormatDefinition resolveDataFormatDefinition(String name);

    /**
     * Gets the processor definition from any of the routes which with the given
     * id
     *
     * @param id id of the processor definition
     * @return the processor definition or <tt>null</tt> if not found
     */
    ProcessorDefinition<?> getProcessorDefinition(String id);

    /**
     * Gets the processor definition from any of the routes which with the given
     * id
     *
     * @param id id of the processor definition
     * @param type the processor definition type
     * @return the processor definition or <tt>null</tt> if not found
     * @throws ClassCastException is thrown if the type is not correct type
     */
    <T extends ProcessorDefinition<T>> T getProcessorDefinition(String id, Class<T> type);

    /**
     * Sets the validators that can be referenced in the routes.
     *
     * @param validators the validators
     */
    void setValidators(List<ValidatorDefinition> validators);

    /**
     * Gets the Hystrix configuration by the given name. If no name is given the
     * default configuration is returned, see <tt>setHystrixConfiguration</tt>
     *
     * @param id id of the configuration, or <tt>null</tt> to return the default
     *            configuration
     * @return the configuration, or <tt>null</tt> if no configuration has been
     *         registered
     */
    HystrixConfigurationDefinition getHystrixConfiguration(String id);

    /**
     * Sets the default Hystrix configuration
     *
     * @param configuration the configuration
     */
    void setHystrixConfiguration(HystrixConfigurationDefinition configuration);

    /**
     * Sets the Hystrix configurations
     *
     * @param configurations the configuration list
     */
    void setHystrixConfigurations(List<HystrixConfigurationDefinition> configurations);

    /**
     * Adds the Hystrix configuration
     *
     * @param id name of the configuration
     * @param configuration the configuration
     */
    void addHystrixConfiguration(String id, HystrixConfigurationDefinition configuration);

    /**
     * Gets the Resilience4j configuration by the given name. If no name is given the
     * default configuration is returned, see <tt>setResilience4jConfiguration</tt>
     *
     * @param id id of the configuration, or <tt>null</tt> to return the default
     *            configuration
     * @return the configuration, or <tt>null</tt> if no configuration has been
     *         registered
     */
    Resilience4jConfigurationDefinition getResilience4jConfiguration(String id);

    /**
     * Sets the default Resilience4j configuration
     *
     * @param configuration the configuration
     */
    void setResilience4jConfiguration(Resilience4jConfigurationDefinition configuration);

    /**
     * Sets the Resilience4j configurations
     *
     * @param configurations the configuration list
     */
    void setResilience4jConfigurations(List<Resilience4jConfigurationDefinition> configurations);

    /**
     * Adds the Resilience4j configuration
     *
     * @param id name of the configuration
     * @param configuration the configuration
     */
    void addResilience4jConfiguration(String id, Resilience4jConfigurationDefinition configuration);

    /**
     * Gets the validators that can be referenced in the routes.
     *
     * @return the validators available
     */
    List<ValidatorDefinition> getValidators();

    /**
     * Sets the transformers that can be referenced in the routes.
     *
     * @param transformers the transformers
     */
    void setTransformers(List<TransformerDefinition> transformers);

    /**
     * Gets the transformers that can be referenced in the routes.
     *
     * @return the transformers available
     */
    List<TransformerDefinition> getTransformers();

    /**
     * Gets the service call configuration by the given name. If no name is
     * given the default configuration is returned, see
     * <tt>setServiceCallConfiguration</tt>
     *
     * @param serviceName name of service, or <tt>null</tt> to return the
     *            default configuration
     * @return the configuration, or <tt>null</tt> if no configuration has been
     *         registered
     */
    ServiceCallConfigurationDefinition getServiceCallConfiguration(String serviceName);

    /**
     * Sets the default service call configuration
     *
     * @param configuration the configuration
     */
    void setServiceCallConfiguration(ServiceCallConfigurationDefinition configuration);

    /**
     * Sets the service call configurations
     *
     * @param configurations the configuration list
     */
    void setServiceCallConfigurations(List<ServiceCallConfigurationDefinition> configurations);

    /**
     * Adds the service call configuration
     *
     * @param serviceName name of the service
     * @param configuration the configuration
     */
    void addServiceCallConfiguration(String serviceName, ServiceCallConfigurationDefinition configuration);

    /**
     * Used for filtering routes routes matching the given pattern, which
     * follows the following rules: - Match by route id - Match by route input
     * endpoint uri The matching is using exact match, by wildcard and regular
     * expression as documented by
     * {@link PatternHelper#matchPattern(String, String)}. For example to only
     * include routes which starts with foo in their route id's, use:
     * include=foo&#42; And to exclude routes which starts from JMS endpoints,
     * use: exclude=jms:&#42; Exclude takes precedence over include.
     *
     * @param include the include pattern
     * @param exclude the exclude pattern
     */
    void setRouteFilterPattern(String include, String exclude);

    /**
     * Sets a custom route filter to use for filtering unwanted routes when
     * routes are added.
     *
     * @param filter the filter
     */
    void setRouteFilter(Function<RouteDefinition, Boolean> filter);

    /**
     * Gets the current route filter
     *
     * @return the filter, or <tt>null</tt> if no custom filter has been
     *         configured.
     */
    Function<RouteDefinition, Boolean> getRouteFilter();

}
