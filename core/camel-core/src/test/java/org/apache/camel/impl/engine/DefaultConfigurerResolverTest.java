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

import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultConfigurerResolverTest {

    private DefaultConfigurerResolver resolver;
    private DefaultCamelContext ctx;

    @BeforeEach
    public void setup() {
        resolver = new DefaultConfigurerResolver();
        ctx = new DefaultCamelContext();
    }

    @DisplayName("Test that the configurer uses the ContextConfigurer wrapper for the CamelContext and subclasses")
    @Test
    void resolvePropertyConfigurerShouldFallbackToDefaultConfigurer() {
        Stream.of(CamelContext.class.getName(), SimpleCamelContext.class.getName(), "org.apache.camel.model.ModelCamelContext",
                "org.apache.camel.SomeCamelContextStuff")
                .forEach(name -> assertThat(resolver.resolvePropertyConfigurer(name, ctx))
                        .as(name).isInstanceOf(DefaultConfigurerResolver.ContextConfigurer.class));

        Stream.of(DefaultCamelContextExtension.class.getName(), "org.apache.camel.SomeCamelContextStuffExtension")
                .forEach(name -> assertThat(resolver.resolvePropertyConfigurer(name, ctx))
                        .as(name).isNull());
    }

    // Note, this might change when we fully decouple the extension from the context
    @DisplayName("Test that the configurer defaults to null if given the extension (ContextConfiguration must be done via Context configurers)")
    @Test
    void resolvePropertyConfigurerForContextExtension() {
        resolver = new DefaultConfigurerResolver();
        DefaultCamelContext ctx = new DefaultCamelContext();

        Stream.of(DefaultCamelContextExtension.class.getName(), "org.apache.camel.SomeCamelContextStuffExtension")
                .forEach(name -> assertThat(resolver.resolvePropertyConfigurer(name, ctx))
                        .as(name).isNull());
    }

    @DisplayName("Test that the configurer returns null for classes named similarly, but unrelated to CamelContext")
    @Test
    void resolvePropertyConfigurerShouldFallbackToExtendedCamelContextOnlyForCamelComponents3() {
        Stream.of(
                "CamelContext", "SimpleCamelContext",
                "org.somepackage.CamelContext", "it.apache.camel.ExtendedCamelContext")
                .forEach(name -> assertThat(resolver.resolvePropertyConfigurer(name, ctx)).as(name).isNull());

    }

}
