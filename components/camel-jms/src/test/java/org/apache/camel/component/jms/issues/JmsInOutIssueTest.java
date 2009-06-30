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
package org.apache.camel.component.jms.issues;

import java.util.concurrent.Future;

import static org.apache.activemq.camel.component.ActiveMQComponent.activeMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsConstants;

/**
 * @version $Revision$
 */
public class JmsInOutIssueTest extends ContextTestSupport {

    public void testInOutWithRequestBody() throws Exception {
        String reply = template.requestBody("activemq:queue:in", "Hello World", String.class);
        assertEquals("Bye World", reply);
    }

    public void testInOutWithAsyncRequestBody() throws Exception {
        Future<String> reply = template.asyncRequestBody("activemq:queue:in", "Hello World", String.class);
        assertEquals("Bye World", reply.get());
    }

    public void testInOutWithSendExchange() throws Exception {
        Exchange out = template.send("activemq:queue:in", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
        });

        assertEquals("Bye World", out.getOut().getBody());
    }

    public void testInOutWithAsyncSendExchange() throws Exception {
        Future<Exchange> out = template.asyncSend("activemq:queue:in", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                exchange.getIn().setBody("Hello World");
            }
        });

        assertEquals("Bye World", out.get().getOut().getBody());
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.addComponent("activemq", activeMQComponent("vm://localhost?broker.persistent=false&broker.useJmx=false"));
        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:queue:in").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        assertEquals("Should be InOut", ExchangePattern.InOut, exchange.getPattern());
                        assertNotNull("There should be a reply destination", exchange.getIn().getHeader(JmsConstants.JMS_REPLY_DESTINATION));

                        exchange.getOut().setBody("Bye World");
                    }
                });
            }
        };
    }

}
