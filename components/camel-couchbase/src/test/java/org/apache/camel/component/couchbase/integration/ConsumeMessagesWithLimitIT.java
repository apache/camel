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
package org.apache.camel.component.couchbase.integration;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "couchbase.enable.it", matches = "true",
                         disabledReason = "Too resource intensive for most systems to run reliably")
@Tags({ @Tag("couchbase-6") })
public class ConsumeMessagesWithLimitIT extends CouchbaseIntegrationTestBase {

    @BeforeEach
    public void addToBucket() {
        for (int i = 0; i < 15; i++) {
            cluster.bucket(bucketName).defaultCollection().upsert("DocumentID_" + i, "message" + i);
        }
    }

    @Test
    public void testQueryForBeers() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

    }

    @AfterEach
    public void cleanBucket() {
        cluster.buckets().flushBucket(bucketName);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(String.format("%s&designDocumentName=%s&viewName=%s&limit=10", getConnectionUri(), bucketName, bucketName))
                        .log("message received")
                        .to("mock:result");
            }
        };

    }
}
