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

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.kinesis.Kinesis2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.Record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled("Must be manually tested.")
public class KinesisComponentIntegrationTest extends CamelTestSupport {

    @BindToRegistry("amazonKinesisClient")
    KinesisClient client = KinesisClient.builder().build();

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void send() throws Exception {
        result.expectedMessageCount(2);

        template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "partition-1");
                exchange.getIn().setBody("Kinesis Event 1.");
            }
        });

        template.send("direct:start", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Kinesis2Constants.PARTITION_KEY, "partition-1");
                exchange.getIn().setBody("Kinesis Event 2.");
            }
        });

        assertMockEndpointsSatisfied();

        assertResultExchange(result.getExchanges().get(0), "Kinesis Event 1.", "partition-1");
        assertResultExchange(result.getExchanges().get(1), "Kinesis Event 2.", "partition-1");
    }

    private void assertResultExchange(Exchange resultExchange, String data, String partition) {
        Record record = resultExchange.getIn().getBody(Record.class);
        assertEquals(data, new String(record.data().asByteArray()));
        assertEquals(partition, resultExchange.getIn().getHeader(Kinesis2Constants.PARTITION_KEY));
        assertNotNull(resultExchange.getIn().getHeader(Kinesis2Constants.APPROX_ARRIVAL_TIME));
        assertNotNull(resultExchange.getIn().getHeader(Kinesis2Constants.SEQUENCE_NUMBER));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String kinesisEndpointUri = "aws2-kinesis://kinesis1?amazonKinesisClient=#amazonKinesisClient";

                from("direct:start").to(kinesisEndpointUri);
            }
        };
    }
}
