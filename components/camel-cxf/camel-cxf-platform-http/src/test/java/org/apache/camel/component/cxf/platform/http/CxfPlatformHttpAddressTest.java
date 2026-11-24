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
package org.apache.camel.component.cxf.platform.http;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CxfPlatformHttpAddressTest {

    @Test
    void fullUrlAddressOfAConsumerFailsAtStartup() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("cxf:http://0.0.0.0:9000/orders?" + CxfPlatformHttpTestSupport.SERVICE_CLASS)
                            .log("${body}");
                }
            });

            assertThatThrownBy(context::start)
                    .rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be a path, such as cxf:/orders")
                    .hasMessageContaining("http://0.0.0.0:9000/orders")
                    .hasMessageContaining("/services");
        }
    }

    @Test
    void withoutPlatformHttpServerTheConsumerFailsAtStartup() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("cxf:/orders?" + CxfPlatformHttpTestSupport.SERVICE_CLASS).log("${body}");
                }
            });

            assertThatThrownBy(context::start)
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("camel.server.enabled=true");
        }
    }

    @Test
    void normalizePath() {
        assertThat(CxfPlatformHttpTransport.normalizePath(null)).isEmpty();
        assertThat(CxfPlatformHttpTransport.normalizePath("/")).isEmpty();
        assertThat(CxfPlatformHttpTransport.normalizePath("services")).isEqualTo("/services");
        assertThat(CxfPlatformHttpTransport.normalizePath("/soap/ws/")).isEqualTo("/soap/ws");
        assertThat(CxfPlatformHttpTransport.servicePath("orders")).isEqualTo("/orders");
        assertThat(CxfPlatformHttpTransport.servicePath("http://localhost:9000/orders")).isNull();
    }
}
