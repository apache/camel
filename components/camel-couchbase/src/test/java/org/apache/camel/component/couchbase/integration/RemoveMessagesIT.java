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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.apache.camel.component.couchbase.CouchbaseConstants.COUCHBASE_DELETE;
import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_ID;

@EnabledIfSystemProperty(named = "couchbase.enable.it", matches = "true",
                         disabledReason = "Too resource intensive for most systems to run reliably")
@Tags({ @Tag("couchbase-7") })
public class RemoveMessagesIT extends CouchbaseIntegrationTestBase {

    @BeforeEach
    public void addToBucket() {
        for (int i = 0; i < 15; i++) {
            cluster.bucket(bucketName).defaultCollection().upsert("DocumentID_" + i, "message" + i);
        }
    }

    @Test
    public void testDelete() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");

        template.sendBodyAndHeader("direct:start", "delete the document ", HEADER_ID, "DocumentID_1");
        template.sendBodyAndHeader("direct:start", "delete the document", HEADER_ID, "DocumentID_2");

        MockEndpoint.assertIsSatisfied(context);

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to(getConnectionUri() + "&operation=" + COUCHBASE_DELETE)
                        .to("mock:result");
            }
        };
    }
}
