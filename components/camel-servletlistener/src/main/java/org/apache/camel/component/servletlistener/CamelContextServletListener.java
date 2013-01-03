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
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
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

    // TODO: Allow to lookup and configure some of the stuff we do in camel-core-xml
    // more easily with this listener as well
    // TODO: Add more tests
    // TODO: Allow to lookup route builders using package scanning

    public static ServletCamelContext instance;

    private static final Logger LOG = LoggerFactory.getLogger(CamelContextServletListener.class);
    private JndiContext jndiContext;
    private ServletCamelContext camelContext;
    private CamelContextLifecycle camelContextLifecycle;
    private boolean test;

    @Override
    @SuppressWarnings("unchecked")
    public void contextInitialized(ServletContextEvent sce) {
        LOG.info("CamelContextServletListener initializing ...");

        // create jndi and camel context
        try {
            jndiContext = new JndiContext();
            camelContext = new ServletCamelContext(jndiContext, sce.getServletContext());
        } catch (Exception e) {
            LOG.error("Error creating CamelContext.", e);
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
        if (!map.isEmpty()) {
            try {
                IntrospectionSupport.setProperties(camelContext, map);
            } catch (Exception e) {
                throw new RuntimeException("Error setting init parameters on CamelContext.", e);
            }
        }

        // get the routes and add to the CamelContext
        Map<String, Object> routes = extractRoutes(map);
        for (Map.Entry<String, Object> entry : routes.entrySet()) {
            if (entry.getValue() instanceof RouteBuilder) {
                LOG.debug("Adding route(s) {} -> {}", entry.getKey(), entry.getValue());
                try {
                    camelContext.addRoutes((RoutesBuilder) entry.getValue());
                } catch (Exception e) {
                    throw new RuntimeException("Error adding route(s) " + entry.getKey(), e);
                }
            } else if (entry.getValue() instanceof RoutesDefinition) {
                LOG.debug("Adding routes {} -> {}", entry.getKey(), entry.getValue());
                try {
                    camelContext.addRouteDefinitions(((RoutesDefinition) entry.getValue()).getRoutes());
                } catch (Exception e) {
                    throw new RuntimeException("Error adding route(s) " + entry.getKey(), e);
                }
            } else if (entry.getValue() instanceof RouteDefinition) {
                LOG.debug("Adding routes {} -> {}", entry.getKey(), entry.getValue());
                try {
                    camelContext.addRouteDefinition((RouteDefinition) entry.getValue());
                } catch (Exception e) {
                    throw new RuntimeException("Error adding route(s) " + entry.getKey(), e);
                }
            } else {
                throw new IllegalArgumentException("Unsupported route " + entry.getKey() + " of type: " + entry.getValue().getClass().getName());
            }
        }

        // any custom CamelContextLifecycle
        String lifecycle = (String) map.remove(CamelContextLifecycle.class.getName());
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

    private Map<String, Object> extractRoutes(Map<String, Object> map) {
        Map<String, Object> routes = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.UK).startsWith("routebuilder")) {
                String value = (String) entry.getValue();
                if (ObjectHelper.isNotEmpty(value)) {
                    Object target = null;
                    if (value.startsWith("#")) {
                        // a reference lookup in jndi
                        value = value.substring(1);
                        target = lookupJndi(jndiContext, value);
                    } else if (ResourceHelper.hasScheme(value)) {
                        InputStream is = null;
                        try {
                            is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext.getClassResolver(), value);
                            target = camelContext.loadRoutesDefinition(is);
                        } catch (Exception e) {
                            throw new RuntimeException("Error loading routes from resource: " + value, e);
                        } finally {
                            IOHelper.close(is, entry.getKey(), LOG);
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
                        routes.put(entry.getKey(), target);
                    }
                }
            }
        }

        // after adding the route builders we should remove them from the map
        for (String name : routes.keySet()) {
            map.remove(name);
        }

        return routes;
    }

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

    private static Object lookupJndi(JndiContext jndiContext, String name) {
        try {
            return jndiContext.lookup(name);
        } catch (NamingException e) {
            throw new RuntimeException("Error looking up in jndi with name: " + name, e);
        }
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

}
