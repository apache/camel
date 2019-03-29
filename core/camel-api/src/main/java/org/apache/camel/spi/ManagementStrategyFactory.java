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
package org.apache.camel.spi;

import java.util.Map;

import org.apache.camel.CamelContext;

/**
 * Service Factory for ManagementStrategy
 */
public interface ManagementStrategyFactory {

    /**
     * Creates the {@link ManagementStrategy}.
     *
     * @param context     the camel context
     * @param properties  optional options to set on {@link ManagementAgent}
     * @return the created strategy
     * @throws Exception is thrown if error creating the strategy
     */
    ManagementStrategy create(CamelContext context, Map<String, Object> properties) throws Exception;

    /**
     * Creates the associated {@link LifecycleStrategy} that the management strategy uses.
     *
     * @param context     the camel context
     * @return the created lifecycle strategy
     * @throws Exception is thrown if error creating the lifecycle strategy
     */
    LifecycleStrategy createLifecycle(CamelContext context) throws Exception;

    /**
     * Setup the management on the {@link CamelContext}.
     * <p/>
     * This allows implementations to provide the logic for setting up management on Camel.
     *
     * @param camelContext  the camel context
     * @param strategy      the management strategy
     * @param lifecycle      the associated lifecycle strategy (optional)
     */
    void setupManagement(CamelContext camelContext, ManagementStrategy strategy, LifecycleStrategy lifecycle);

}
