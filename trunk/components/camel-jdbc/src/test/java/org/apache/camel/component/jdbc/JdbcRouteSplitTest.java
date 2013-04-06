/**
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
package org.apache.camel.component.jdbc;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class JdbcRouteSplitTest extends AbstractJdbcTestSupport {
    
    @EndpointInject(uri = "mock:result")
    private MockEndpoint mock;

    @Test
    public void testJdbcRoutes() throws Exception {
        mock.expectedMessageCount(3);

        template.sendBody("direct:hello", "select * from customer order by ID");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:hello")
                        // here we split the data from the testdb into new messages one by one
                        // so the mock endpoint will receive a message per row in the table
                    .to("jdbc:testdb").split(body()).to("mock:result");

                // END SNIPPET: e1
            }
        };
    }
}
