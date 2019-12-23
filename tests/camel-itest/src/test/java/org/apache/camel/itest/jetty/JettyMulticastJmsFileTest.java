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

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.TestSupport;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;

@ContextConfiguration
public class JettyMulticastJmsFileTest extends AbstractJUnit4SpringContextTests {
    
    private static int port = AvailablePortFinder.getNextAvailable();
    private static final String URL = "http://localhost:" + port + "/test";
    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts 
        System.setProperty("JettyMulticastJmsFileTest.port", Integer.toString(port));
    }

    @Autowired
    protected CamelContext camelContext;

    @Test
    public void testJettyMulticastJmsFile() throws Exception {
        TestSupport.deleteDirectory("target/jetty");

        ProducerTemplate template = camelContext.createProducerTemplate();

        String out = template.requestBody(URL, "Hello World", String.class);
        assertEquals("Bye World", out);

        template.stop();

        ConsumerTemplate consumer = camelContext.createConsumerTemplate();
        String in = consumer.receiveBody("jms:queue:foo", 5000, String.class);
        assertEquals("Hello World", in);

        String in2 = consumer.receiveBody("file://target/jetty?noop=true&readLock=none", 5000, String.class);
        assertEquals("Hello World", in2);

        consumer.stop();
    }

}
