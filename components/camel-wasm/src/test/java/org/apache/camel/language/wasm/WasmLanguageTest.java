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
package org.apache.camel.language.wasm;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.StringHelper;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

public class WasmLanguageTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "transform@functions.wasm",
            "transform@file:{{wasm.resources.path}}/functions.wasm"
    })
    public void testLanguage(String expression) throws Exception {
        try (CamelContext cc = new DefaultCamelContext()) {

            FluentProducerTemplate pt = cc.createFluentProducerTemplate();

            cc.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:in")
                            .transform()
                            .wasm(
                                    StringHelper.before(expression, "@"),
                                    StringHelper.after(expression, "@"));
                }
            });
            cc.start();

            Exchange out = pt.to("direct:in")
                    .withHeader("foo", "bar")
                    .withBody("hello")
                    .request(Exchange.class);

            assertThat(out.getMessage().getHeaders())
                    .containsEntry("foo", "bar");
            assertThat(out.getMessage().getBody(String.class))
                    .isEqualTo("HELLO");

        }
    }

    @Test
    public void testLanguageFailure() throws Exception {
        try (CamelContext cc = new DefaultCamelContext()) {

            FluentProducerTemplate pt = cc.createFluentProducerTemplate();

            cc.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:in")
                            .transform()
                            .wasm("transform_err", "functions.wasm");
                }
            });
            cc.start();

            Exchange out = pt.to("direct:in")
                    .withHeader("foo", "bar")
                    .withBody("hello")
                    .request(Exchange.class);

            assertThat(out.getException())
                    .isNotNull()
                    .hasCauseInstanceOf(RuntimeException.class)
                    .extracting(Throwable::getCause, as(InstanceOfAssertFactories.THROWABLE))
                    .hasMessage("this is an error");
        }
    }
}
