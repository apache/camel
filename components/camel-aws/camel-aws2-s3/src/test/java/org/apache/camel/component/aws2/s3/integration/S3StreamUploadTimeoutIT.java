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
import software.amazon.awssdk.services.s3.model.S3Object;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class S3StreamUploadTimeoutIT extends Aws2S3Base {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(23);

        for (int i = 0; i < 23; i++) {
            template.sendBody("direct:stream1", "Andrea\n");
        }

        Thread.sleep(11000);
        assertMockEndpointsSatisfied();

        Exchange ex = template.request("direct:listObjects", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.listObjects);
            }
        });

        List<S3Object> resp = ex.getMessage().getBody(List.class);
        assertEquals(1, resp.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String awsEndpoint1
                        = "aws2-s3://mycamel-1?autoCreateBucket=true&streamingUploadMode=true&keyName=fileTest.txt&batchMessageNumber=25&namingStrategy=random&streamingUploadTimeout=10000";

                from("direct:stream1").to(awsEndpoint1).to("mock:result");

                String awsEndpoint = "aws2-s3://mycamel-1?autoCreateBucket=true";

                from("direct:listObjects").to(awsEndpoint);
            }
        };
    }
}
