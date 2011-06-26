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

import java.util.HashMap;
import java.util.Map;
import javax.mail.Message;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Unit test for Mail using camel headers to set recipient subject.
 */
public class RawMailMessageTest extends CamelTestSupport {

    @Test
    public void testGetRawJavaMailMessage() throws Exception {
        Mailbox.clearAll();

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("To", "davsclaus@apache.org");
        map.put("From", "jstrachan@apache.org");
        map.put("Subject", "Camel rocks");

        String body = "Hello Claus.\nYes it does.\n\nRegards James.";

        getMockEndpoint("mock:mail").expectedMessageCount(1);
        template.sendBodyAndHeaders("smtp://davsclaus@apache.org", body, map);
        assertMockEndpointsSatisfied();

        Exchange exchange = getMockEndpoint("mock:mail").getReceivedExchanges().get(0);

        // START SNIPPET: e1
        // get access to the raw javax.mail.Message as shown below
        Message javaMailMessage = exchange.getIn(MailMessage.class).getMessage();
        assertNotNull(javaMailMessage);

        assertEquals("Camel rocks", javaMailMessage.getSubject());
        // END SNIPPET: e1
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("pop3://davsclaus@apache.org").to("mock:mail");
            }
        };
    }
}