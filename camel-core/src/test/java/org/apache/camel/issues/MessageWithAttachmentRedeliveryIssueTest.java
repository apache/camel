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
package org.apache.camel.issues;

import java.io.File;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultAttachment;

/**
 *
 */
public class MessageWithAttachmentRedeliveryIssueTest extends ContextTestSupport {

    public void testMessageWithAttachmentRedeliveryIssue() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
                exchange.getIn().addAttachment("message1.xml", new DataHandler(new FileDataSource(new File("src/test/data/message1.xml"))));
                exchange.getIn().addAttachmentObject("message2.xml", new DefaultAttachment(new FileDataSource(new File("src/test/data/message2.xml"))));
            }
        });

        assertMockEndpointsSatisfied();

        Message msg = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getIn();
        assertNotNull(msg);

        assertEquals("Hello World", msg.getBody());
        assertTrue(msg.hasAttachments());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).maximumRedeliveries(3).redeliveryDelay(0);

                from("direct:start")
                    .process(new Processor() {
                        private int counter;
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            if (counter++ < 2) {
                                throw new IllegalArgumentException("Forced");
                            }
                        }
                    }).to("mock:result");

            }
        };
    }
}
