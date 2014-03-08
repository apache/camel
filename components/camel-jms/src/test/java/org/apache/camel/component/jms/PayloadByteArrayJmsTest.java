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


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;


/**
 * Unit test that we send payload as byte[] for certain types
 */
public class PayloadByteArrayJmsTest extends CamelTestSupport {

    protected String componentName = "activemq";

    @Test
    public void testReaderShouldBeByteArray() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(byte[].class);
        mock.message(0).body(String.class).isEqualTo("Hello World");

        Reader body = new StringReader("Hello World");

        template.sendBody("activemq:queue:hello", body);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInputStreamShouldBeByteArray() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(byte[].class);
        mock.message(0).body(String.class).isEqualTo("Hello World");

        InputStream body = new ByteArrayInputStream("Hello World".getBytes());

        template.sendBody("activemq:queue:hello", body);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testByteBufferShouldBeByteArray() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(byte[].class);
        mock.message(0).body(String.class).isEqualTo("Hello World");

        ByteBuffer body = ByteBuffer.wrap("Hello World".getBytes());

        template.sendBody("activemq:queue:hello", body);

        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent(componentName, jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:queue:hello").to("mock:result");
            }
        };
    }
}