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
package org.apache.camel.itest.jetty;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.interceptor.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

@ContextConfiguration
public class JettyJmsTest extends AbstractJUnit38SpringContextTests {

    @Autowired
    protected CamelContext camelContext;

    @EndpointInject(uri = "mock:resultEndpoint")
    protected MockEndpoint resultEndpoint;

    public void testMocksAreValidWithTracerEnabled() throws Exception {
        assertNotNull(camelContext);
        Tracer tracer = Tracer.getTracer(camelContext);
        assertNotNull(tracer);
        assertTrue("The tracer should be enabled", tracer.isEnabled());
        validMockes();
    }

    public void testMocksAreValidWithTracerDisabled() throws Exception {
        assertNotNull(camelContext);
        Tracer tracer = Tracer.getTracer(camelContext);
        assertNotNull(tracer);
        tracer.setEnabled(false);
        validMockes();
    }

    private void validMockes() throws Exception {
        resultEndpoint.reset();
        assertNotNull(resultEndpoint);

        ProducerTemplate<Exchange> template = camelContext.createProducerTemplate();
        template.sendBodyAndHeader("jetty:http://localhost:9000/test", "Hello form Willem", "Operation", "greetMe");

        // Sleep a while and wait for the message whole processing
        Thread.sleep(4000);

        MockEndpoint.assertIsSatisfied(camelContext);
        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        assertEquals("Should get one message", list.size(), 1);

        for (Exchange exchange : list) {
            Object result = exchange.getIn().getBody();
            assertEquals("Should get the request", "Hello form Willem", result);
            assertEquals("Should get the header", "greetMe", exchange.getIn().getHeader("Operation"));
        }
    }
}
