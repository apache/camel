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
package org.apache.camel.reifier;

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Policy;
import org.apache.camel.spi.TransactedPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.model.TransactedDefinition.PROPAGATION_REQUIRED;

public abstract class AbstractPolicyReifier<T extends ProcessorDefinition<?>> extends ProcessorReifier<T> {

    private static final Logger LOG = LoggerFactory.getLogger(TransactedReifier.class);

    public AbstractPolicyReifier(Route route, T definition) {
        super(route, definition);
    }

    public AbstractPolicyReifier(CamelContext camelContext, T definition) {
        super(camelContext, definition);
    }

    public Policy resolvePolicy(Policy policy, String ref, Class<? extends Policy> type) {
        if (policy != null) {
            return policy;
        }
        // explicit ref given so lookup by it
        if (org.apache.camel.util.ObjectHelper.isNotEmpty(ref)) {
            return mandatoryLookup(ref, Policy.class);
        }

        // no explicit reference given from user so we can use some convention
        // over configuration here

        // try to lookup by scoped type
        Policy answer = null;
        if (type != null) {
            // try find by type, note that this method is not supported by all
            // registry
            Map<String, ?> types = findByTypeWithName(type);
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
            answer = lookup(PROPAGATION_REQUIRED, TransactedPolicy.class);
        }

        // this logic only applies if we are a transacted policy
        // still no policy found then try lookup the platform transaction
        // manager and use it as policy
        if (answer == null && type == TransactedPolicy.class) {
            Class<?> tmClazz = camelContext.getClassResolver().resolveClass("org.springframework.transaction.PlatformTransactionManager");
            if (tmClazz != null) {
                // see if we can find the platform transaction manager in the
                // registry
                Map<String, ?> maps = findByTypeWithName(tmClazz);
                if (maps.size() == 1) {
                    // only one platform manager then use it as default and
                    // create a transacted
                    // policy with it and default to required

                    // as we do not want dependency on spring jars in the
                    // camel-core we use
                    // reflection to lookup classes and create new objects and
                    // call methods
                    // as this is only done during route building it does not
                    // matter that we
                    // use reflection as performance is no a concern during
                    // route building
                    Object transactionManager = maps.values().iterator().next();
                    LOG.debug("One instance of PlatformTransactionManager found in registry: {}", transactionManager);
                    Class<?> txClazz = camelContext.getClassResolver().resolveClass("org.apache.camel.spring.spi.SpringTransactionPolicy");
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
