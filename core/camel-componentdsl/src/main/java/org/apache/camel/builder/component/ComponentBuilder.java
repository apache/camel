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
package org.apache.camel.builder.component;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;

/**
 * Type-safe component DSL for building Camel components
 */
public interface ComponentBuilder <C extends Component> {
    /**
     * Builds a component using the default {@link CamelContext}.
     *
     * @return a built {@link Component}
     */
    C build();

    /**
     * Builds a component using a provided {@link CamelContext}.
     *
     * @param context the camel context
     * @return a built {@link Component}
     */
    C build(CamelContext context);

    /**
     * Builds and register a component to the provided {@link CamelContext}.
     *
     * @param context the camel context
     * @param componentName the name the component is registered as
     */
    void register(CamelContext context, String componentName);

    /**
     * Adds an option to this component. This API is only intended for Camel
     * internally.
     */
    void doSetProperty(String name, Object value);
}
