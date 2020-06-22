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
package org.apache.camel.component.aws.kinesis.integration;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.kinesis.model.Record;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.kinesis.KinesisConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled("Must be manually tested.")
public class KinesisComponentIntegrationTest extends CamelTestSupport {

    @BindToRegistry("amazonKinesisClient")
    AmazonKinesis client = AmazonKinesisClientBuilder.standard().withRegion(Regions.EU_CENTRAL_1).build();

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void send() throws Exception {
        result.expectedMessageCount(2);

        template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KinesisConstants.PARTITION_KEY, "partition-1");
                exchange.getIn().setBody("Kinesis Event 1.");
            }
        });

        template.send("direct:start", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(KinesisConstants.PARTITION_KEY, "partition-1");
                exchange.getIn().setBody("Kinesis Event 2.");
            }
        });

        assertMockEndpointsSatisfied();

        assertResultExchange(result.getExchanges().get(0), "Kinesis Event 1.", "partition-1");
        assertResultExchange(result.getExchanges().get(1), "Kinesis Event 2.", "partition-1");
    }

    private void assertResultExchange(Exchange resultExchange, String data, String partition) {
        assertIsInstanceOf(Record.class, resultExchange.getIn().getBody());
        Record record = resultExchange.getIn().getBody(Record.class);
        assertEquals(data, new String(record.getData().array()));
        assertEquals(partition, resultExchange.getIn().getHeader(KinesisConstants.PARTITION_KEY));
        assertNotNull(resultExchange.getIn().getHeader(KinesisConstants.APPROX_ARRIVAL_TIME));
        assertNotNull(resultExchange.getIn().getHeader(KinesisConstants.SEQUENCE_NUMBER));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String kinesisEndpointUri = "aws-kinesis://kinesis1?amazonKinesisClient=#amazonKinesisClient";

                from("direct:start").to(kinesisEndpointUri);
            }
        };
    }
}
