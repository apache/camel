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

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * @version 
 */
public class MailCollectionHeaderTest extends CamelTestSupport {

    @Test
    public void testMailHeaderWithCollection() throws Exception {
        Mailbox.clearAll();

        String[] foo = new String[] {"Carlsberg", "Heineken"};
        template.sendBodyAndHeader("direct:a", "Hello World", "beers", foo);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).header("beers").isNotNull();

        mock.assertIsSatisfied();

        Object beers = mock.getReceivedExchanges().get(0).getIn().getHeader("beers");
        assertNotNull(beers);
        List<?> list = assertIsInstanceOf(List.class, beers);
        assertEquals("Carlsberg", list.get(0));
        assertEquals("Heineken", list.get(1));
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a").to("smtp://localhost?username=james@localhost");

                from("pop3://localhost?username=james&password=secret&consumer.initialDelay=100&consumer.delay=100").to("mock:result");
            }
        };
    }

}
