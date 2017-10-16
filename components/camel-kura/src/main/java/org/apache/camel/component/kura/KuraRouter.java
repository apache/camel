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
package org.apache.camel.component.kura;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.core.osgi.OsgiDefaultCamelContext;
import org.apache.camel.model.RoutesDefinition;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class KuraRouter extends RouteBuilder implements BundleActivator {

    // Member collaborators

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected BundleContext bundleContext;

    protected CamelContext camelContext;

    protected ProducerTemplate producerTemplate;

    protected ConsumerTemplate consumerTemplate;

    // Lifecycle

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        try {
            this.bundleContext = bundleContext;
            log.debug("Initializing bundle {}.", bundleContext.getBundle().getBundleId());
            camelContext = createCamelContext();

            camelContext.addRoutes(this);
            ConfigurationAdmin configurationAdmin = requiredService(ConfigurationAdmin.class);
            Configuration camelKuraConfig = configurationAdmin.getConfiguration(camelXmlRoutesPid());
            if (camelKuraConfig != null && camelKuraConfig.getProperties() != null) {
                Object routePropertyValue = camelKuraConfig.getProperties().get(camelXmlRoutesProperty());
                if (routePropertyValue != null) {
                    InputStream routesXml = new ByteArrayInputStream(routePropertyValue.toString().getBytes());
                    RoutesDefinition loadedRoutes = camelContext.loadRoutesDefinition(routesXml);
                    camelContext.addRouteDefinitions(loadedRoutes.getRoutes());
                }
            }

            beforeStart(camelContext);
            log.debug("About to start Camel Kura router: {}", getClass().getName());
            camelContext.start();
            producerTemplate = camelContext.createProducerTemplate();
            consumerTemplate = camelContext.createConsumerTemplate();
            log.debug("Bundle {} started.", bundleContext.getBundle().getBundleId());
        } catch (Throwable e) {
            String errorMessage = "Problem when starting Kura module " + getClass().getName() + ":";
            log.warn(errorMessage, e);

            // Print error to the Kura console.
            System.err.println(errorMessage);
            e.printStackTrace();

            throw e;
        }
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        log.debug("Stopping bundle {}.", bundleContext.getBundle().getBundleId());
        camelContext.stop();
        log.debug("Bundle {} stopped.", bundleContext.getBundle().getBundleId());
    }

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) throws Exception {
        start(componentContext.getBundleContext());
    }

    protected void deactivate(ComponentContext componentContext) throws Exception {
        stop(componentContext.getBundleContext());
    }

    // Callbacks

    @Override
    public void configure() throws Exception {
        log.debug("No programmatic routes configuration found.");
    }

    protected CamelContext createCamelContext() {
        return new OsgiDefaultCamelContext(bundleContext);
    }

    protected void beforeStart(CamelContext camelContext) {
        log.debug("Empty KuraRouter CamelContext before start configuration - skipping.");
    }

    // API Helpers

    protected <T> T service(Class<T> serviceType) {
        ServiceReference reference = bundleContext.getServiceReference(serviceType.getName());
        return reference == null ? null : (T) bundleContext.getService(reference);
    }

    protected <T> T requiredService(Class<T> serviceType) {
        ServiceReference reference = bundleContext.getServiceReference(serviceType.getName());
        if (reference == null) {
            throw new IllegalStateException("Cannot find service: " + serviceType.getName());
        }
        return (T) bundleContext.getService(reference);
    }

    // Private helpers

    protected String camelXmlRoutesPid() {
        return "kura.camel";
    }

    protected String camelXmlRoutesProperty() {
        return "kura.camel." + bundleContext.getBundle().getSymbolicName() + ".route";
    }

}