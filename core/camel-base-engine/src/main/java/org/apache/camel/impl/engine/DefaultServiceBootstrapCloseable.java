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
package org.apache.camel.impl.engine;

import java.util.Set;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Service;
import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.spi.ConfigurerStrategy;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.support.PluginHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link BootstrapCloseable} which will collect all registered {@link Service} which is
 * {@link BootstrapCloseable} and run their task and remove the service from {@link CamelContext}.
 */
public class DefaultServiceBootstrapCloseable implements BootstrapCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultServiceBootstrapCloseable.class);

    private final CamelContext camelContext;
    private final ExtendedCamelContext camelContextExtension;

    public DefaultServiceBootstrapCloseable(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.camelContextExtension = camelContext.getCamelContextExtension();
    }

    @Override
    public void close() {
        // clear bootstrap configurers
        ConfigurerStrategy.clearBootstrapConfigurers();

        Set<Service> set
                = camelContextExtension.getServices().stream().filter(s -> s instanceof BootstrapCloseable)
                        .collect(Collectors.toSet());
        // its a bootstrap service
        for (Service service : set) {
            try {
                if (service instanceof BootstrapCloseable) {
                    ((BootstrapCloseable) service).close();
                }
                // service is no longer needed as it was only intended during bootstrap
                camelContext.removeService(service);
            } catch (Exception e) {
                LOG.warn("Error during closing bootstrap service. This exception is ignored", e);
            }
        }

        // clear bootstrap configurer resolver
        ConfigurerResolver cr = PluginHelper.getBootstrapConfigurerResolver(camelContextExtension);
        if (cr instanceof BootstrapCloseable) {
            try {
                ((BootstrapCloseable) cr).close();
            } catch (Exception e) {
                LOG.warn("Error during closing bootstrap service. This exception is ignored", e);
            }
        }

        // clear processor factory
        ProcessorFactory pf = PluginHelper.getProcessorFactory(camelContextExtension);
        if (pf instanceof BootstrapCloseable) {
            try {
                ((BootstrapCloseable) pf).close();
            } catch (Exception e) {
                LOG.warn("Error during closing bootstrap service. This exception is ignored", e);
            }
        }

        // clear bootstrap factory finder
        FactoryFinder ff = camelContextExtension.getBootstrapFactoryFinder();
        if (ff instanceof BootstrapCloseable) {
            try {
                ((BootstrapCloseable) ff).close();
            } catch (Exception e) {
                LOG.warn("Error during closing bootstrap service. This exception is ignored", e);
            }
        }
        camelContextExtension.setBootstrapFactoryFinder(null);
    }

}
