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

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.reifier.ProcessReifier;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When using transacted then we need to download the runtime implementation
 */
public class TransactedDownloader {

    private static final Logger LOG = LoggerFactory.getLogger(TransactedDownloader.class);

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

    public static void registerDownloadReifiers() {
        // the reifiers are static (for the JVM), so they refer to the CamelContext of the route only, not to the
        // KameletMain that registers them, which would otherwise stay referenced when it is done
        ProcessorReifier.registerReifier(TransactedDefinition.class,
                (route, processorDefinition) -> {
                    if (processorDefinition instanceof TransactedDefinition transacted) {
                        DependencyDownloader downloader = route.getCamelContext().hasService(DependencyDownloader.class);
                        if (downloader != null) {
                            downloader.downloadDependency("org.apache.camel", "camel-jta",
                                    route.getCamelContext().getVersion());
                            if (bindPassThroughPolicies(route.getCamelContext(), transacted)) {
                                LOG.warn("Route {} uses transacted, but there is no transaction manager: the route runs"
                                         + " without a transaction. Declare a transaction manager bean, or export to"
                                         + " Spring Boot or Quarkus, for transactions.",
                                        route.getRouteId());
                            }
                        }
                    }
                    return ProcessReifier.coreReifier(route, processorDefinition);
                });
    }

    /**
     * Binds a policy that runs the steps without a transaction under the PROPAGATION_* names that have no bean, when
     * the transacted has no policy or reference of its own and the registry has no transaction manager.
     *
     * @return whether the steps run without a transaction
     */
    static boolean bindPassThroughPolicies(CamelContext context, TransactedDefinition transacted) {
        if (transacted.getPolicy() != null || transacted.getRef() != null || hasTransactionManager(context)) {
            return false;
        }
        Registry registry = context.getRegistry();
        TransactedPolicy policy = new PassThroughTransactedPolicy();
        for (String name : TRANSACTED_POLICIES) {
            // a policy of the user's own under that name is used as it is
            if (registry.lookupByName(name) == null) {
                registry.bind(name, policy);
            }
        }
        return true;
    }

    /**
     * Whether the registry has a transacted policy of the user's own, or a Spring PlatformTransactionManager, of which
     * Camel makes the policy.
     */
    private static boolean hasTransactionManager(CamelContext context) {
        Registry registry = context.getRegistry();
        for (TransactedPolicy policy : registry.findByType(TransactedPolicy.class)) {
            if (!(policy instanceof PassThroughTransactedPolicy)) {
                return true;
            }
        }
        // not with the class resolver of camel run, which downloads camel-spring to load the class (and camel export
        // would then add it): a transaction manager bean of the user's own has loaded the class already
        Class<?> tm = ObjectHelper.loadClass("org.springframework.transaction.PlatformTransactionManager",
                context.getApplicationContextClassLoader());
        return tm != null && !registry.findByType(tm).isEmpty();
    }

    /**
     * Runs the steps without a transaction. CAMEL-26850: it returned null before, and a route with transacted then had
     * no processor, so every message was gone without a trace.
     */
    private static final class PassThroughTransactedPolicy implements TransactedPolicy {

        @Override
        public void beforeWrap(final Route route, final NamedNode definition) {
        }

        @Override
        public Processor wrap(final Route route, final Processor processor) {
            return processor;
        }
    }
}
