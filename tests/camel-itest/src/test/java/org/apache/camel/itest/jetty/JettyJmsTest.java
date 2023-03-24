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
package org.apache.camel.itest.jetty;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.utils.extensions.JmsServiceExtension;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@CamelSpringTest
@ContextConfiguration
public class JettyJmsTest {
    @RegisterExtension
    public static JmsServiceExtension jmsServiceExtension = JmsServiceExtension.createExtension();

    private static int port = AvailablePortFinder.getNextAvailable();
    private static final String URL = "http://localhost:" + port + "/JettyJmsTest";
    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts
        System.setProperty("JettyJmsTest.port", Integer.toString(port));
    }

    @Autowired
    protected CamelContext camelContext;

    @EndpointInject("mock:JettyJmsTestResultEndpoint")
    protected MockEndpoint resultEndpoint;

    @Test
    void testMocksAreValid() throws Exception {
        assertNotNull(resultEndpoint);
        resultEndpoint.reset();

        ProducerTemplate template = camelContext.createProducerTemplate();
        template.sendBodyAndHeader(URL, "Hello form Willem", "Operation", "greetMe");

        // Sleep a while and wait for the message whole processing
        Thread.sleep(4000);
        template.stop();

        MockEndpoint.assertIsSatisfied(camelContext);
        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        assertEquals(1, list.size(), "Should get one message");

        for (Exchange exchange : list) {
            Object result = exchange.getIn().getBody();
            assertEquals("Hello form Willem", result, "Should get the request");
            assertEquals("greetMe", exchange.getIn().getHeader("Operation"), "Should get the header");
        }
    }

}
