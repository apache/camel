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

import java.io.FileInputStream;

import javax.jms.ConnectionFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.xml.StringSource;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * For unit testing with XML streams that can be troublesome with the StreamCache
 */
public class JmsXMLRouteTest extends CamelTestSupport {

    private static final String TEST_LONDON = "src/test/data/message1.xml";
    private static final String TEST_TAMPA = "src/test/data/message2.xml";

    @Test
    public void testLondonWithFileStreamAsObject() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:london");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("James");

        Source source = new StreamSource(new FileInputStream(TEST_LONDON));
        assertNotNull(source);

        template.sendBody("direct:object", source);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLondonWithFileStreamAsBytes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:london");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("James");

        Source source = new StreamSource(new FileInputStream(TEST_LONDON));
        assertNotNull(source);

        template.sendBody("direct:bytes", source);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLondonWithFileStreamAsDefault() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:london");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("James");

        Source source = new StreamSource(new FileInputStream(TEST_LONDON));
        assertNotNull(source);

        template.sendBody("direct:default", source);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTampaWithFileStreamAsObject() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tampa");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("Hiram");

        Source source = new StreamSource(new FileInputStream(TEST_TAMPA));
        assertNotNull(source);

        template.sendBody("direct:object", source);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTampaWithFileStreamAsBytes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tampa");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("Hiram");

        Source source = new StreamSource(new FileInputStream(TEST_TAMPA));
        assertNotNull(source);

        template.sendBody("direct:bytes", source);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTampaWithFileStreamAsDefault() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tampa");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("Hiram");

        Source source = new StreamSource(new FileInputStream(TEST_TAMPA));
        assertNotNull(source);

        template.sendBody("direct:default", source);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLondonWithStringSourceAsObject() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:london");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("James");

        Source source = new StringSource("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<person user=\"james\">\n"
                + "  <firstName>James</firstName>\n"
                + "  <lastName>Strachan</lastName>\n"
                + "  <city>London</city>\n"
                + "</person>");
        assertNotNull(source);

        template.sendBody("direct:object", source);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLondonWithStringSourceAsBytes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:london");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("James");

        Source source = new StringSource("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<person user=\"james\">\n"
                + "  <firstName>James</firstName>\n"
                + "  <lastName>Strachan</lastName>\n"
                + "  <city>London</city>\n"
                + "</person>");
        assertNotNull(source);

        template.sendBody("direct:bytes", source);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLondonWithStringSourceAsDefault() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:london");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("James");

        Source source = new StringSource("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<person user=\"james\">\n"
                + "  <firstName>James</firstName>\n"
                + "  <lastName>Strachan</lastName>\n"
                + "  <city>London</city>\n"
                + "</person>");
        assertNotNull(source);

        template.sendBody("direct:default", source);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable stream caching
                context.setStreamCaching(true);

                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0));

                // no need to convert to String as JMS producer can handle XML streams now
                from("direct:object").to("activemq:queue:object?jmsMessageType=Object");

                // no need to convert to String as JMS producer can handle XML streams now
                from("direct:bytes").to("activemq:queue:bytes?jmsMessageType=Bytes");

                // no need to convert to String as JMS producer can handle XML streams now
                from("direct:default").to("activemq:queue:default");

                from("activemq:queue:object")
                    .process(exchange -> {
                        Object body = exchange.getIn().getBody();
                        // should preserve the object as Source
                        assertIsInstanceOf(Source.class, body);
                    }).to("seda:choice");

                from("activemq:queue:bytes")
                    .process(exchange -> {
                        Object body = exchange.getIn().getBody();
                        // should be a byte array by default
                        assertIsInstanceOf(byte[].class, body);
                    }).to("seda:choice");

                from("activemq:queue:default")
                    .to("seda:choice");

                from("seda:choice")
                    .choice()
                        .when().xpath("/person/city = 'London'").to("mock:london")
                        .when().xpath("/person/city = 'Tampa'").to("mock:tampa")
                        .otherwise().to("mock:unknown")
                    .end();
            }
        };
    }

}
