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

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.core.osgi.OsgiDefaultCamelContext;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class KuraRouter extends RouteBuilder implements BundleActivator {

    // Member collaborators

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected BundleContext bundleContext;

    protected CamelContext camelContext;

    protected ProducerTemplate producerTemplate;

    // Lifecycle

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        this.bundleContext = bundleContext;
        log.debug("Initializing bundle {}.", bundleContext.getBundle().getBundleId());
        camelContext = createCamelContext();
        camelContext.addRoutes(this);
        beforeStart(camelContext);
        camelContext.start();
        producerTemplate = camelContext.createProducerTemplate();
        log.debug("Bundle {} started.", bundleContext.getBundle().getBundleId());
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        log.debug("Stopping bundle {}.", bundleContext.getBundle().getBundleId());
        camelContext.stop();
        log.debug("Bundle {} stopped.", bundleContext.getBundle().getBundleId());
    }

    // Callbacks

    protected CamelContext createCamelContext() {
        return new OsgiDefaultCamelContext(bundleContext);
    }

    protected void beforeStart(CamelContext camelContext) {
        log.debug("Empty KuraRouter CamelContext before start configuration - skipping.");
    }

    // API Helpers

    protected <T> T service(Class<T> serviceType) {
        ServiceReference reference = bundleContext.getServiceReference(serviceType);
        return (T) bundleContext.getService(reference);
    }

}