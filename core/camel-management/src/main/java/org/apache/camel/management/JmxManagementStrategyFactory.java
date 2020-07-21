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
package org.apache.camel.management;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.ManagementStrategyFactory;
import org.apache.camel.support.PropertyBindingSupport;

/**
 * Factory for creating JMX {@link ManagementStrategy}.
 */
public class JmxManagementStrategyFactory implements ManagementStrategyFactory {

    @Override
    public ManagementStrategy create(CamelContext context, Map<String, Object> options) throws Exception {
        DefaultManagementAgent agent = new DefaultManagementAgent(context);
        if (options != null) {
            PropertyBindingSupport.build().bind(context, agent, options);
        }

        return new JmxManagementStrategy(context, agent);
    }

    @Override
    public LifecycleStrategy createLifecycle(CamelContext context) throws Exception {
        return new JmxManagementLifecycleStrategy(context);
    }

    @Override
    public void setupManagement(CamelContext camelContext, ManagementStrategy strategy, LifecycleStrategy lifecycle) {
        camelContext.setManagementStrategy(strategy);
        // must add management lifecycle strategy as first choice
        if (!camelContext.getLifecycleStrategies().isEmpty()) {

            // a bit of ugly code to handover pre registered services that has been add to an eager/provisional JmxManagementLifecycleStrategy
            // which is now re-placed with a new JmxManagementLifecycleStrategy that is based on the end user configured settings
            // and therefore will be in use
            List<java.util.function.Consumer<JmxManagementLifecycleStrategy>> preServices = null;
            JmxManagementLifecycleStrategy jmx = camelContext.getLifecycleStrategies().stream()
                    .filter(s -> s instanceof JmxManagementLifecycleStrategy)
                    .map(JmxManagementLifecycleStrategy.class::cast)
                    .findFirst().orElse(null);
            if (jmx != null) {
                preServices = jmx.getPreServices();
            }

            if (preServices != null &&  !preServices.isEmpty() && lifecycle instanceof JmxManagementLifecycleStrategy) {
                JmxManagementLifecycleStrategy existing = (JmxManagementLifecycleStrategy) lifecycle;
                for (java.util.function.Consumer<JmxManagementLifecycleStrategy> pre : preServices) {
                    existing.addPreService(pre);
                }
            }

            // camel-spring/camel-blueprint may re-initialize JMX during startup, so remove any previous
            camelContext.getLifecycleStrategies().removeIf(s -> s instanceof JmxManagementLifecycleStrategy);
        }
        camelContext.getLifecycleStrategies().add(0, lifecycle);
    }

}
