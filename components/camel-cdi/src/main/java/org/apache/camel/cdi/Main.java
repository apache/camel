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

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.main.MainCommandLineSupport;
import org.apache.deltaspike.cdise.api.CdiContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.camel.cdi.AnyLiteral.ANY;
import static org.apache.camel.cdi.BeanManagerHelper.getReference;
import static org.apache.deltaspike.cdise.api.CdiContainerLoader.getCdiContainer;

/**
 * Camel CDI boot integration. Allows Camel and CDI to be booted up on the command line as a JVM process.
 * See http://camel.apache.org/camel-boot.html.
 */
@Vetoed
public class Main extends MainCommandLineSupport {

    static {
        // Since version 2.3.0.Final and WELD-1915, Weld SE registers a shutdown hook that conflicts
        // with Camel main support. See WELD-2051. The system property above is available starting
        // Weld 2.3.1.Final to deactivate the registration of the shutdown hook.
        System.setProperty("org.jboss.weld.se.shutdownHook", String.valueOf(Boolean.FALSE));
    }

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static Main instance;

    private CdiContainer cdiContainer;

    public static void main(String... args) throws Exception {
        Main main = new Main();
        instance = main;
        main.run(args);
    }

    /**
     * Returns the currently executing instance.
     *
     * @return the current running instance
     */
    public static Main getInstance() {
        return instance;
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
        Map<String, CamelContext> camels = manager.getBeans(CamelContext.class, ANY).stream()
            .map(bean -> getReference(manager, CamelContext.class, bean))
            .collect(toMap(CamelContext::getName, identity()));
        if (camels.size() > 1) {
            throw new IllegalArgumentException("Multiple CamelContext detected. This Main class only supports single CamelContext");
        } else if (camels.size() == 1) {
            return camels.values().iterator().next();
        }
        return null;
    }

    @Override
    protected void doStart() throws Exception {
        // TODO: Use standard CDI Java SE support when CDI 2.0 becomes a prerequisite
        CdiContainer container = getCdiContainer();
        container.boot();
        container.getContextControl().startContexts();
        cdiContainer = container;
        super.doStart();
        initCamelContext();
        warnIfNoCamelFound();
    }

    @Override
    protected void initCamelContext() throws Exception {
        // camel-cdi has already initialized and start CamelContext so we should not do this again
    }

    private void warnIfNoCamelFound() {
        BeanManager manager = cdiContainer.getBeanManager();
        Set<Bean<?>> contexts = manager.getBeans(CamelContext.class, ANY);
        // Warn if there is no CDI Camel contexts
        if (contexts.isEmpty()) {
            LOG.warn("Camel CDI main has started with no Camel context!");
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (cdiContainer != null) {
            cdiContainer.shutdown();
        }
    }
}
