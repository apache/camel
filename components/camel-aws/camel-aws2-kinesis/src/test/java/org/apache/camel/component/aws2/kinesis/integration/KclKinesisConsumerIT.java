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
package org.apache.camel.component.aws2.kinesis.integration;

import java.util.concurrent.TimeUnit;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.kinesis.Kinesis2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Must be manually tested.")
public class KclKinesisConsumerIT extends CamelTestSupport {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:start").delay(10000)
                        .to("aws2-kinesis://pippo?useDefaultCredentialsProvider=true&region=eu-west-1").startupOrder(2);

                from("aws2-kinesis://pippo?useDefaultCredentialsProvider=true&useKclConsumers=true&region=eu-west-1&asyncClient=true")
                        .startupOrder(1)
                        .log("${body} and ${headers}")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testConnectivity() throws InterruptedException {
        result.expectedMessageCount(2);

        template.send("direct:start", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "partition-123");
            exchange.getIn().setBody("Kinesis Event 1.");
        });
        template.send("direct:start", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "partition-123");
            exchange.getIn().setBody("Kinesis Event 2.");
        });

        Awaitility.await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(2, result.getExchanges().size()));
    }
}
