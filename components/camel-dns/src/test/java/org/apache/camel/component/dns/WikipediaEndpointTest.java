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

/**
 * A test case to check wikipedia records.
 */
public class WikipediaEndpointTest extends CamelTestSupport {

    private static final String RESPONSE_MONKEY = "\"A monkey is a nonhuman " + "primate mammal with the exception usually of the lemurs and "
        + "tarsiers. More specifically, the term monkey refers to a subset " + "of monkeys: any of the smaller longer-tailed catarrhine or "
        + "platyrrhine primates as contrasted with the apes.\" " + "\" http://en.wikipedia.org/wiki/Monkey\"";

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("dns:wikipedia").to("mock:result");
            }
        };
    }

    @Test
    @Ignore("Testing behind nat produces timeouts")
    public void testWikipediaForMonkey() throws Exception {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedMessagesMatches(new Predicate() {
            public boolean matches(Exchange exchange) {
                String str = (String) exchange.getIn().getBody();
                return RESPONSE_MONKEY.equals(str);
            }
        });
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("term", "monkey");
        template.sendBodyAndHeaders(null, headers);
        resultEndpoint.assertIsSatisfied();
    }

}