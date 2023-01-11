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
package org.apache.camel.cdi;

import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.main.MainCommandLineSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.camel.cdi.BeanManagerHelper.getReference;

/**
 * Camel CDI boot integration. Allows Camel and CDI to be booted up on the command line as a JVM process.
 */
@Vetoed
public class Main extends MainCommandLineSupport {

    static {
        // Since version 2.3.0.Final and WELD-1915, Weld SE registers a shutdown hook that conflicts
        // with Camel main support. See WELD-2051. The system property above is available starting
        // Weld 2.3.1.Final to deactivate the registration of the shutdown hook.
        System.setProperty(
                "org.jboss.weld.se.shutdownHook",
                System.getProperty("org.jboss.weld.se.shutdownHook", String.valueOf(Boolean.FALSE)));
    }

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static Main instance;

    private boolean startContexts = true;
    private SeContainer cdiContainer;
    private Runnable stopHook;

    public static void main(String... args) throws Exception {
        Main main = new Main();
        instance = main;
        try {
            main.run(args);
        } finally {
            instance = null; // ensure main can be reused even if unlikely
        }
    }

    /**
     * Returns the currently executing instance.
     *
     * @return the current running instance
     */
    public static Main getInstance() {
        return instance;
    }

    public Main setStartContexts(final boolean startContexts) {
        this.startContexts = startContexts;
        return this;
    }

    @Override
    protected ProducerTemplate findOrCreateCamelTemplate() {
        if (getCamelContext() == null) {
            throw new IllegalArgumentException("No CamelContext are available so cannot create a ProducerTemplate!");
        }
        return getCamelContext().createProducerTemplate();
    }

    @Override
    protected CamelContext createCamelContext() {
        BeanManager manager = cdiContainer.getBeanManager();
        Map<String, CamelContext> camels = manager.getBeans(CamelContext.class, Any.Literal.INSTANCE).stream()
                .map(bean -> getReference(manager, CamelContext.class, bean))
                .collect(toMap(CamelContext::getName, identity()));
        if (camels.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple CamelContext detected. This Main class only supports single CamelContext");
        } else if (camels.size() == 1) {
            return camels.values().iterator().next();
        }
        return null;
    }

    @Override
    protected void doStart() throws Exception {
        final var container = SeContainerInitializer.newInstance();
        cdiContainer = container.initialize();
        startContexts();
        super.doStart();
        initCamelContext();
        warnIfNoCamelFound();
    }

    @Override
    protected void initCamelContext() throws Exception {
        // camel-cdi has already initialized and start CamelContext so we should not do this again
    }

    protected void startContexts() {
        if (!startContexts) {
            LOG.debug("Context are not automatically started");
            return;
        }
        try {
            final var requestContextController = cdiContainer.select(RequestContextController.class).get();
            if (requestContextController.activate()) {
                LOG.debug("Request context started");
                stopHook = requestContextController::deactivate;
            } else {
                LOG.debug("Request context already started");
            }
        } catch (final UnsatisfiedResolutionException e) {
            // ignore, start without starting the contexts, will not impact much camel normally
            LOG.debug("Didn't start request scope", e);
        }
    }

    private void warnIfNoCamelFound() {
        BeanManager manager = cdiContainer.getBeanManager();
        Set<Bean<?>> contexts = manager.getBeans(CamelContext.class, Any.Literal.INSTANCE);
        // Warn if there is no CDI Camel contexts
        if (contexts.isEmpty()) {
            LOG.warn("Camel CDI main has started with no Camel context!");
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (stopHook != null) {
            stopHook.run();
        }
        if (cdiContainer != null) {
            cdiContainer.close();
        }
    }
}
