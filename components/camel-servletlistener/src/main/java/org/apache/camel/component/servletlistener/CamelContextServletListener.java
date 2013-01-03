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
import javax.naming.NamingException;
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
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.apache.camel.util.jndi.JndiContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ServletContextListener} which is used to bootstrap
 * {@link org.apache.camel.CamelContext} in web applications.
 */
public class CamelContextServletListener implements ServletContextListener {

    /**
     * instance is used for testing purpose
     */
    public static ServletCamelContext instance;

    private static final Logger LOG = LoggerFactory.getLogger(CamelContextServletListener.class);
    private JndiContext jndiContext;
    private ServletCamelContext camelContext;
    private CamelContextLifecycle camelContextLifecycle;
    private boolean test;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOG.info("CamelContextServletListener initializing ...");

        // create jndi and camel context
        try {
            jndiContext = new JndiContext();
            camelContext = new ServletCamelContext(jndiContext, sce.getServletContext());
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
                for (Object clazz : (Set) route) {
                    try {
                        camelContext.addRoutes((RoutesBuilder) clazz);
                    } catch (Exception e) {
                        throw new RuntimeException("Error adding route " + clazz, e);
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

        // any custom CamelContextLifecycle
        String lifecycle = (String) map.remove("CamelContextLifecycle");
        if (lifecycle != null) {
            try {
                Class<CamelContextLifecycle> clazz = camelContext.getClassResolver().resolveMandatoryClass(lifecycle, CamelContextLifecycle.class);
                camelContextLifecycle = camelContext.getInjector().newInstance(clazz);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Error creating CamelContextLifecycle class with name " + lifecycle, e);
            }
        }

        // validate that we could set all the init parameters
        if (!map.isEmpty()) {
            throw new IllegalArgumentException("Error setting init parameters on CamelContext."
                    + " There are " + map.size() + " unknown parameters. [" + map + "]");
        }

        try {
            if (camelContextLifecycle != null) {
                camelContextLifecycle.beforeStart(camelContext, jndiContext);
            }
            camelContext.start();
            if (camelContextLifecycle != null) {
                camelContextLifecycle.afterStart(camelContext, jndiContext);
            }
        } catch (Exception e) {
            LOG.error("Error starting CamelContext.", e);
            throw new RuntimeException("Error starting CamelContext.", e);
        }

        if (this.test) {
            instance = camelContext;
        }

        LOG.info("CamelContextServletListener initialized");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOG.info("CamelContextServletListener destroying ...");
        if (camelContext != null) {
            try {
                if (camelContextLifecycle != null) {
                    camelContextLifecycle.beforeStop(camelContext, jndiContext);
                }
                camelContext.stop();
                if (camelContextLifecycle != null) {
                    camelContextLifecycle.afterStop(camelContext, jndiContext);
                }
            } catch (Exception e) {
                LOG.warn("Error stopping CamelContext. This exception will be ignored.", e);
            }
        }
        camelContext = null;
        jndiContext = null;
        instance = null;
        LOG.info("CamelContextServletListener destroyed");
    }

    /**
     * Extracts all the init parameters, and will do reference lookup in {@link JndiContext}
     * if the value starts with a # sign.
     */
    private Map<String, Object> extractInitParameters(ServletContextEvent sce) {
        // configure CamelContext with the init parameter
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        Enumeration names = sce.getServletContext().getInitParameterNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            String value = sce.getServletContext().getInitParameter(name);

            if (ObjectHelper.isNotEmpty(value)) {
                Object target = value;
                if (value.startsWith("#")) {
                    // a reference lookup in jndi
                    value = value.substring(1);
                    target = lookupJndi(jndiContext, value);
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

                // clear the existing lifecycle strategies define by the DefaultCamelContext constructor
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
     * @param map  parameters
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
                Iterator it = ObjectHelper.createIterator(value);
                while (it.hasNext()) {
                    value = (String) it.next();
                    if (ObjectHelper.isNotEmpty(value)) {
                        // trim value before usage, as people can indent the values
                        value = value.trim();
                        Object target = null;

                        if (value.startsWith("#")) {
                            // a reference lookup in jndi
                            value = value.substring(1);
                            target = lookupJndi(jndiContext, value);
                        } else if (ResourceHelper.hasScheme(value)) {
                            // XML resource from classpath or file system
                            InputStream is = null;
                            try {
                                is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext.getClassResolver(), value);
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

    private static Object lookupJndi(JndiContext jndiContext, String name) {
        try {
            return jndiContext.lookup(name);
        } catch (NamingException e) {
            throw new RuntimeException("Error looking up in jndi with name: " + name, e);
        }
    }

}
