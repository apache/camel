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
package org.apache.camel.cdi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates one or more resources representing <a href="http://camel.apache.org/http://camel.apache.org/xml-configuration.html">Camel XML configuration</a>
 * to import. Resources are currently loaded from the classpath.<p>
 *
 * {@code CamelContext} elements and other Camel primitives are automatically
 * deployed as CDI beans during the container bootstrap so that they become
 * available for injection at runtime. If such an element has an explicit
 * {@code id} attribute set, the corresponding CDI bean is qualified with the
 * {@code @Named} qualifier, e.g., given the following Camel XML configuration:
 *
 * <pre>{@code
 * <camelContext id="foo">
 *     <endpoint id="bar" uri="seda:inbound">
 *         <property key="queue" value="#queue"/>
 *         <property key="concurrentConsumers" value="10"/>
 *     </endpoint>
 * <camelContext/>
 * }</pre>
 *
 * Corresponding CDI beans are automatically deployed and can be injected, e.g.:
 *
 * <pre><code>
 * {@literal @}Inject
 * {@literal @}ContextName("foo")
 *  CamelContext context;
 * </code></pre>
 *
 * <pre><code>
 * {@literal @}Inject
 * {@literal @}Named("bar")
 *  Endpoint endpoint;
 * </code></pre>
 *
 * Note that {@code CamelContext} beans are automatically qualified with both
 * the {@code Named} and {@code ContextName} qualifiers. If the imported
 * {@code CamelContext} element doesn't have an {@code id} attribute, the
 * corresponding bean is deployed with the built-in {@code Default} qualifier.<p>
 *
 * Conversely, CDI beans deployed in the application can be referred to from
 * the Camel XML configuration, usually using the {@code ref} attribute, e.g.,
 * given the following bean declared:
 *
 * <pre><code>
 * {@literal @}Produces
 * {@literal @}Named("baz")
 *  Processor processor = exchange{@literal ->} exchange.getIn().setHeader("qux", "quux");
 * </code></pre>
 *
 * A reference to that bean can be declared in the imported Camel XML configuration,
 * e.g.:
 *
 * <pre>{@code
 * <camelContext id="foo">
 *     <route>
 *         <from uri="..."/>
 *         <process ref="baz"/>
 *     </route>
 * <camelContext/>
 * }</pre>
 *
 * @since 2.18.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ImportResource {

    /**
     * Resource locations from which to import Camel XML configuration.
     *
     * @return the locations of the resources to import
     */
    String[] value() default {};
}
