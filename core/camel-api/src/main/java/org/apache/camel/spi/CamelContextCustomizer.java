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
import org.apache.camel.Ordered;

/**
 * Strategy for applying custom configuration to a {@link CamelContext} after it has been created.
 * <p/>
 * Customizers are discovered from the {@link Registry} and invoked during bootstrap, giving a hook to programmatically
 * tune the context (adding services, configuring options, registering beans) without subclassing. Multiple customizers
 * run in {@link Ordered} order. This is most commonly used by runtimes such as Spring Boot and Quarkus to apply
 * user-provided configuration. The sibling SPIs {@link ComponentCustomizer}, {@link DataFormatCustomizer}, and
 * {@link LanguageCustomizer} customize individual components, data formats, and languages.
 * <p/>
 * See <a href="https://camel.apache.org/manual/camelcontext-autoconfigure.html">CamelContext auto-configuration</a> in
 * the Camel user manual.
 *
 * @see   ComponentCustomizer
 * @see   DataFormatCustomizer
 * @see   LanguageCustomizer
 * @since 3.6
 */
@FunctionalInterface
public interface CamelContextCustomizer extends Ordered, Comparable<CamelContextCustomizer> {

    /**
     * Configure the {@link CamelContext}.
     *
     * @param camelContext the camel context to configure.
     */
    void configure(CamelContext camelContext);

    @Override
    default int getOrder() {
        return 0;
    }

    @Override
    default int compareTo(CamelContextCustomizer other) {
        return Integer.compare(getOrder(), other.getOrder());
    }
}
