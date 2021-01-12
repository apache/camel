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
package org.apache.camel.component.couchbase;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.After;
import org.junit.jupiter.api.Test;

public class ConsumeMessagesWithLimitTest extends CouchbaseIntegrationTestBase {

    @Test
    public void testQueryForBeers() throws Exception {
        for (int i = 0; i < 15; i++) {
            cluster.bucket(bucketName).defaultCollection().upsert("DocumentID_" + i, "message" + i);
        }
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);

        assertMockEndpointsSatisfied(30, TimeUnit.SECONDS);

    }

    @After
    public void cleanBucket() {
        cluster.buckets().flushBucket(bucketName);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(String.format("%s&designDocumentName=%s&viewName=%s&limit=10", getConnectionUri(), bucketName, bucketName))
                        .log("message received")
                        .to("mock:result");
            }
        };

    }
}
