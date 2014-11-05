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
package org.apache.camel.itest.osgi.spring.event;

import org.apache.camel.Exchange;
import org.apache.camel.component.event.CamelEvent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.OSGiIntegrationSpringTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

@RunWith(PaxExam.class)
public class EventRouteTest extends OSGiIntegrationSpringTestSupport {

    protected Object expectedBody = "Hello there!";
    protected String uri = "spring-event:default";

    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{"org/apache/camel/itest/osgi/spring/event/CamelContext.xml"});
    }

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

}
