/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cdi.internal;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Producer;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cdi.CdiCamelContext;

/**
 * Configuration options to be applied to a {@link CamelContext} by a {@link CamelContextBean}
 */
public class CamelContextConfig {
    private final List<Bean<?>> routeBuilderBeans = new ArrayList<Bean<?>>();

    public void addRouteBuilderBean(Bean<?> bean) {
        routeBuilderBeans.add(bean);
    }

    public void configure(CdiCamelContext camelContext, BeanManager beanManager) {
        for (Bean<?> bean : routeBuilderBeans) {
            CreationalContext<?> createContext = beanManager.createCreationalContext(bean);
            RouteBuilder routeBuilder = (RouteBuilder)beanManager.getReference(bean, RouteBuilder.class, createContext);
            try {
                camelContext.addRoutes(routeBuilder);
            } catch (Exception e) {
                throw new RuntimeCamelException("Could not add route builder " + routeBuilder + ". Reason: " + e, e);
            }
        }
    }
}
