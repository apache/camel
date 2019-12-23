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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

public class FromFileSendMailTest extends CamelTestSupport {

    @Test
    public void testSendFileAsMail() throws Exception {
        Mailbox.clearAll();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.message(0).body().isInstanceOf(GenericFile.class);

        template.sendBodyAndHeader("file://target/mailtext", "Hi how are you", Exchange.FILE_NAME, "mail.txt");

        assertMockEndpointsSatisfied();

        Mailbox mailbox = Mailbox.get("james@localhost");
        assertEquals(1, mailbox.size());
        Object body = mailbox.get(0).getContent(); 
        assertEquals("Hi how are you", body);
        Object subject = mailbox.get(0).getSubject();
        assertEquals("Hello World", subject);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/mailtext?initialDelay=100&delay=100")
                    .setHeader("Subject", constant("Hello World"))
                    .setHeader("To", constant("james@localhost"))
                    .setHeader("From", constant("claus@localhost"))
                    .to("smtp://localhost?password=secret&username=claus&initialDelay=100&delay=100", "mock:result");
            }
        };
    }

}
