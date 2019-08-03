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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.xbill.DNS.Message;
import org.xbill.DNS.Section;

/**
 * Tests for the dig endpoint.
 */
@Ignore("Wikipedia service is broken now")
public class DnsDigEndpointTest extends CamelTestSupport {

    private static final String RESPONSE_MONKEY = "\"A Macaque, an old world species of "
                + "monkey native to Southeast Asia|thumb]A monkey is a primate of the "
                + "Haplorrhini suborder and simian infraorder, either an Old World monkey "
                + "or a New World monkey, but excluding apes. There are about 260 known "
                + "living specie\" \"s of monkey. Many are arboreal, although there are "
                + "species that live primarily on the ground, such as baboons... "
                + "http://en.wikipedia.org/wiki/Monkey\""; 
    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce("direct:start")
    protected ProducerTemplate template;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("dns:dig").to("mock:result");
            }
        };
    }

    @Test
    public void testDigForMonkey() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(new Predicate() {
            public boolean matches(Exchange exchange) {
                String str = ((Message) exchange.getIn().getBody()).getSectionArray(Section.ANSWER)[0].rdataToString();
                return RESPONSE_MONKEY.equals(str);
            }
        });
        Map<String, Object> headers = new HashMap<>();
        headers.put("dns.name", "monkey.wp.dg.cx");
        headers.put("dns.type", "TXT");
        template.sendBodyAndHeaders(null, headers);
        resultEndpoint.assertIsSatisfied();
    }

}
