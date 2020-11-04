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
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.ExtendedCamelContextConfigurer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultConfigurerResolverTest {

    private DefaultConfigurerResolver resolver;

    @Test
    void resolvePropertyConfigurerShouldFallbackToExtendedCamelContextOnlyForCamelComponents() {
        resolver = new DefaultConfigurerResolver();
        DefaultCamelContext ctx = new DefaultCamelContext();

        Stream.of(CamelContext.class.getName(), ExtendedCamelContext.class.getName(),
                SimpleCamelContext.class.getName(), "org.apache.camel.SomeCamelContextStuff")
                .forEach(name -> assertThat(resolver.resolvePropertyConfigurer(name, ctx))
                        .as(name).isInstanceOf(ExtendedCamelContextConfigurer.class));

        Stream.of(
                "CamelContext", "ExtendedCamelContext", "SimpleCamelContext",
                "org.somepackage.CamelContext", "it.apache.camel.ExtendedCamelContext")
                .forEach(name -> assertThat(resolver.resolvePropertyConfigurer(name, ctx)).as(name).isNull());

    }

}
