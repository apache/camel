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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultPollingConsumerPollStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An object that is deleted (for example by another consumer) after it was listed is skipped; the other objects of the
 * same poll are still consumed and the poll does not fail.
 */
class MinioConsumerDeletedObjectTest extends CamelTestSupport {

    private final List<Exception> failedPolls = new CopyOnWriteArrayList<>();
    private FakeS3Server s3;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        s3 = new FakeS3Server("bkt");
        s3.put("a.txt", "deleted before the stat");
        s3.put("b.txt", "deleted before the get");
        s3.put("c.txt", "hello");
        s3.deleteWhenListed("a.txt");
        s3.deleteWhenStatted("b.txt");
        s3.start();
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("recordFailedPolls", new DefaultPollingConsumerPollStrategy() {
            @Override
            public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception e) {
                failedPolls.add(e);
                return false;
            }
        });
        return context;
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
                from("minio://bkt?accessKey=xxx&secretKey=yyy&region=us-east-1&delay=10&pollStrategy=#recordFailedPolls"
                     + "&endpoint=" + s3.endpoint())
                        .to("mock:result");
            }
        };
    }

    @Test
    void deletedObjectsAreSkipped() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");

        mock.assertIsSatisfied();
        assertEquals(List.of(), failedPolls, "no poll should fail because an object was deleted after the listing");
    }
}
