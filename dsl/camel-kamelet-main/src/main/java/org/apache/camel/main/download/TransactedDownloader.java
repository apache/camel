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

import java.util.Arrays;

import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.main.KameletMain;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.reifier.ProcessReifier;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.spi.TransactedPolicy;

/**
 * When using transacted then we need to download the runtime implementation
 */
public class TransactedDownloader {

    private static final String[] TRANSACTED_POLICIES = new String[] {
            "PROPAGATION_REQUIRED",
            "PROPAGATION_REQUIRES_NEW",
            "PROPAGATION_SUPPORTS",
            "PROPAGATION_NOT_SUPPORTED",
            "PROPAGATION_NEVER",
            "PROPAGATION_MANDATORY",
            "PROPAGATION_NESTED"
    };

    private TransactedDownloader() {
    }

    public static void registerDownloadReifiers(KameletMain main) {
        ProcessorReifier.registerReifier(TransactedDefinition.class,
                (route, processorDefinition) -> {
                    if (processorDefinition instanceof TransactedDefinition) {
                        DependencyDownloader downloader = route.getCamelContext().hasService(DependencyDownloader.class);
                        if (downloader != null) {
                            downloader.downloadDependency("org.apache.camel", "camel-jta",
                                    route.getCamelContext().getVersion());
                            TransactedPolicy policy = new DummyTransactedPolicy();
                            Arrays.stream(TRANSACTED_POLICIES).forEach(p -> main.bind(p, policy));
                        }
                    }
                    return ProcessReifier.coreReifier(route, processorDefinition);
                });
    }

    private static class DummyTransactedPolicy implements TransactedPolicy {

        @Override
        public void beforeWrap(final Route route, final NamedNode definition) {
        }

        @Override
        public Processor wrap(final Route route, final Processor processor) {
            return null;
        }
    }
}
