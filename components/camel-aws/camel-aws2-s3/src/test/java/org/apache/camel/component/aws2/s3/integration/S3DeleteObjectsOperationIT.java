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

import java.util.Arrays;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class S3DeleteObjectsOperationIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void deleteMultipleObjects() throws Exception {
        result.expectedMessageCount(1);

        // First upload some objects
        template.send("direct:putObject1", exchange -> exchange.getIn().setBody("Test Content 1"));
        template.send("direct:putObject2", exchange -> exchange.getIn().setBody("Test Content 2"));
        template.send("direct:putObject3", exchange -> exchange.getIn().setBody("Test Content 3"));

        // Now delete multiple objects
        template.send("direct:deleteObjects", new Processor() {
            @Override
            public void process(Exchange exchange) {
                List<String> keys = Arrays.asList("test-key-1", "test-key-2", "test-key-3");
                exchange.getIn().setHeader(AWS2S3Constants.KEYS_TO_DELETE, keys);
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.deleteObjects);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange resultExchange = result.getExchanges().get(0);
        DeleteObjectsResponse response = resultExchange.getMessage().getBody(DeleteObjectsResponse.class);
        assertNotNull(response);
        assertTrue(response.deleted().size() >= 1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint = "aws2-s3://" + name.get() + "?autoCreateBucket=true";

                from("direct:putObject1")
                        .setHeader(AWS2S3Constants.KEY, constant("test-key-1"))
                        .to(awsEndpoint);

                from("direct:putObject2")
                        .setHeader(AWS2S3Constants.KEY, constant("test-key-2"))
                        .to(awsEndpoint);

                from("direct:putObject3")
                        .setHeader(AWS2S3Constants.KEY, constant("test-key-3"))
                        .to(awsEndpoint);

                from("direct:deleteObjects")
                        .to(awsEndpoint)
                        .to("mock:result");
            }
        };
    }
}
