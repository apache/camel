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
package org.apache.camel.component.jms;

import java.io.File;
import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;


/**
 * Unit test that we can do file over JMS to file.
 */
public class FileRouteToJmsToFileTest extends ContextTestSupport {

    protected String componentName = "activemq";

    public void testRouteFileToFile() throws Exception {
        deleteDirectory("target/file2file");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("file://target/file2file/in", "Hello World", FileComponent.HEADER_FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
        Thread.sleep(100);

        File file = new File("./target/file2file/out/hello.txt");
        file = file.getAbsoluteFile();
        assertTrue("The file should exists", file.exists());
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        camelContext.addComponent(componentName, jmsComponentClientAcknowledge(connectionFactory));

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/file2file/in").to("activemq:queue:hello");

                from("activemq:queue:hello").to("file://target/file2file/out", "mock:result");
            }
        };
    }
}