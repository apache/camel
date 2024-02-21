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
package org.apache.camel.itest.greeter;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JettyRecipientListCxfIssueTest extends CamelSpringTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JettyRecipientListCxfIssueTest.class);

    private static int port1 = AvailablePortFinder.getNextAvailable();
    private static int port2 = AvailablePortFinder.getNextAvailable();
    private static int port3 = AvailablePortFinder.getNextAvailable();
    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts
        System.setProperty("RecipientListCxfTest.port1", Integer.toString(port1));
        System.setProperty("RecipientListCxfTest.port2", Integer.toString(port2));
        System.setProperty("RecipientListCxfTest.port3", Integer.toString(port3));
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/itest/greeter/JettyRecipientListCxfIssueTest.xml");
    }

    @Test
    void testJettyRecipientListCxf() {
        final String request = context().getTypeConverter().convertTo(String.class, new File("src/test/resources/greetMe.xml"));
        assertNotNull(request);

        // send a message to jetty
        Exchange out = template.request("http://0.0.0.0:{{RecipientListCxfTest.port3}}/myapp", exchange -> {
            exchange.getIn().setHeader("operationName", "greetMe");
            exchange.getIn().setBody(request);
        });

        assertNotNull(out);
        assertNotNull(out.getMessage(), "Should have message");

        String body = out.getMessage().getBody(String.class);
        LOG.info("Reply from jetty call:\n{}", body);

        // we get the last reply as response
        assertNotNull(body);
        assertTrue(body.contains("Bye Camel"), "Should have Bye Camel");
    }

}
