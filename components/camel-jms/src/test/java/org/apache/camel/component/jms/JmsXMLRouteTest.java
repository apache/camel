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

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.util.xml.StringSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * For unit testing with XML streams that can be troublesome with the StreamCache
 */
public class JmsXMLRouteTest extends AbstractJMSTest {
    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private static final String TEST_LONDON = "src/test/data/message1.xml";
    private static final String TEST_TAMPA = "src/test/data/message2.xml";
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @ParameterizedTest
    @ValueSource(strings = { "direct:object", "direct:bytes", "direct:default" })
    public void testLondonWithFileStream(String endpointUri) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:london");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("James");

        Source source = new StreamSource(new FileInputStream(TEST_LONDON));
        assertNotNull(source);

        template.sendBody(endpointUri, source);

        MockEndpoint.assertIsSatisfied(context);
    }

    @ParameterizedTest
    @ValueSource(strings = { "direct:object", "direct:bytes", "direct:default" })
    public void testTampaWithFileStream(String endpointUri) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tampa");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("Hiram");

        Source source = new StreamSource(new FileInputStream(TEST_TAMPA));
        assertNotNull(source);

        template.sendBody(endpointUri, source);

        MockEndpoint.assertIsSatisfied(context);
    }

    @ParameterizedTest
    @ValueSource(strings = { "direct:object", "direct:bytes", "direct:default" })
    public void testLondonWithStringSourceAsObject(String endpointUri) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:london");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).contains("James");

        Source source = new StringSource(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                                         + "<person user=\"james\">\n"
                                         + "  <firstName>James</firstName>\n"
                                         + "  <lastName>Strachan</lastName>\n"
                                         + "  <city>London</city>\n"
                                         + "</person>");
        assertNotNull(source);

        template.sendBody(endpointUri, source);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @ContextFixture
    public void configureStreamCaching(CamelContext context) {
        // enable stream caching
        context.setStreamCaching(true);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0));

                // no need to convert to String as JMS producer can handle XML streams now
                from("direct:object").to("activemq:queue:JmsXMLRouteTest.object?jmsMessageType=Object");

                // no need to convert to String as JMS producer can handle XML streams now
                from("direct:bytes").to("activemq:queue:JmsXMLRouteTest.bytes?jmsMessageType=Bytes");

                // no need to convert to String as JMS producer can handle XML streams now
                from("direct:default").to("activemq:queue:JmsXMLRouteTest.default");

                from("activemq:queue:JmsXMLRouteTest.object")
                        .process(exchange -> {
                            Object body = exchange.getIn().getBody();
                            // should preserve the object as Source
                            assertIsInstanceOf(Source.class, body);
                        }).to("seda:choice");

                from("activemq:queue:JmsXMLRouteTest.bytes")
                        .process(exchange -> {
                            Object body = exchange.getIn().getBody();
                            // should be a byte array by default
                            assertIsInstanceOf(byte[].class, body);
                        }).to("seda:choice");

                from("activemq:queue:JmsXMLRouteTest.default")
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

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
