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

import org.apache.camel.CamelContext;
import org.jspecify.annotations.Nullable;

/**
 * Pluggable strategy for resolving a {@link PropertyConfigurer} by the name of the target type (for example
 * {@code timer-component} or {@code timer-endpoint}).
 * <p/>
 * The default implementation looks up the class name from a service resource under the path
 * {@link #RESOURCE_PATH}{@code <name>} on the classpath and instantiates it. These resources are generated at build
 * time by the {@code camel-package-maven-plugin} for every class annotated with {@link Configurer}. Camel consults the
 * resolver when configuring a component or endpoint from properties, using the returned {@link PropertyConfigurer} to
 * set options without reflection.
 *
 * @see   PropertyConfigurer
 * @see   Configurer
 * @since 3.1
 */
public interface ConfigurerResolver {

    String RESOURCE_PATH = "META-INF/services/org/apache/camel/configurer/";

    /**
     * Resolves the given configurer.
     *
     * @param  name    the name of the configurer (timer-component or timer-endpoint etc)
     * @param  context the camel context
     * @return         the resolved configurer, or <tt>null</tt> if no configurer could be found
     */
    @Nullable
    PropertyConfigurer resolvePropertyConfigurer(String name, CamelContext context);
}
