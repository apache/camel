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

import org.apache.camel.main.KameletMain;
import org.apache.camel.model.SagaDefinition;
import org.apache.camel.reifier.ProcessReifier;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.saga.InMemorySagaService;

/**
 * When using saga then we need to register a CamelSagaService
 */
public class SagaDownloader {

    private SagaDownloader() {
    }

    public static void registerDownloadReifiers(KameletMain main) {
        ProcessorReifier.registerReifier(SagaDefinition.class,
                (route, processorDefinition) -> {
                    if (processorDefinition instanceof SagaDefinition) {
                        DependencyDownloader downloader = route.getCamelContext().hasService(DependencyDownloader.class);
                        if (downloader != null) {
                            downloader.downloadDependency("org.apache.camel", "camel-saga",
                                    route.getCamelContext().getVersion());
                            downloader.downloadDependency("org.apache.camel", "camel-lra",
                                    route.getCamelContext().getVersion());
                        }
                    }
                    main.bind("inMemorySagaService", new InMemorySagaService());
                    return ProcessReifier.coreReifier(route, processorDefinition);
                });
    }
}
