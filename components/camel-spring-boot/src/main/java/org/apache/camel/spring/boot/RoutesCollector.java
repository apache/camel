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
package org.apache.camel.spring.boot;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Ordered;
import org.apache.camel.RoutesBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.PriorityOrdered;

public class RoutesCollector implements BeanPostProcessor, PriorityOrdered {

    private static final Logger LOG = LoggerFactory.getLogger(RoutesCollector.class);

    // Collaborators

    private final ApplicationContext applicationContext;

    private final List<CamelContextConfiguration> camelContextConfigurations;

    // Constructors

    public RoutesCollector(ApplicationContext applicationContext, List<CamelContextConfiguration> camelContextConfigurations) {
        this.applicationContext = applicationContext;
        this.camelContextConfigurations = camelContextConfigurations;
    }

    // Overridden

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof CamelContext && beanName.equals("camelContext")) {
            CamelContext camelContext = (CamelContext) bean;
            LOG.debug("Post-processing CamelContext bean: {}", camelContext.getName());
            for (RoutesBuilder routesBuilder : applicationContext.getBeansOfType(RoutesBuilder.class).values()) {
                try {
                    LOG.debug("Injecting following route into the CamelContext: {}", routesBuilder);
                    camelContext.addRoutes(routesBuilder);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            if (camelContextConfigurations != null) {
                for (CamelContextConfiguration camelContextConfiguration : camelContextConfigurations) {
                    LOG.debug("CamelContextConfiguration found. Invoking: {}", camelContextConfiguration);
                    camelContextConfiguration.beforeApplicationStart(camelContext);
                }
            }
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST;
    }

}