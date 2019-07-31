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

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test based on user forum request about this component
 */
public class JdbcFix9491Test extends AbstractJdbcTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @EndpointInject("direct:start")
    private ProducerTemplate direct;

    @Test
    public void testTimerInvoked() throws Exception {
        mock.expectedMessageCount(2);

        direct.sendBody("select * from customer");
        direct.sendBody("select * from customer");

        assertMockEndpointsSatisfied();
        Assert.assertEquals(2, mock.getReceivedExchanges().get(1).getIn().getBody(List.class).size());

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").
                to("jdbc:testdb?statement.maxRows=2").to("mock:result");
            }
        };
    }
}
