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
package org.apache.camel.component.restlet;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.junit.Test;

/**
 * @version 
 */
public class RestletHeaderFilterStrategyTest extends RestletTestSupport {

    private static final String HEADER_FILTER = "filter";

    @Test
    public void testRestletProducerInFilterAllowedHeader() throws Exception {
        String acceptedHeaderKey = "dontFilter";
        MockEndpoint mock = getMockEndpoint("mock:out");
        mock.expectedHeaderReceived(acceptedHeaderKey, "any value");
        String out = template.requestBodyAndHeader("direct:start", null, acceptedHeaderKey, "any value", String.class);
        mock.assertIsSatisfied();
    }

    @Test
    public void testRestletProducerInFilterNotAllowedHeader() throws Exception {
        String notAcceptedHeaderKey = HEADER_FILTER + "ThisHeader";
        MockEndpoint mock = getMockEndpoint("mock:out");
        mock.whenAnyExchangeReceived(new Processor() {
            public void process(Exchange exchange) throws Exception {
                String notValidHeader = exchange.getIn().getHeader(notAcceptedHeaderKey, String.class);
                Map<String, Object> headers = exchange.getIn().getHeaders();
                for (String key : headers.keySet()) {
                    assertFalse("Header should have been filtered: " + key, key.startsWith(HEADER_FILTER));
                }
            }
        });
        template.requestBodyAndHeader("direct:start", null, notAcceptedHeaderKey, "any value", String.class);
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // force synchronous processing using restlet and add filtering
                DefaultHeaderFilterStrategy strategy = new DefaultHeaderFilterStrategy();
                strategy.setInFilterPattern(HEADER_FILTER + ".*");
                strategy.setOutFilterPattern(HEADER_FILTER + ".*");

                RestletComponent restlet = context.getComponent("restlet", RestletComponent.class);
                restlet.setHeaderFilterStrategy(strategy);
                restlet.setSynchronous(true);

                from("direct:start").to("restlet:http://localhost:" + portNum + "/users/123/exclude").to("log:reply");
                from("restlet:http://localhost:" + portNum + "/users/{id}/{filterExcluded}?restletMethods=GET").to("mock:out");
            }
        };
    }
}
