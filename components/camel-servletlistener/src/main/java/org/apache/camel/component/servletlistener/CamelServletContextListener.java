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
package org.apache.camel.component.servletlistener;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.ErrorHandlerBuilderRef;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.management.DefaultManagementAgent;
import org.apache.camel.management.DefaultManagementLifecycleStrategy;
import org.apache.camel.management.DefaultManagementStrategy;
import org.apache.camel.management.ManagedManagementStrategy;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ServletContextListener} which is used to bootstrap
 * {@link org.apache.camel.CamelContext} in web applications.
 * 
 * @param <R> the type of the {@link Registry} being {@link #createRegistry() created}
 */
public abstract class CamelServletContextListener<R extends Registry> implements ServletContextListener {

    /**
     * instance is used for testing purpose
     */
    public static ServletCamelContext instance;

    /**
     * Key to store the created {@link org.apache.camel.CamelContext} as an attribute on the {@link javax.servlet.ServletContext}.
     */
    public static final String CAMEL_CONTEXT_KEY = "CamelContext";

    protected static final Logger LOG = LoggerFactory.getLogger(CamelServletContextListener.class);
    protected ServletCamelContext camelContext;
    protected CamelContextLifecycle<R> camelContextLifecycle;
    protected boolean test;
    protected R registry;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOG.info("CamelContextServletListener initializing ...");

        // create jndi and camel context
        try {
            registry = createRegistry();
            camelContext = new ServletCamelContext(registry, sce.getServletContext());
        } catch (Exception e) {
            throw new RuntimeException("Error creating CamelContext.", e);
        }

        // get the init parameters
        Map<String, Object> map = extractInitParameters(sce);

        // special for test parameter
        String test = (String) map.remove("test");
        if (test != null && "true".equalsIgnoreCase(test)) {
            this.test = true;
        }
        LOG.trace("In test mode? {}", this.test);

        // set properties on the camel context from the init parameters
        try {
            initPropertyPlaceholder(camelContext, map);
            initJmx(camelContext, map);
            initCamelContext(camelContext, map);
            if (!map.isEmpty()) {
                IntrospectionSupport.setProperties(camelContext, map);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error setting init parameters on CamelContext.", e);
        }

        // any custom CamelContextLifecycle
        String lifecycle = (String) map.remove("CamelContextLifecycle");
        if (lifecycle != null) {
            try {
                Class<CamelContextLifecycle<R>> clazz = CastUtils.cast(camelContext.getClassResolver().resolveMandatoryClass(lifecycle, CamelContextLifecycle.class));
                camelContextLifecycle = camelContext.getInjector().newInstance(clazz);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Error creating CamelContextLifecycle class with name " + lifecycle, e);
            }
        }
        
        try {
            if (camelContextLifecycle != null) {
                camelContextLifecycle.beforeAddRoutes(camelContext, registry);
            }
        } catch (Exception e) {
            LOG.error("Error before adding routes to CamelContext.", e);
            throw new RuntimeException("Error before adding routes to CamelContext.", e);
        }

        // get the routes and add to the CamelContext
        List<Object> routes = extractRoutes(map);
        for (Object route : routes) {
            if (route instanceof RouteBuilder) {
                try {
                    camelContext.addRoutes((RoutesBuilder) route);
                } catch (Exception e) {
                    throw new RuntimeException("Error adding route " + route, e);
                }
            } else if (route instanceof Set) {
                // its a set of route builders
                for (Object routesBuilder : (Set<?>) route) {
                    try {
                        camelContext.addRoutes((RoutesBuilder) routesBuilder);
                    } catch (Exception e) {
                        throw new RuntimeException("Error adding route " + routesBuilder, e);
                    }
                }
            } else if (route instanceof RoutesDefinition) {
                try {
                    camelContext.addRouteDefinitions(((RoutesDefinition) route).getRoutes());
                } catch (Exception e) {
                    throw new RuntimeException("Error adding route(s) " + route, e);
                }
            } else if (route instanceof RouteDefinition) {
                try {
                    camelContext.addRouteDefinition((RouteDefinition) route);
                } catch (Exception e) {
                    throw new RuntimeException("Error adding route(s) " + route, e);
                }
            } else {
                throw new IllegalArgumentException("Unsupported route: " + route);
            }
        }
        
        // just log if we could not use all the parameters, as they may be used by others
        if (!map.isEmpty()) {
            LOG.info("There are {} ServletContext init parameters, unknown to Camel. Maybe they are used by other frameworks? [{}]", map.size(), map);
        }

        try {
            if (camelContextLifecycle != null) {
                camelContextLifecycle.afterAddRoutes(camelContext, registry);
            }
        } catch (Exception e) {
            LOG.error("Error after adding routes to CamelContext.", e);
            throw new RuntimeException("Error after adding routes to CamelContext.", e);
        }

        try {
            if (camelContextLifecycle != null) {
                camelContextLifecycle.beforeStart(camelContext, registry);
            }
            camelContext.start();
            if (camelContextLifecycle != null) {
                camelContextLifecycle.afterStart(camelContext, registry);
            }
        } catch (Exception e) {
            LOG.error("Error starting CamelContext.", e);
            throw new RuntimeException("Error starting CamelContext.", e);
        }

        if (this.test) {
            instance = camelContext;
        }

        // store the CamelContext as an attribute
        sce.getServletContext().setAttribute(CAMEL_CONTEXT_KEY, camelContext);

        LOG.info("CamelContextServletListener initialized");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOG.info("CamelContextServletListener destroying ...");
        if (camelContext != null) {
            try {
                if (camelContextLifecycle != null) {
                    camelContextLifecycle.beforeStop(camelContext, registry);
                }
                camelContext.stop();
                if (camelContextLifecycle != null) {
                    camelContextLifecycle.afterStop(camelContext, registry);
                }
            } catch (Exception e) {
                LOG.warn("Error stopping CamelContext. This exception will be ignored.", e);
            }
        }
        camelContext = null;
        registry = null;
        instance = null;

        // store the CamelContext as an attribute
        sce.getServletContext().removeAttribute(CAMEL_CONTEXT_KEY);

        LOG.info("CamelContextServletListener destroyed");
    }

    /**
     * Creates the {@link Registry} implementation to use.
     */
    protected abstract R createRegistry() throws Exception;

    /**
     * Extracts all the init parameters, and will do reference lookup in {@link #createRegistry() registry}
     * in case the value starts with a {@code #} sign.
     */
    private Map<String, Object> extractInitParameters(ServletContextEvent sce) {
        // configure CamelContext with the init parameter
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        Enumeration<?> names = sce.getServletContext().getInitParameterNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            String value = sce.getServletContext().getInitParameter(name);

            if (ObjectHelper.isNotEmpty(value)) {
                Object target = value;
                if (value.startsWith("#")) {
                    // a reference lookup in registry
                    value = value.substring(1);
                    target = lookupRegistryByName(value);
                    LOG.debug("Resolved the servlet context's initialization parameter {} to {}", value, target);
                }
                map.put(name, target);
            }
        }
        return map;
    }

    /**
     * Initializes the property placeholders by registering the {@link PropertiesComponent} with
     * the configuration from the given init parameters.
     */
    private void initPropertyPlaceholder(ServletCamelContext camelContext, Map<String, Object> parameters) throws Exception {
        // setup property placeholder first
        Map<String, Object> properties = IntrospectionSupport.extractProperties(parameters, "propertyPlaceholder.");
        if (properties != null && !properties.isEmpty()) {
            PropertiesComponent pc = new PropertiesComponent();
            IntrospectionSupport.setProperties(pc, properties);
            // validate we could set all parameters
            if (!properties.isEmpty()) {
                throw new IllegalArgumentException("Error setting propertyPlaceholder parameters on CamelContext."
                        + " There are " + properties.size() + " unknown parameters. [" + properties + "]");
            }
            // register the properties component
            camelContext.addComponent("properties", pc);
        }
    }

    /**
     * Initializes JMX on {@link ServletCamelContext} with the configuration from the given init parameters.
     */
    private void initJmx(ServletCamelContext camelContext, Map<String, Object> parameters) throws Exception {
        // setup jmx
        Map<String, Object> properties = IntrospectionSupport.extractProperties(parameters, "jmx.");
        if (properties != null && !properties.isEmpty()) {
            String disabled = (String) properties.remove("disabled");
            boolean disableJmx = CamelContextHelper.parseBoolean(camelContext, disabled != null ? disabled : "false");
            if (disableJmx) {
                // disable JMX which is a bit special to do
                LOG.info("JMXAgent disabled");
                // clear the existing lifecycle strategies define by the DefaultCamelContext constructor
                camelContext.getLifecycleStrategies().clear();
                // no need to add a lifecycle strategy as we do not need one as JMX is disabled
                camelContext.setManagementStrategy(new DefaultManagementStrategy());
            } else {
                LOG.info("JMXAgent enabled");
                DefaultManagementAgent agent = new DefaultManagementAgent(camelContext);
                IntrospectionSupport.setProperties(agent, properties);

                ManagementStrategy managementStrategy = new ManagedManagementStrategy(camelContext, agent);
                camelContext.setManagementStrategy(managementStrategy);

                // clear the existing lifecycle strategies defined by the DefaultCamelContext constructor
                camelContext.getLifecycleStrategies().clear();
                camelContext.addLifecycleStrategy(new DefaultManagementLifecycleStrategy(camelContext));
                // set additional configuration from agent
                boolean onlyId = agent.getOnlyRegisterProcessorWithCustomId() != null && agent.getOnlyRegisterProcessorWithCustomId();
                camelContext.getManagementStrategy().onlyManageProcessorWithCustomId(onlyId);

                String statisticsLevel = (String) properties.remove("statisticsLevel");
                if (statisticsLevel != null) {
                    camelContext.getManagementStrategy().setStatisticsLevel(ManagementStatisticsLevel.valueOf(statisticsLevel));
                }

                String loadStatisticsEnabled = (String) properties.remove("loadStatisticsEnabled");
                Boolean statisticsEnabled = CamelContextHelper.parseBoolean(camelContext, loadStatisticsEnabled != null ? loadStatisticsEnabled : "true");
                if (statisticsEnabled != null) {
                    camelContext.getManagementStrategy().setLoadStatisticsEnabled(statisticsEnabled);
                }
            }
            // validate we could set all parameters
            if (!properties.isEmpty()) {
                throw new IllegalArgumentException("Error setting jmx parameters on CamelContext."
                        + " There are " + properties.size() + " unknown parameters. [" + properties + "]");
            }
        }
    }

    /**
     * Initializes the {@link ServletCamelContext} by setting the supported init parameters.
     */
    private void initCamelContext(ServletCamelContext camelContext, Map<String, Object> parameters) throws Exception {
        String messageHistory = (String) parameters.remove("messageHistory");
        if (messageHistory != null) {
            camelContext.setMessageHistory(CamelContextHelper.parseBoolean(camelContext, messageHistory));
        }
        String streamCache = (String) parameters.remove("streamCache");
        if (streamCache != null) {
            camelContext.setStreamCaching(CamelContextHelper.parseBoolean(camelContext, streamCache));
        }
        String trace = (String) parameters.remove("trace");
        if (trace != null) {
            camelContext.setTracing(CamelContextHelper.parseBoolean(camelContext, trace));
        }
        String delayer = (String) parameters.remove("delayer");
        if (delayer != null) {
            camelContext.setDelayer(CamelContextHelper.parseLong(camelContext, delayer));
        }
        String handleFault = (String) parameters.remove("handleFault");
        if (handleFault != null) {
            camelContext.setHandleFault(CamelContextHelper.parseBoolean(camelContext, handleFault));
        }
        String errorHandlerRef = (String) parameters.remove("errorHandlerRef");
        if (errorHandlerRef != null) {
            camelContext.setErrorHandlerBuilder(new ErrorHandlerBuilderRef(errorHandlerRef));
        }
        String autoStartup = (String) parameters.remove("autoStartup");
        if (autoStartup != null) {
            camelContext.setAutoStartup(CamelContextHelper.parseBoolean(camelContext, autoStartup));
        }
        String useMDCLogging = (String) parameters.remove("useMDCLogging");
        if (useMDCLogging != null) {
            camelContext.setUseMDCLogging(CamelContextHelper.parseBoolean(camelContext, useMDCLogging));
        }
        String useBreadcrumb = (String) parameters.remove("useBreadcrumb");
        if (useBreadcrumb != null) {
            camelContext.setUseBreadcrumb(CamelContextHelper.parseBoolean(camelContext, useBreadcrumb));
        }
        String managementNamePattern = (String) parameters.remove("managementNamePattern");
        if (managementNamePattern != null) {
            camelContext.getManagementNameStrategy().setNamePattern(managementNamePattern);
        }
        String threadNamePattern = (String) parameters.remove("threadNamePattern");
        if (threadNamePattern != null) {
            camelContext.getExecutorServiceManager().setThreadNamePattern(threadNamePattern);
        }

        // extract any additional properties. prefixes
        Map<String, Object> properties = IntrospectionSupport.extractProperties(parameters, "properties.");
        if (properties != null && !properties.isEmpty()) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                camelContext.getProperties().put(entry.getKey(), "" + entry.getValue());
            }
        }
    }

    /**
     * Extract the routes from the parameters.
     *
     * @param map parameters
     * @return a list of routes, which can be of different types. See source code for more details.
     */
    private List<Object> extractRoutes(Map<String, Object> map) {
        List<Object> answer = new ArrayList<Object>();
        List<String> names = new ArrayList<String>();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.UK).startsWith("routebuilder")) {
                names.add(entry.getKey());
                // we can have multiple values assigned, separated by comma, so create an iterator
                String value = (String) entry.getValue();
                Iterator<Object> it = ObjectHelper.createIterator(value);
                while (it.hasNext()) {
                    value = (String) it.next();
                    if (ObjectHelper.isNotEmpty(value)) {
                        // trim value before usage, as people can indent the values
                        value = value.trim();
                        Object target = null;

                        if (value.startsWith("#")) {
                            // a reference lookup in jndi
                            value = value.substring(1);
                            target = lookupRegistryByName(value);
                        } else if (ResourceHelper.hasScheme(value)) {
                            // XML resource from classpath or file system
                            InputStream is = null;
                            try {
                                is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, value);
                                target = camelContext.loadRoutesDefinition(is);
                            } catch (Exception e) {
                                throw new RuntimeException("Error loading routes from resource: " + value, e);
                            } finally {
                                IOHelper.close(is, entry.getKey(), LOG);
                            }
                        } else if (value.startsWith("packagescan:")) {
                            // using package scanning
                            String path = value.substring(12);
                            Set<Class<?>> classes = camelContext.getPackageScanClassResolver().findImplementations(RouteBuilder.class, path);
                            if (!classes.isEmpty()) {
                                Set<RouteBuilder> builders = new LinkedHashSet<RouteBuilder>();
                                target = builders;
                                for (Class<?> clazz : classes) {
                                    try {
                                        RouteBuilder route = (RouteBuilder) camelContext.getInjector().newInstance(clazz);
                                        builders.add(route);
                                    } catch (Exception e) {
                                        throw new RuntimeException("Error creating RouteBuilder " + clazz, e);
                                    }
                                }
                            }
                        } else {
                            // assume its a FQN classname for a RouteBuilder class
                            try {
                                Class<RouteBuilder> clazz = camelContext.getClassResolver().resolveMandatoryClass(value, RouteBuilder.class);
                                target = camelContext.getInjector().newInstance(clazz);
                            } catch (Exception e) {
                                throw new RuntimeException("Error creating RouteBuilder " + value, e);
                            }
                        }
                        if (target != null) {
                            answer.add(target);
                        }
                    }
                }
            }
        }

        // after adding the route builders we should remove them from the map
        for (String name : names) {
            map.remove(name);
        }

        return answer;
    }

    private Object lookupRegistryByName(String name) {
        return registry.lookupByName(name);
    }

}
