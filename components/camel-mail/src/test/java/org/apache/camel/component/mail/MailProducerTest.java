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
package org.apache.camel.component.mail;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

public class MailProducerTest extends CamelTestSupport {

    @Test
    public void testProduer() throws Exception {
        Mailbox.clearAll();
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Message ", "To", "someone@localhost");
        assertMockEndpointsSatisfied();
        // need to check the message header
        Exchange exchange = getMockEndpoint("mock:result").getExchanges().get(0);
        assertNotNull("The message id should not be null", exchange.getIn().getHeader(MailConstants.MAIL_MESSAGE_ID));

        Mailbox box = Mailbox.get("someone@localhost");
        assertEquals(1, box.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("smtp://camel@localhost", "mock:result");
            }
        };
    }

}
