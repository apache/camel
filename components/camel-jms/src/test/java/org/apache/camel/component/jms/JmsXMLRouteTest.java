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

import java.io.FileInputStream;
import javax.jms.ConnectionFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxp.StringSource;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;

/**
 * For unit testing with XML streams that can be troublesome with the StreamCache
 *
 * @version $Revision$
 */
public class JmsXMLRouteTest extends ContextTestSupport {

    private static final String TEST_LONDON = "src/test/data/message1.xml";
    private static final String TEST_TAMPA = "src/test/data/message2.xml";

    public void testLondonWithFileStream() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:london");
        mock.expectedMessageCount(1);
        mock.message(0).bodyAs(String.class).contains("James");

        Source source = new StreamSource(new FileInputStream(TEST_LONDON));
        assertNotNull(source);

        template.sendBody("direct:start", source);

        assertMockEndpointsSatisfied();
    }

    public void testTampaWithFileStream() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tampa");
        mock.expectedMessageCount(1);
        mock.message(0).bodyAs(String.class).contains("Hiram");

        Source source = new StreamSource(new FileInputStream(TEST_TAMPA));
        assertNotNull(source);

        template.sendBody("direct:start", source);

        assertMockEndpointsSatisfied();
    }

    public void testLondonWithStringSource() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:london");
        mock.expectedMessageCount(1);
        mock.message(0).bodyAs(String.class).contains("James");

        Source source = new StringSource("<person user=\"james\">\n"
                + "  <firstName>James</firstName>\n"
                + "  <lastName>Strachan</lastName>\n"
                + "  <city>London</city>\n"
                + "</person>");
        assertNotNull(source);

        template.sendBody("direct:start", source);

        assertMockEndpointsSatisfied();
    }


    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        camelContext.addComponent("activemq", jmsComponentClientAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("activemq:queue:foo");

                from("activemq:queue:foo")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            Object body = exchange.getIn().getBody();
                            // should preserve the object as Source
                            assertIsInstanceOf(Source.class, body);
                        }
                    })
                    .choice()
                        .when().xpath("/person/city = 'London'").to("mock:london")
                        .when().xpath("/person/city = 'Tampa'").to("mock:tampa")
                        .otherwise().to("mock:unknown")
                    .end();
            }
        };
    }


}
