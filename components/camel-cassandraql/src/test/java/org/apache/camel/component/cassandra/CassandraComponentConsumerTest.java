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
package org.apache.camel.component.cassandra;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.datastax.driver.core.Row;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.cassandraunit.CassandraCQLUnit;
import org.junit.Rule;
import org.junit.Test;

public class CassandraComponentConsumerTest extends BaseCassandraTest {

    private static final String CQL = "select login, first_name, last_name from camel_user";

    @Rule
    public CassandraCQLUnit cassandra = CassandraUnitUtils.cassandraCQLUnit();

    @Test
    public void testConsumeAll() throws Exception {
        if (!canTest()) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:resultAll");
        mock.expectedMinimumMessageCount(1);
        mock.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Object body = exchange.getIn().getBody();
                assertTrue(body instanceof List);
            }
        });
        mock.await(1, TimeUnit.SECONDS);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConsumeUnprepared() throws Exception {
        if (!canTest()) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:resultUnprepared");
        mock.expectedMinimumMessageCount(1);
        mock.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Object body = exchange.getIn().getBody();
                assertTrue(body instanceof List);
            }
        });
        mock.await(1, TimeUnit.SECONDS);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConsumeOne() throws Exception {
        if (!canTest()) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:resultOne");
        mock.expectedMinimumMessageCount(1);
        mock.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Object body = exchange.getIn().getBody();
                assertTrue(body instanceof Row);
            }
        });
        mock.await(1, TimeUnit.SECONDS);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("cql://localhost/camel_ks?cql=" + CQL).to("mock:resultAll");
                from("cql://localhost/camel_ks?cql=" + CQL + "&prepareStatements=false").to("mock:resultUnprepared");
                from("cql://localhost/camel_ks?cql=" + CQL + "&resultSetConversionStrategy=ONE").to("mock:resultOne");
            }
        };
    }
}
