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
package org.apache.camel.main.download;

import org.apache.camel.Processor;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.support.PluginHelper;

/**
 * When using circuit breakers then we need to download the runtime implementation
 */
public final class CircuitBreakerDownloader {

    private CircuitBreakerDownloader() {
    }

    public static void registerDownloadReifiers() {
        ProcessorReifier.registerReifier(CircuitBreakerDefinition.class,
                (route, processorDefinition) -> {
                    if (processorDefinition instanceof CircuitBreakerDefinition) {
                        CircuitBreakerDefinition cb = (CircuitBreakerDefinition) processorDefinition;
                        DependencyDownloader downloader = route.getCamelContext().hasService(DependencyDownloader.class);
                        if (downloader != null) {
                            if (cb.getResilience4jConfiguration() != null) {
                                downloader.downloadDependency("org.apache.camel", "camel-resilience4j",
                                        route.getCamelContext().getVersion());
                            }
                            if (cb.getFaultToleranceConfiguration() != null) {
                                downloader.downloadDependency("org.apache.camel", "camel-microprofile-fault-tolerance",
                                        route.getCamelContext().getVersion());
                            }
                            if (cb.getConfiguration() != null) {
                                String id = cb.getConfiguration();
                                Object cfg = ((ModelCamelContext) route.getCamelContext()).getResilience4jConfiguration(id);
                                if (cfg != null) {
                                    downloader.downloadDependency("org.apache.camel", "camel-resilience4j",
                                            route.getCamelContext().getVersion());
                                }
                                cfg = ((ModelCamelContext) route.getCamelContext()).getFaultToleranceConfiguration(id);
                                if (cfg != null) {
                                    downloader.downloadDependency("org.apache.camel", "camel-microprofile-fault-tolerance",
                                            route.getCamelContext().getVersion());
                                }
                            } else {
                                // use resilience4j as default
                                downloader.downloadDependency("org.apache.camel", "camel-resilience4j",
                                        route.getCamelContext().getVersion());
                            }
                        }
                        // circuit breakers uses a processor factory (which supports using downloaded JARs)
                        return new ProcessorReifier<>(route, cb) {
                            @Override
                            public Processor createProcessor() throws Exception {
                                final ProcessorFactory processorFactory
                                        = PluginHelper.getProcessorFactory(getCamelContext());
                                if (processorFactory != null) {
                                    return processorFactory.createProcessor(route, cb);
                                }
                                return null;
                            }
                        };
                    }
                    return null;
                });
    }

}
