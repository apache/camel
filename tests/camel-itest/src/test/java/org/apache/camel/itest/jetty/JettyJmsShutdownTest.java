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

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.itest.utils.extensions.JmsServiceExtension;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.junit6.CamelSpringTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@CamelSpringTest
@ContextConfiguration
@Timeout(60)
public class JettyJmsShutdownTest {
    @RegisterExtension
    public static JmsServiceExtension jmsServiceExtension = JmsServiceExtension.createExtension();

    @RegisterExtension
    static AvailablePortFinder.Port portHolder = AvailablePortFinder.find();
    private static final String URL;
    static {
        int port = portHolder.getPort();
        URL = "http://localhost:" + port + "/JettyJmsShutdownTest";

        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts
        System.setProperty("JettyJmsShutdownTest.port", Integer.toString(port));
    }

    @Autowired
    protected CamelContext camelContext;

    @Autowired
    protected ProducerTemplate template;

    @Test
    void testShutdown() throws Exception {
        // wait until all 3 messages are in progress in the consumer route
        NotifyBuilder notify
                = new NotifyBuilder(camelContext).fromRoute("JettyJmsShutdownTest-consumer").whenReceived(3).create();

        Future<String> reply1 = template.asyncRequestBody(URL, "World", String.class);
        Future<String> reply2 = template.asyncRequestBody(URL, "Camel", String.class);
        Future<String> reply3 = template.asyncRequestBody(URL, "Tiger", String.class);

        assertTrue(notify.matches(30, TimeUnit.SECONDS), "All messages should be received by the consumer route");

        // shutdown while in progress
        camelContext.stop();

        assertEquals("Bye World", reply1.get(5, TimeUnit.SECONDS));
        assertEquals("Bye Camel", reply2.get(5, TimeUnit.SECONDS));
        assertEquals("Bye Tiger", reply3.get(5, TimeUnit.SECONDS));
    }

}
