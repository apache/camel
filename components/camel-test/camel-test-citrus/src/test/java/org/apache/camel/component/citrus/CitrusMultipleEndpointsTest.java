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
package org.apache.camel.component.citrus;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CitrusMultipleEndpointsTest extends CamelTestSupport {

    @Test
    void shouldValidateMultipleEndpointsInRoute() throws Exception {
        CitrusEndpoint orderCheck
                = context.getEndpoint("citrus:validate/orderCheck", CitrusEndpoint.class);
        orderCheck.allMessages().body("""
                {
                  "orderId": "${orderId}",
                  "status": "created"
                }
                """);
        orderCheck.expectedMessageCount(1);

        CitrusEndpoint responseCheck
                = context.getEndpoint("citrus:validate/responseCheck", CitrusEndpoint.class);
        responseCheck.allMessages().body("""
                {
                  "orderId": "${orderId}",
                  "status": "processed"
                }
                """);
        responseCheck.expectedMessageCount(1);

        template.send("direct:start", exchange -> {
            exchange.setProperty("orderId", "ORD-001");
            exchange.getIn().setBody("""
                    {
                      "orderId": "ORD-001",
                      "status": "created"
                    }
                    """);
        });

        orderCheck.assertIsSatisfied();
        responseCheck.assertIsSatisfied();

        assertThat(orderCheck.getReceivedCount()).isEqualTo(1);
        assertThat(responseCheck.getReceivedCount()).isEqualTo(1);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("citrus:validate/orderCheck")
                        .process(exchange -> {
                            String body = exchange.getIn().getMandatoryBody(String.class);
                            exchange.getIn().setBody(body.replace("created", "processed"));
                        })
                        .to("citrus:validate/responseCheck");
            }
        };
    }
}
