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
package org.apache.camel.component.batch;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BatchAggregationTest extends CamelTestSupport {

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("sumStrategy", (AggregationStrategy) (oldExchange, newExchange) -> {
            if (oldExchange == null) {
                return newExchange;
            }
            int oldSum = oldExchange.getIn().getBody(Integer.class);
            int newVal = newExchange.getIn().getBody(Integer.class);
            oldExchange.getIn().setBody(oldSum + newVal);
            return oldExchange;
        });
    }

    @Test
    void testAggregationStrategy() throws Exception {
        List<Integer> items = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:aggregate", exchange -> {
            exchange.getIn().setBody(items);
        });

        assertNull(result.getException());
        // Sum of 1..10 = 55
        int sum = result.getIn().getBody(Integer.class);
        assertEquals(55, sum);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:process-item")
                        .log("Processing ${body}");

                from("direct:aggregate")
                        .to("batch:aggJob?processorRef=direct:process-item&aggregationStrategy=#sumStrategy");
            }
        };
    }
}
