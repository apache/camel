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
package org.apache.camel.builder.endpoint;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CAMEL-25771: an endpoint DSL option that is an object, such as an ExchangeFormatter, is a bean, which the checksum of
 * a route leaves out: the same route has the same checksum with another instance.
 */
class EndpointDslRouteChecksumTest {

    @Test
    void anOptionThatIsAnObjectKeepsTheChecksum() throws Exception {
        String first = checksum();
        String second = checksum();

        assertNotNull(first);
        assertEquals(first, second);
    }

    private static String checksum() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new EndpointRouteBuilder() {
                @Override
                public void configure() {
                    from(direct("orders")).routeId("orders")
                            .to(log("out").advanced().exchangeFormatter(new DefaultExchangeFormatter()));
                }
            });
            context.start();
            return context.getRoute("orders").getChecksum();
        }
    }
}
