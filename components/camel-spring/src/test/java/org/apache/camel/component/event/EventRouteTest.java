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
package org.apache.camel.component.event;

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.SpringTestSupport;
import org.junit.Test;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class EventRouteTest extends SpringTestSupport {

    protected Object expectedBody = "Hello there!";
    protected String uri = "spring-event:foo";

    @Test
    public void testSendingCamelExchangeToEndpointResultsInValidApplicationEventAfterTheRefreshEvent() throws Exception {
        MockEndpoint result = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        result.expectedMessageCount(2);

        template.sendBodyAndHeader(uri, expectedBody, "cheese", 123);

        result.assertIsSatisfied();

        // lets test we receive the context refreshed event
        Exchange exchange = result.getReceivedExchanges().get(0);
        Object body = exchange.getIn().getBody(ContextRefreshedEvent.class);
        log.info("Received body: " + body);
        assertNotNull(body);

        // lets test we receive the camel event
        exchange = result.getReceivedExchanges().get(1);
        body = exchange.getIn().getBody();
        log.info("Received body: " + body);
        CamelEvent event = assertIsInstanceOf(CamelEvent.class, body);
        Object actualBody = event.getExchange().getIn().getBody();
        assertEquals("Received event body", expectedBody, actualBody);
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/event/camelContext.xml");
    }
}
