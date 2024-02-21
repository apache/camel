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

import java.util.Iterator;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;

public class JdbcProducerOutputTypeStreamListTest extends AbstractJdbcTestSupport {
    private static final String QUERY = "select * from customer";

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void shouldReturnAnIterator() throws Exception {
        result.expectedMessageCount(1);

        template.sendBody("direct:start", QUERY);

        result.assertIsSatisfied();
        assertIsInstanceOf(Iterator.class, resultBodyAt(0));
    }

    @Test
    public void shouldStreamResultRows() throws Exception {
        result.expectedMessageCount(3);

        template.sendBody("direct:withSplit", QUERY);

        result.assertIsSatisfied();
        assertIsInstanceOf(Map.class, resultBodyAt(0));
        assertIsInstanceOf(Map.class, resultBodyAt(1));
        assertIsInstanceOf(Map.class, resultBodyAt(2));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("jdbc:testdb?outputType=StreamList").to("mock:result");
                from("direct:withSplit").to("jdbc:testdb?outputType=StreamList").split(body()).to("mock:result");
            }
        };
    }

    private Object resultBodyAt(int index) {
        return result.assertExchangeReceived(index).getIn().getBody();
    }
}
