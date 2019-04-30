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
package org.apache.camel.impl.engine;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.ManagementStrategyFactory;

/**
 * Factory for creating non JMX {@link ManagementStrategy}.
 */
public class DefaultManagementStrategyFactory implements ManagementStrategyFactory {

    @Override
    public ManagementStrategy create(CamelContext context, Map<String, Object> properties) throws Exception {
        return new DefaultManagementStrategy(context);
    }

    @Override
    public LifecycleStrategy createLifecycle(CamelContext context) throws Exception {
        // not in use for non JMX
        return null;
    }

    @Override
    public void setupManagement(CamelContext camelContext, ManagementStrategy strategy, LifecycleStrategy lifecycle) {
        camelContext.setManagementStrategy(strategy);

        if (!camelContext.getLifecycleStrategies().isEmpty()) {
            // camel-spring/camel-blueprint may re-initialize JMX during startup,
            // so remove any previous JMX initialized lifecycle strategy
            camelContext.getLifecycleStrategies().removeIf(s -> s.getClass().getName().startsWith("org.apache.camel.management"));
        }
    }
}
