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
package org.apache.camel.component.minio;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An object whose exchange failed is no longer in progress, so a later poll consumes it again.
 */
class MinioConsumerInProgressFailureTest extends CamelTestSupport {

    private final AtomicInteger attempts = new AtomicInteger();
    private FakeS3Server s3;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        s3 = new FakeS3Server("bkt");
        s3.put("a.txt", "hello");
        s3.start();
        return super.createCamelContext();
    }

    @AfterEach
    void stopServer() {
        if (s3 != null) {
            s3.stop();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("minio://bkt?accessKey=xxx&secretKey=yyy&region=us-east-1&delay=10&endpoint=" + s3.endpoint())
                        .to("seda:work");

                from("seda:work")
                        .process(e -> {
                            if (attempts.incrementAndGet() == 1) {
                                throw new IllegalStateException("first attempt fails");
                            }
                        })
                        .to("mock:result");
            }
        };
    }

    @Test
    void failedObjectIsConsumedAgain() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");

        mock.assertIsSatisfied();
        assertEquals(2, attempts.get());
    }
}
