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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Unit test to verify that message headers override pre configuration.
 */
public class MailHeaderOverrulePreConfigurationRecipientsTest extends CamelTestSupport {

    @Test
    public void testSendWithRecipientsInHeaders() throws Exception {
        Mailbox.clearAll();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("to", "claus@localhost");
        mock.expectedHeaderReceived("cc", "willem@localhost");
        mock.expectedHeaderReceived("bcc", "hadrian@localhost");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("to", "claus@localhost");
        headers.put("cc", "willem@localhost");
        headers.put("bcc", "hadrian@localhost");

        template.sendBodyAndHeaders("smtp://james3@localhost", "Hello World", headers);

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("pop3://claus@localhost?to=someone@outhere.com&cc=none@world.com&consumer.initialDelay=100&consumer.delay=100").to("mock:result");
            }
        };
    }

}
