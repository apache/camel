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
package org.apache.camel.processor.enricher;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PollEnricherFileTest extends ContextTestSupport {

    @Test
    public void testPollEnrichFile() throws Exception {
        getMockEndpoint("mock:2").expectedMessageCount(1);
        getMockEndpoint("mock:2").message(0).body().isNull();

        assertMockEndpointsSatisfied();

        long ex1 = getMockEndpoint("mock:1").getExchanges().get(0).getProperty(Exchange.RECEIVED_TIMESTAMP, long.class);
        long ex2 = getMockEndpoint("mock:2").getExchanges().get(0).getProperty(Exchange.RECEIVED_TIMESTAMP, long.class);
        long delta = ex2 - ex1;
        Assertions.assertTrue(delta > 800, "Should delay for at least 1 second, was " + delta + " millis");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:hello?repeatCount=1&delay=10")
                        .to("log:1", "mock:1")
                        .pollEnrich("file:target/temp?noop=true&fileName=doesnotexist.csv", 1000)
                        .to("log:2", "mock:2");
            }
        };
    }
}
