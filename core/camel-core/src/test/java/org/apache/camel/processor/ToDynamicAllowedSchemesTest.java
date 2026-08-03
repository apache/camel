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
package org.apache.camel.processor;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The optional {@code allowedSchemes} allow-list on {@code toD} restricts which component schemes a dynamic recipient
 * may resolve to. A recipient whose scheme is not in the list is rejected, independently of
 * {@code ignoreInvalidEndpoint}. See CAMEL-24298.
 */
class ToDynamicAllowedSchemesTest extends ContextTestSupport {

    @Test
    void allowedSchemeIsSent() throws Exception {
        getMockEndpoint("mock:allowed").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello", "target", "mock:allowed");

        assertMockEndpointsSatisfied();
    }

    @Test
    void disallowedSchemeIsRejected() {
        assertThatThrownBy(() -> template.sendBodyAndHeader("direct:start", "Hello", "target", "seda:blocked"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(ResolveEndpointFailedException.class)
                .hasMessageContaining("not in the allowed schemes");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").toD().allowedSchemes("mock").uri("${header.target}");
            }
        };
    }
}
