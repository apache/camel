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
package org.apache.camel.component.dns;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.Record;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A set of test cases to make DNS lookups.
 */
public class DnsLookupEndpointTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("dns:lookup").to("mock:result");
            }
        };
    }

    @Test
    void testDNSWithNoHeaders() throws Exception {
        resultEndpoint.expectedMessageCount(0);
        try {
            template.sendBody("hello");
            fail("Should have thrown exception");
        } catch (Exception t) {
            assertTrue(t.getCause() instanceof IllegalArgumentException);
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    void testDNSWithEmptyNameHeader() throws Exception {
        resultEndpoint.expectedMessageCount(0);
        try {
            template.sendBodyAndHeader("hello", "dns.name", "");
            fail("Should have thrown exception");
        } catch (Exception t) {
            assertTrue(t.getCause() instanceof IllegalArgumentException, t.toString());
        }
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    @Disabled("Testing behind nat produces timeouts")
    void testDNSWithNameHeader() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(new Predicate() {
            public boolean matches(Exchange exchange) {
                Record[] record = (Record[]) exchange.getIn().getBody();
                return record[0].getName().toString().equals("www.example.com.");
            }
        });
        Map<String, Object> headers = new HashMap<>();
        headers.put("dns.name", "www.example.com");
        template.sendBodyAndHeaders("hello", headers);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    @Disabled("Testing behind nat produces timeouts")
    void testDNSWithNameHeaderAndType() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(new Predicate() {
            public boolean matches(Exchange exchange) {
                Record[] record = (Record[]) exchange.getIn().getBody();
                return record[0].getName().toString().equals("www.example.com.");
            }
        });
        Map<String, Object> headers = new HashMap<>();
        headers.put("dns.name", "www.example.com");
        headers.put("dns.type", "A");
        template.sendBodyAndHeaders("hello", headers);
        resultEndpoint.assertIsSatisfied();
    }
}
