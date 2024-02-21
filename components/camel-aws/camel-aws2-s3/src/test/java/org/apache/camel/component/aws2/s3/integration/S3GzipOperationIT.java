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

import java.util.UUID;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;
import org.apache.camel.component.aws2.s3.AWS2S3Operations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class S3GzipOperationIT extends Aws2S3Base {
    @Test
    public void sendAndGet() throws Exception {
        final String content = UUID.randomUUID().toString();
        final MockEndpoint poll = getMockEndpoint("mock:poll");

        poll.expectedBodiesReceived(content);
        poll.expectedMessageCount(1);

        // put a compressed element to the bucket
        Object putResult = fluentTemplate()
                .to("direct:putObject")
                .withHeader(AWS2S3Constants.KEY, "hello.txt.gz")
                .withBody(content)
                .request(Object.class);

        assertNotNull(putResult);

        //retrieve it from a producer
        Object getResult = fluentTemplate()
                .to("direct:getObject")
                .withHeader(AWS2S3Constants.KEY, "hello.txt.gz")
                .withHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.getObject)
                .request(String.class);

        assertEquals(content, getResult);

        //retrieve it from a polling consumer
        poll.assertIsSatisfied();

        // delete the content
        fluentTemplate()
                .to("aws2-s3://mycamel?autoCreateBucket=true")
                .withHeader(AWS2S3Constants.KEY, "hello.txt.gz")
                .withHeader(AWS2S3Constants.S3_OPERATION, AWS2S3Operations.deleteObject)
                .request();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:putObject")
                        .marshal().gzipDeflater()
                        .to("aws2-s3://mycamel?autoCreateBucket=true");
                from("direct:getObject")
                        .to("aws2-s3://mycamel?autoCreateBucket=true&deleteAfterRead=false&includeBody=true")
                        .unmarshal().gzipDeflater();
                from("aws2-s3://mycamel?autoCreateBucket=true&deleteAfterRead=false&includeBody=true&prefix=hello.txt.gz")
                        .unmarshal().gzipDeflater()
                        .to("mock:poll");
            }
        };
    }
}
