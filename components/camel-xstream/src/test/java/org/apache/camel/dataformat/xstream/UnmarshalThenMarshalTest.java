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
package org.apache.camel.dataformat.xstream;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class UnmarshalThenMarshalTest extends CamelTestSupport {
    
    @Test
    public void testSendXmlAndUnmarshal() throws Exception {

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        PurchaseOrder expectedBody = new PurchaseOrder();
        expectedBody.setAmount(20.0);
        expectedBody.setName("Wine");
        expectedBody.setPrice(5.0);

        template.sendBody("direct:start", expectedBody);

        resultEndpoint.assertIsSatisfied();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        Object actualBody = exchange.getIn().getBody();

        log.debug("Received: " + actualBody);
        assertEquals("Received body", expectedBody, actualBody);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").
                        marshal().xstream(PurchaseOrder.class).
                        process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                log.debug("marshalled: " + exchange.getIn().getBody(String.class));
                            }
                        }).
                        unmarshal().xstream(PurchaseOrder.class).
                        to("mock:result");
            }
        };
    }
}
