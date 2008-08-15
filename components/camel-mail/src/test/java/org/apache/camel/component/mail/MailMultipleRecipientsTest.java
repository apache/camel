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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Unit test to verift that we can have multiple recipients in To, CC and BCC
 */
public class MailMultipleRecipientsTest extends ContextTestSupport {

    public void testSendWithMultipleRecipientsInHeader() throws Exception {
        Mailbox.clearAll();

        // START SNIPPET: e1
        Map<String, Object> headers = new HashMap<String, Object>();
        // test with both comma and semi colon as Camel supports both kind of separators
        headers.put("to", "claus@localhost, willem@localhost ; hadrian@localhost");
        headers.put("cc", "james@localhost");

        assertMailbox("claus");
        assertMailbox("willem");
        assertMailbox("hadrian");

        template.sendBodyAndHeaders("smtp://localhost", "Hello World", headers);
        // END SNIPPET: e1

        assertMockEndpointsSatisifed();
    }

    public void testSendWithMultipleRecipientsPreConfigured() throws Exception {
        Mailbox.clearAll();

        assertMailbox("claus");
        assertMailbox("willem");

        // START SNIPPET: e2
        // here we have preconfigued the to recievs to claus and willem. Notice we use comma to seperate
        // the two recipeients. Camel also support using colon as seperator char
        template.sendBody("smtp://localhost?To=claus@localhost,willem@localhost&CC=james@localhost", "Hello World");
        // END SNIPPET: e2

        assertMockEndpointsSatisifed();
    }

    private void assertMailbox(String name) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:" + name);
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("cc", "james@localhost");
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("pop3://claus@localhost?consumer.delay=1000").to("mock:claus");

                from("pop3://willem@localhost?consumer.delay=1000").to("mock:willem");

                from("pop3://hadrian@localhost?consumer.delay=1000").to("mock:hadrian");
            }
        };
    }

}
