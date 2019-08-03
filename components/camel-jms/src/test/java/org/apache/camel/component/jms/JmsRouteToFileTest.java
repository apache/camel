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
package org.apache.camel.component.jms;

import java.io.File;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Unit test that we can consume JMS message and store it as file (to avoid regression bug)
 */
public class JmsRouteToFileTest extends CamelTestSupport {

    protected String componentName = "activemq";

    @Test
    public void testRouteToFile() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        
        deleteDirectory("target/routetofile");

        template.sendBody("activemq:queue:hello", "Hello World");

        // pause to let file producer save the file
        result.assertIsSatisfied();
        
        // do file assertions
        File dir = new File("target/routetofile");
        assertTrue("Should be directory", dir.isDirectory());
        File file = dir.listFiles()[0];
        assertTrue("File should exists", file.exists());
        String body = IOConverter.toString(file, null);
        assertEquals("Hello World", body);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent(componentName, jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // using mock endpoint here purely for testing. You would normally write this route as
                // from("activemq:queue:hello").to("file://target/routetofile");
                from("activemq:queue:hello").to("file://target/routetofile").to("mock:result");
            }
        };
    }
}
