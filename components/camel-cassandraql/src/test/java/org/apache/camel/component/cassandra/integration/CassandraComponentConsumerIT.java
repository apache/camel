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
package org.apache.camel.component.cassandra.integration;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.datastax.oss.driver.api.core.cql.Row;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CassandraComponentConsumerIT extends BaseCassandra {

    static final String CQL = "select login, first_name, last_name from camel_user";

    @Test
    public void testConsumeAll() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultAll");
        mock.expectedMinimumMessageCount(1);
        mock.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                assertTrue(body instanceof List);
            }
        });
        mock.await(1, TimeUnit.SECONDS);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testConsumeUnprepared() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultUnprepared");
        mock.expectedMinimumMessageCount(1);
        mock.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                assertTrue(body instanceof List);
            }
        });
        mock.await(1, TimeUnit.SECONDS);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testConsumeOne() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultOne");
        mock.expectedMinimumMessageCount(1);
        mock.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                assertTrue(body instanceof Row);
            }
        });
        mock.await(1, TimeUnit.SECONDS);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                fromF("cql://%s/%s?cql=%s", getUrl(), KEYSPACE_NAME, CQL).to("mock:resultAll");
                fromF("cql://%s/%s?cql=%s&prepareStatements=false", getUrl(), KEYSPACE_NAME, CQL)
                        .to("mock:resultUnprepared");
                fromF("cql://%s/%s?cql=%s&resultSetConversionStrategy=ONE", getUrl(), KEYSPACE_NAME, CQL)
                        .to("mock:resultOne");
            }
        };
    }
}
