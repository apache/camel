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
package org.apache.camel.component.mail;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.Mailbox.MailboxUser;
import org.apache.camel.component.mail.Mailbox.Protocol;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MailCollectionHeaderTest extends CamelTestSupport {
    private static final MailboxUser james = Mailbox.getOrCreateUser("james", "secret");

    @Test
    public void testMailHeaderWithCollection() throws Exception {
        Mailbox.clearAll();

        String[] foo = new String[] { "Carlsberg", "Heineken" };
        template.sendBodyAndHeader("direct:a", "Hello World", "beers", foo);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World\r\n");
        mock.message(0).header("beers").isNotNull();

        mock.assertIsSatisfied();

        Object beers = mock.getReceivedExchanges().get(0).getIn().getHeader("beers");
        assertNotNull(beers);
        List<?> list = assertIsInstanceOf(List.class, beers);
        assertEquals("Carlsberg", list.get(0));
        assertEquals("Heineken", list.get(1));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").to(james.uriPrefix(Protocol.smtp));

                from("pop3://localhost:" + Mailbox.getPort(Protocol.pop3)
                     + "?username=james&password=secret&initialDelay=100&delay=100").to("mock:result");
            }
        };
    }

}
