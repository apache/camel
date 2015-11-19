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
package org.apache.camel.cdi.internal;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.cdi.CdiCamelContext;
import org.apache.camel.model.RouteContainer;
import org.apache.camel.util.ObjectHelper;

/**
 * Configuration options to be applied to a {@link org.apache.camel.CamelContext} by a {@link CamelContextBean}
 */
public class CamelContextConfig {
    // use a set to avoid duplicates
    private final Set<Bean<?>> routeBuilderBeans = new LinkedHashSet<Bean<?>>();
    private final Set<AnnotatedType<?>> patRouteBuilders = new LinkedHashSet<AnnotatedType<?>>();

    public void addRouteBuilderBean(Bean<?> bean) {
        routeBuilderBeans.add(bean);
    }

    public void configure(CdiCamelContext camelContext, BeanManager beanManager) {
        for (AnnotatedType<?> pat : patRouteBuilders) {
            final Set<Bean<?>> beans = beanManager.getBeans(pat.getJavaClass());
            final Bean<?> bean = beanManager.resolve(beans);
            routeBuilderBeans.add(bean);
        }
        patRouteBuilders.clear();

        for (Bean<?> bean : routeBuilderBeans) {
            CreationalContext<?> createContext = beanManager.createCreationalContext(bean);
            Class<?> beanClass = bean.getBeanClass();
            Set<Type> types = bean.getTypes();
            for (Type type : types) {
                // lets use the first type for producer methods
                if (type instanceof Class<?>) {
                    beanClass = (Class<?>) type;
                    break;
                }
            }
            Object reference = beanManager.getReference(bean, beanClass, createContext);
            ObjectHelper.notNull(reference, "Could not instantiate bean of type: " + beanClass.getName() + " for " + bean);
            try {
                // we should not toString reference instance as in CDI it may be proxied
                if (reference instanceof RoutesBuilder) {
                    RoutesBuilder routeBuilder = (RoutesBuilder)reference;
                    camelContext.addRoutes(routeBuilder);
                } else if (reference instanceof RouteContainer) {
                    RouteContainer routeContainer = (RouteContainer)reference;
                    camelContext.addRouteDefinitions(routeContainer.getRoutes());
                } else {
                    throw new IllegalArgumentException("Invalid route builder of type: " + beanClass.getName()
                            + ". Should be RoutesBuilder or RoutesContainer");
                }
            } catch (Exception e) {
                throw new RuntimeCamelException("Error adding route builder of type: " + beanClass.getName()
                        + " to CamelContext: " + camelContext.getName() + " due " + e.getMessage(), e);
            }
        }
    }

    public void addRouteBuilderBean(final AnnotatedType<?> process) {
        patRouteBuilders.add(process);
    }
}
