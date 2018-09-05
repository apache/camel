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
package org.apache.camel.reifier;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.processor.WrapProcessor;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.TransactedPolicy;
import org.apache.camel.support.CamelContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.model.TransactedDefinition.PROPAGATION_REQUIRED;

public class TransactedReifier extends ProcessorReifier<TransactedDefinition> {

    private static final Logger LOG = LoggerFactory.getLogger(TransactedReifier.class);

    TransactedReifier(ProcessorDefinition<?> definition) {
        super((TransactedDefinition) definition);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Policy policy = resolvePolicy(routeContext);
        org.apache.camel.util.ObjectHelper.notNull(policy, "policy", this);

        // before wrap
        policy.beforeWrap(routeContext, definition);

        // create processor after the before wrap
        Processor childProcessor = this.createChildProcessor(routeContext, true);

        // wrap
        Processor target = policy.wrap(routeContext, childProcessor);

        if (!(target instanceof Service)) {
            // wrap the target so it becomes a service and we can manage its lifecycle
            target = new WrapProcessor(target, childProcessor);
        }
        return target;
    }

    protected Policy resolvePolicy(RouteContext routeContext) {
        return resolvePolicy(routeContext, definition);
    }

    public static Policy resolvePolicy(RouteContext routeContext, TransactedDefinition definition) {
        if (definition.getPolicy() != null) {
            return definition.getPolicy();
        }
        return resolvePolicy(routeContext, definition.getRef(), definition.getType());
    }

    public static Policy resolvePolicy(RouteContext routeContext, String ref, Class<? extends Policy> type) {
        // explicit ref given so lookup by it
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(ref)) {
            return CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), ref, Policy.class);
        }

        // no explicit reference given from user so we can use some convention over configuration here

        // try to lookup by scoped type
        Policy answer = null;
        if (type != null) {
            // try find by type, note that this method is not supported by all registry
            Map<String, ?> types = routeContext.lookupByType(type);
            if (types.size() == 1) {
                // only one policy defined so use it
                Object found = types.values().iterator().next();
                if (type.isInstance(found)) {
                    return type.cast(found);
                }
            }
        }

        // for transacted routing try the default REQUIRED name
        if (type == TransactedPolicy.class) {
            // still not found try with the default name PROPAGATION_REQUIRED
            answer = routeContext.lookup(PROPAGATION_REQUIRED, TransactedPolicy.class);
        }

        // this logic only applies if we are a transacted policy
        // still no policy found then try lookup the platform transaction manager and use it as policy
        if (answer == null && type == TransactedPolicy.class) {
            Class<?> tmClazz = routeContext.getCamelContext().getClassResolver().resolveClass("org.springframework.transaction.PlatformTransactionManager");
            if (tmClazz != null) {
                // see if we can find the platform transaction manager in the registry
                Map<String, ?> maps = routeContext.lookupByType(tmClazz);
                if (maps.size() == 1) {
                    // only one platform manager then use it as default and create a transacted
                    // policy with it and default to required

                    // as we do not want dependency on spring jars in the camel-core we use
                    // reflection to lookup classes and create new objects and call methods
                    // as this is only done during route building it does not matter that we
                    // use reflection as performance is no a concern during route building
                    Object transactionManager = maps.values().iterator().next();
                    LOG.debug("One instance of PlatformTransactionManager found in registry: {}", transactionManager);
                    Class<?> txClazz = routeContext.getCamelContext().getClassResolver().resolveClass("org.apache.camel.spring.spi.SpringTransactionPolicy");
                    if (txClazz != null) {
                        LOG.debug("Creating a new temporary SpringTransactionPolicy using the PlatformTransactionManager: {}", transactionManager);
                        TransactedPolicy txPolicy = org.apache.camel.support.ObjectHelper.newInstance(txClazz, TransactedPolicy.class);
                        Method method;
                        try {
                            method = txClazz.getMethod("setTransactionManager", tmClazz);
                        } catch (NoSuchMethodException e) {
                            throw new RuntimeCamelException("Cannot get method setTransactionManager(PlatformTransactionManager) on class: " + txClazz);
                        }
                        org.apache.camel.support.ObjectHelper.invokeMethod(method, txPolicy, transactionManager);
                        return txPolicy;
                    } else {
                        // camel-spring is missing on the classpath
                        throw new RuntimeCamelException("Cannot create a transacted policy as camel-spring.jar is not on the classpath!");
                    }
                } else {
                    if (maps.isEmpty()) {
                        throw new NoSuchBeanException(null, "PlatformTransactionManager");
                    } else {
                        throw new IllegalArgumentException("Found " + maps.size() + " PlatformTransactionManager in registry. "
                                + "Cannot determine which one to use. Please configure a TransactionTemplate on the transacted policy.");
                    }
                }
            }
        }

        return answer;
    }
}
