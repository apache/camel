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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class ProduceMessagesSimpleTest extends CamelTestSupport {

    // Ignore test since build environment does not have any couchbase instance
    @Disabled
    @Test
    public void testInsert() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "couchbase persist");
        assertMockEndpointsSatisfied();
        mock.message(0).body().equals("couchbase persist");

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                // need couchbase installed on localhost
                from("direct:start").setHeader(CouchbaseConstants.HEADER_ID, constant("blabla:120771"))
                        .to("couchbase:http://192.168.1.102/test?additionalHosts=localhost&username=root&password=123456")
                        .to("mock:result");

            }
        };
    }
}
