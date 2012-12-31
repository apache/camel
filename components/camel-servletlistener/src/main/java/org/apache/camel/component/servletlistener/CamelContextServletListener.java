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

import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jndi.JndiContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ServletContextListener} which is used to bootstrap
 * {@link org.apache.camel.CamelContext} in web applications.
 */
public class CamelContextServletListener implements ServletContextListener {

    // TODO: Refactor the init method into smaller methods
    // TODO: Allow to lookup and configure some of the stuff we do in camel-core-xml
    // more easily with this listener as well
    // TODO: Add more tests
    // TODO: Lookup jndi and have expected type, and fail if not matching type
    // TODO: Consider CamelContextLifecycle as a SPI in camel-core to make it easier for
    // everyone to do some custom code when Camel starts/stops. Though you can use an EventListener
    // but maybe this SPI is even easier and more common to use

    public static ServletCamelContext instance;

    private static final Logger LOG = LoggerFactory.getLogger(CamelContextServletListener.class);
    private JndiContext jndiContext;
    private ServletCamelContext camelContext;
    private CamelContextLifecycle camelContextLifecycle;
    private boolean test;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOG.info("CamelContextServletListener initializing ...");

        try {
            jndiContext = new JndiContext();
            camelContext = new ServletCamelContext(jndiContext, sce.getServletContext());
        } catch (Exception e) {
            LOG.error("Error creating CamelContext.", e);
            throw new RuntimeException("Error creating CamelContext.", e);
        }

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

        // special for test parameter
        String test = (String) map.remove("test");
        if (test != null && "true".equalsIgnoreCase(test)) {
            this.test = true;
        }
        LOG.trace("In test mode? {}", this.test);

        if (!map.isEmpty()) {
            try {
                IntrospectionSupport.setProperties(camelContext, map);
            } catch (Exception e) {
                throw new RuntimeException("Error setting init parameters on CamelContext.", e);
            }
        }

        Set<String> routeBuilders = new HashSet<String>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.UK).startsWith("routebuilder")) {
                routeBuilders.add(entry.getKey());
                String value = (String) entry.getValue();
                if (ObjectHelper.isNotEmpty(value)) {
                    Object target;
                    if (value.startsWith("#")) {
                        // a reference lookup in jndi
                        value = value.substring(1);
                        target = lookupJndi(jndiContext, value);
                    } else {
                        try {
                            Class<RouteBuilder> clazz = camelContext.getClassResolver().resolveMandatoryClass(value, RouteBuilder.class);
                            target = camelContext.getInjector().newInstance(clazz);
                        } catch (Exception e) {
                            throw new RuntimeException("Error creating RouteBuilder " + value, e);
                        }
                    }
                    if (target instanceof RouteBuilder) {
                        LOG.debug("Adding RouteBuilder {} -> {}", entry.getKey(), target);
                        try {
                            camelContext.addRoutes((RoutesBuilder) target);
                        } catch (Exception e) {
                            throw new RuntimeException("Error adding RouteBuilder " + value, e);
                        }
                    }
                }
            }
        }

        // after adding the route builders we should remove them from the map
        for (String name : routeBuilders) {
            map.remove(name);
        }

        // validate that we could set all the init parameters
        if (!map.isEmpty()) {
            throw new IllegalArgumentException("Error setting init parameters on CamelContext."
                    + " There are " + map.size() + " unknown parameters. [" + map + "]");
        }

        // Any custom CamelContextLifecycle
        String lifecycle = (String) map.remove(CamelContextLifecycle.class.getName());
        if (lifecycle != null) {
            try {
                Class<CamelContextLifecycle> clazz = camelContext.getClassResolver().resolveMandatoryClass(lifecycle, CamelContextLifecycle.class);
                camelContextLifecycle = camelContext.getInjector().newInstance(clazz);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Error creating CamelContextLifecycle class with name " + lifecycle, e);
            }
        }

        try {
            if (camelContextLifecycle != null) {
                camelContextLifecycle.preStart(camelContext, jndiContext);
            }
            camelContext.start();
            if (camelContextLifecycle != null) {
                camelContextLifecycle.postStart(camelContext, jndiContext);
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
                    camelContextLifecycle.preStop(camelContext, jndiContext);
                }
                camelContext.stop();
                if (camelContextLifecycle != null) {
                    camelContextLifecycle.postStop(camelContext, jndiContext);
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
