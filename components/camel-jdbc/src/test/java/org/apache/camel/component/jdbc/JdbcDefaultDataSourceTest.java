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
package org.apache.camel.component.jdbc;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class JdbcDefaultDataSourceTest extends AbstractJdbcTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @SuppressWarnings("rawtypes")
    @Test
    public void testReadSize() throws Exception {
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "select * from customer");

        assertMockEndpointsSatisfied();

        List list = mock.getExchanges().get(0).getIn().getBody(ArrayList.class);
        assertEquals(1, list.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").to("jdbc:dataSource?readSize=1").to("mock:result");
            }
        };
    }
}