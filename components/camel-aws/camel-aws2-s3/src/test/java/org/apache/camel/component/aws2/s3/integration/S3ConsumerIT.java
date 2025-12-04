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

package org.apache.camel.component.aws2.s3.integration;

import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Endpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class S3ConsumerIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(3);

        template.send("direct:putObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "test.txt");
                exchange.getIn().setBody("Test");
            }
        });

        template.send("direct:putObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "test1.txt");
                exchange.getIn().setBody("Test1");
            }
        });

        template.send("direct:putObject", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2S3Constants.KEY, "test2.txt");
                exchange.getIn().setBody("Test2");
            }
        });

        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> MockEndpoint.assertIsSatisfied(context));

        // in-progress should remove keys when complete
        AWS2S3Endpoint s3 = (AWS2S3Endpoint) context.getRoute("s3consumer").getEndpoint();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Assertions.assertFalse(s3.getInProgressRepository().contains("test.txt"));
            Assertions.assertFalse(s3.getInProgressRepository().contains("test1.txt"));
            Assertions.assertFalse(s3.getInProgressRepository().contains("test2.txt"));
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint = "aws2-s3://" + name.get() + "?autoCreateBucket=true";

                from("direct:putObject").startupOrder(1).to(awsEndpoint);

                from("aws2-s3://" + name.get()
                                + "?moveAfterRead=true&destinationBucket=camel-kafka-connector&autoCreateBucket=true&destinationBucketPrefix=RAW(movedPrefix)&destinationBucketSuffix=RAW(movedSuffix)")
                        .routeId("s3consumer")
                        .startupOrder(2)
                        .log("${body}")
                        .process(e -> {
                            String key = e.getMessage().getHeader(AWS2S3Constants.KEY, String.class);
                            log.info("Processing S3Object: {}", key);
                            // should be in-progress
                            AWS2S3Endpoint s3 = (AWS2S3Endpoint)
                                    context.getRoute("s3consumer").getEndpoint();
                            Assertions.assertTrue(s3.getInProgressRepository().contains(key));
                        })
                        .to("mock:result");
            }
        };
    }
}
