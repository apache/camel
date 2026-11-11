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
package org.apache.camel.component.chaos;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Without stream caching a condition that reads the body would consume a stream body, so it is refused; one on the
 * headers is fine.
 */
class ChaosWhenNoStreamCachingTest extends ChaosTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.setStreamCaching(false);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders").to("mock:stock");
            }
        };
    }

    @Test
    void aConditionOnTheBodyIsRefused() {
        assertThatThrownBy(() -> chaos().addRule("endpoint=mock:stock error when=\"${body} contains 'boom'\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith("when=${body} contains 'boom' reads the body, which consumes a stream body when"
                                      + " stream caching is off (it is on by default)");
        assertThat(chaos().addRule("endpoint=mock:stock error when=\"${header.country} == 'ES'\"")).isEqualTo("r1");
    }
}
