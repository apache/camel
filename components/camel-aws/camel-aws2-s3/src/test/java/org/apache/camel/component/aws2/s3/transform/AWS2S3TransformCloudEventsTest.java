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
package org.apache.camel.component.aws2.s3.transform;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Operations;
import org.apache.camel.component.cloudevents.CloudEvent;
import org.apache.camel.component.cloudevents.CloudEvents;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AWS2S3TransformCloudEventsTest extends CamelTestSupport {
    protected MockEndpoint resultEndpoint;

    @Test
    public void testCloudEventDataTypeTransformation() throws Exception {
        resultEndpoint.expectedBodiesReceived("Hello World!");

        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(AWS2S3Constants.BUCKET_NAME, "mycamel");
                exchange.getMessage().setHeader(AWS2S3Constants.KEY, "camel.txt");
                exchange.getMessage().setHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.getObject);
                exchange.getMessage().setBody("Hello");
            }
        });

        resultEndpoint.assertIsSatisfied();
        CloudEvent cloudEvent = CloudEvents.v1_0;
        Message received = exchange.getMessage();
        Assertions.assertEquals("org.apache.camel.event.aws.s3.getObject",
                received.getHeader(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TYPE).http()));
        Assertions.assertEquals("aws.s3.bucket.mycamel",
                received.getHeader(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE).http()));
        Assertions.assertEquals(cloudEvent.version(),
                received.getHeader(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_VERSION).http()));
        Assertions.assertEquals("Hello World!", received.getBody());
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        resultEndpoint = getMockEndpoint("mock:result");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .inputType("aws2-s3:application-cloudevents")
                        .setBody(body().append(" World!"))
                        .outputType("http:application-cloudevents")
                        .to("mock:result");
            }
        };
    }
}
