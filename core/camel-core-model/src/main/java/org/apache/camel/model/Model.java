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
import org.apache.camel.RouteTemplateContext;
import org.apache.camel.model.app.RegistryBeanDefinition;
import org.apache.camel.model.cloud.ServiceCallConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.spi.ModelReifierFactory;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Model interface
 */
public interface Model {

    /**
     * Adds the given model lifecycle strategy to be used.
     *
     * @param modelLifecycleStrategy the strategy
     */
    void addModelLifecycleStrategy(ModelLifecycleStrategy modelLifecycleStrategy);

    /**
     * Returns the model lifecycle strategies used to handle lifecycle notifications
     *
     * @return the lifecycle strategies
     */
    List<ModelLifecycleStrategy> getModelLifecycleStrategies();

    /**
     * Adds a collection of route configuration definitions to the context
     *
     * @param routesConfigurations the route configuration(s) definition to add
     */
    void addRouteConfigurations(List<RouteConfigurationDefinition> routesConfigurations);

    /**
     * Adds a single route configuration definition to the context
     *
     * @param routesConfiguration the route configuration to add
     */
    void addRouteConfiguration(RouteConfigurationDefinition routesConfiguration);

    /**
     * Returns a list of the current route configuration definitions
     *
     * @return list of the current route configuration definitions
     */
    List<RouteConfigurationDefinition> getRouteConfigurationDefinitions();

    /**
     * Removes a route configuration from the context
     *
     * @param  routeConfigurationDefinition route configuration to remove
     * @throws Exception                    if the route configuration could not be removed for whatever reason
     */
    void removeRouteConfiguration(RouteConfigurationDefinition routeConfigurationDefinition) throws Exception;

    /**
     * Gets the route configuration definition with the given id
     *
     * @param  id id of the route configuration
     * @return    the route configuration definition or <tt>null</tt> if not found
     */
    RouteConfigurationDefinition getRouteConfigurationDefinition(String id);

    /**
     * Returns a list of the current route definitions
     *
     * @return list of the current route definitions
     */
    List<RouteDefinition> getRouteDefinitions();

    /**
     * Gets the route definition with the given id
     *
     * @param  id id of the route
     * @return    the route definition or <tt>null</tt> if not found
     */
    RouteDefinition getRouteDefinition(String id);

    /**
     * Adds a collection of route definitions to the context
     * <p/>
     * <b>Important: </b> Each route in the same {@link CamelContext} must have an <b>unique</b> route id. If you use
     * the API from {@link CamelContext} or {@link Model} to add routes, then any new routes which has a route id that
     * matches an old route, then the old route is replaced by the new route.
     *
     * @param  routeDefinitions the route(s) definition to add
     * @throws Exception        if the route definitions could not be added for whatever reason
     */
    void addRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception;

    /**
     * Add a route definition to the context
     * <p/>
     * <b>Important: </b> Each route in the same {@link CamelContext} must have an <b>unique</b> route id. If you use
     * the API from {@link CamelContext} or {@link Model} to add routes, then any new routes which has a route id that
     * matches an old route, then the old route is replaced by the new route.
     *
     * @param  routeDefinition the route definition to add
     * @throws Exception       if the route definition could not be added for whatever reason
     */
    void addRouteDefinition(RouteDefinition routeDefinition) throws Exception;

    /**
     * Removes a collection of route definitions from the context - stopping any previously running routes if any of
     * them are actively running
     *
     * @param  routeDefinitions route(s) definitions to remove
     * @throws Exception        if the route definitions could not be removed for whatever reason
     */
    void removeRouteDefinitions(Collection<RouteDefinition> routeDefinitions) throws Exception;

    /**
     * Removes a route definition from the context - stopping any previously running routes if any of them are actively
     * running
     *
     * @param  routeDefinition route definition to remove
     * @throws Exception       if the route definition could not be removed for whatever reason
     */
    void removeRouteDefinition(RouteDefinition routeDefinition) throws Exception;

    /**
     * Returns a list of the current route template definitions
     *
     * @return list of the current route template definitions
     */
    List<RouteTemplateDefinition> getRouteTemplateDefinitions();

    /**
     * Gets the route template definition with the given id
     *
     * @param  id id of the route template
     * @return    the route template definition or <tt>null</tt> if not found
     */
    RouteTemplateDefinition getRouteTemplateDefinition(String id);

    /**
     * Adds a collection of route template definitions to the context
     * <p/>
     * <b>Important: </b> Each route in the same {@link CamelContext} must have an <b>unique</b> route template id.
     *
     * @param  routeTemplateDefinitions the route template(s) definition to add
     * @throws Exception                if the route template definitions could not be added for whatever reason
     */
    void addRouteTemplateDefinitions(Collection<RouteTemplateDefinition> routeTemplateDefinitions) throws Exception;

    /**
     * Add a route definition to the context
     * <p/>
     * <b>Important: </b> Each route template in the same {@link CamelContext} must have an <b>unique</b> route id.
     *
     * @param  routeTemplateDefinition the route template definition to add
     * @throws Exception               if the route template definition could not be added for whatever reason
     */
    void addRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) throws Exception;

    /**
     * Removes a collection of route template definitions from the context
     *
     * @param  routeTemplateDefinitions route template(s) definitions to remove
     * @throws Exception                if the route template definitions could not be removed for whatever reason
     */
    void removeRouteTemplateDefinitions(Collection<RouteTemplateDefinition> routeTemplateDefinitions) throws Exception;

    /**
     * Removes a route template definition from the context
     *
     * @param  routeTemplateDefinition route template definition to remove
     * @throws Exception               if the route template definition could not be removed for whatever reason
     */
    void removeRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition) throws Exception;

    /**
     * Removes the route templates matching the pattern - stopping any previously running routes if any of them are
     * actively running
     *
     * @param pattern pattern, such as * for all, or foo* to remove all foo templates
     */
    void removeRouteTemplateDefinitions(String pattern) throws Exception;

    /**
     * Add a converter to translate a {@link RouteTemplateDefinition} to a {@link RouteDefinition}.
     *
     * @param templateIdPattern the route template ut to whom a pattern should eb applied
     * @param converter         the {@link RouteTemplateDefinition.Converter} used to convert a
     *                          {@link RouteTemplateDefinition} to a {@link RouteDefinition}
     */
    void addRouteTemplateDefinitionConverter(String templateIdPattern, RouteTemplateDefinition.Converter converter);

    /**
     * Adds a new route from a given route template
     *
     * @param  routeId         the id of the new route to add (optional)
     * @param  routeTemplateId the id of the route template (mandatory)
     * @param  parameters      parameters to use for the route template when creating the new route
     * @return                 the id of the route added (for example when an id was auto assigned)
     * @throws Exception       is thrown if error creating and adding the new route
     */
    String addRouteFromTemplate(String routeId, String routeTemplateId, Map<String, Object> parameters) throws Exception;

    /**
     * Adds a new route from a given route template
     *
     * @param  routeId         the id of the new route to add (optional)
     * @param  routeTemplateId the id of the route template (mandatory)
     * @param  prefixId        prefix to use when assigning route and node IDs (optional)
     * @param  parameters      parameters to use for the route template when creating the new route
     * @return                 the id of the route added (for example when an id was auto assigned)
     * @throws Exception       is thrown if error creating and adding the new route
     */
    String addRouteFromTemplate(
            String routeId, String routeTemplateId, String prefixId,
            Map<String, Object> parameters)
            throws Exception;

    /**
     * Adds a new route from a given route template
     *
     * @param  routeId              the id of the new route to add (optional)
     * @param  routeTemplateId      the id of the route template (mandatory)
     * @param  prefixId             prefix to use when assigning route and node IDs (optional)
     * @param  routeTemplateContext the route template context (mandatory)
     * @return                      the id of the route added (for example when an id was auto assigned)
     * @throws Exception            is thrown if error creating and adding the new route
     */
    String addRouteFromTemplate(
            String routeId, String routeTemplateId, String prefixId,
            RouteTemplateContext routeTemplateContext)
            throws Exception;

    /**
     * Adds a new route from a given templated route definition
     *
     * @param  templatedRouteDefinition the templated route definition to add as a route (mandatory)
     * @throws Exception                is thrown if error creating and adding the new route
     */
    void addRouteFromTemplatedRoute(TemplatedRouteDefinition templatedRouteDefinition) throws Exception;

    /**
     * Adds new routes from a given templated route definitions
     *
     * @param  templatedRouteDefinitions the templated route definitions to add as a route (mandatory)
     * @throws Exception                 is thrown if error creating and adding the new route
     */
    default void addRouteFromTemplatedRoutes(
            Collection<TemplatedRouteDefinition> templatedRouteDefinitions)
            throws Exception {
        ObjectHelper.notNull(templatedRouteDefinitions, "templatedRouteDefinitions");
        for (TemplatedRouteDefinition templatedRouteDefinition : templatedRouteDefinitions) {
            addRouteFromTemplatedRoute(templatedRouteDefinition);
        }
    }

    /**
     * Returns a list of the current REST definitions
     *
     * @return list of the current REST definitions
     */
    List<RestDefinition> getRestDefinitions();

    /**
     * Adds a collection of rest definitions to the context
     *
     * @param  restDefinitions the rest(s) definition to add
     * @param  addToRoutes     whether the rests should also automatically be added as routes
     * @throws Exception       if the rest definitions could not be created for whatever reason
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
     * @param  name the data format definition name or a reference to it in the {@link org.apache.camel.spi.Registry}
     * @return      the resolved data format definition, or <tt>null</tt> if not found
     */
    DataFormatDefinition resolveDataFormatDefinition(String name);

    /**
     * Gets the processor definition from any of the routes which with the given id
     *
     * @param  id id of the processor definition
     * @return    the processor definition or <tt>null</tt> if not found
     */
    ProcessorDefinition<?> getProcessorDefinition(String id);

    /**
     * Gets the processor definition from any of the routes which with the given id
     *
     * @param  id                 id of the processor definition
     * @param  type               the processor definition type
     * @return                    the processor definition or <tt>null</tt> if not found
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
     * Gets the Resilience4j configuration by the given name. If no name is given the default configuration is returned,
     * see <tt>setResilience4jConfiguration</tt>
     *
     * @param  id id of the configuration, or <tt>null</tt> to return the default configuration
     * @return    the configuration, or <tt>null</tt> if no configuration has been registered
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
     * @param id            name of the configuration
     * @param configuration the configuration
     */
    void addResilience4jConfiguration(String id, Resilience4jConfigurationDefinition configuration);

    /**
     * Gets the MicroProfile Fault Tolerance configuration by the given name. If no name is given the default
     * configuration is returned, see <tt>setFaultToleranceConfigurationDefinition</tt>
     *
     * @param  id id of the configuration, or <tt>null</tt> to return the default configuration
     * @return    the configuration, or <tt>null</tt> if no configuration has been registered
     */
    FaultToleranceConfigurationDefinition getFaultToleranceConfiguration(String id);

    /**
     * Sets the default MicroProfile Fault Tolerance configuration
     *
     * @param configuration the configuration
     */
    void setFaultToleranceConfiguration(FaultToleranceConfigurationDefinition configuration);

    /**
     * Sets the MicroProfile Fault Tolerance configurations
     *
     * @param configurations the configuration list
     */
    void setFaultToleranceConfigurations(List<FaultToleranceConfigurationDefinition> configurations);

    /**
     * Adds the MicroProfile Fault Tolerance configuration
     *
     * @param id            name of the configuration
     * @param configuration the configuration
     */
    void addFaultToleranceConfiguration(String id, FaultToleranceConfigurationDefinition configuration);

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
     * Gets the service call configuration by the given name. If no name is given the default configuration is returned,
     * see <tt>setServiceCallConfiguration</tt>
     *
     * @param  serviceName name of service, or <tt>null</tt> to return the default configuration
     * @return             the configuration, or <tt>null</tt> if no configuration has been registered
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
     * @param serviceName   name of the service
     * @param configuration the configuration
     */
    void addServiceCallConfiguration(String serviceName, ServiceCallConfigurationDefinition configuration);

    /**
     * Used for filtering routes routes matching the given pattern, which follows the following rules: - Match by route
     * id - Match by route input endpoint uri The matching is using exact match, by wildcard and regular expression as
     * documented by {@link PatternHelper#matchPattern(String, String)}. For example to only include routes which starts
     * with foo in their route id's, use: include=foo&#42; And to exclude routes which starts from JMS endpoints, use:
     * exclude=jms:&#42; Exclude takes precedence over include.
     *
     * @param include the include pattern
     * @param exclude the exclude pattern
     */
    void setRouteFilterPattern(String include, String exclude);

    /**
     * Sets a custom route filter to use for filtering unwanted routes when routes are added.
     *
     * @param filter the filter
     */
    void setRouteFilter(Function<RouteDefinition, Boolean> filter);

    /**
     * Gets the current route filter
     *
     * @return the filter, or <tt>null</tt> if no custom filter has been configured.
     */
    Function<RouteDefinition, Boolean> getRouteFilter();

    /**
     * Gets the {@link ModelReifierFactory}
     */
    ModelReifierFactory getModelReifierFactory();

    /**
     * Sets a custom {@link ModelReifierFactory}
     */
    void setModelReifierFactory(ModelReifierFactory modelReifierFactory);

    /**
     * Adds the custom bean
     */
    void addRegistryBean(RegistryBeanDefinition bean);

    /**
     * Gets the custom beans
     */
    List<RegistryBeanDefinition> getRegistryBeans();

}
