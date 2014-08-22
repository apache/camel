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
package org.apache.camel.itest.osgi.mail;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;


import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.mock_javamail.Mailbox;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.workingDirectory;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
@Ignore("Does not work anymore as the mock javamail does not kick in as mail provider")
public class MailRouteTest extends OSGiIntegrationTestSupport {

    @Test
    public void testSendAndReceiveMails() throws Exception {
        Mailbox.clearAll();

        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedBodiesReceived("hello world!");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("reply-to", "route-test-reply@localhost");
        template.sendBodyAndHeaders("smtp://route-test-james@localhost", "hello world!", headers);

        // lets test the first sent worked
        assertMailboxReceivedMessages("route-test-james@localhost");

        // lets sleep to check that the mail poll does not redeliver duplicate mails
        Thread.sleep(3000);

        // lets test the receive worked
        resultEndpoint.assertIsSatisfied();

        // Validate that the headers were preserved.
        Exchange exchange = resultEndpoint.getReceivedExchanges().get(0);
        String replyTo = (String)exchange.getIn().getHeader("reply-to");
        assertEquals("route-test-reply@localhost", replyTo);

        assertMailboxReceivedMessages("route-test-copy@localhost");
    }

    protected void assertMailboxReceivedMessages(String name) throws Exception {
        Mailbox mailbox = Mailbox.get(name);
        assertEquals(name + " should have received 1 mail", 1, mailbox.size());

        Message message = mailbox.get(0);
        assertNotNull(name + " should have received at least one mail!", message);
        Object content = message.getContent();
        assertNotNull("The content should not be null!", content);
        if (content instanceof InputStream) {
            assertEquals("hello world!", IOConverter.toString((InputStream)content, null));
        } else {
            assertEquals("hello world!", message.getContent());
        }
        assertEquals("camel@localhost", message.getFrom()[0].toString());
        boolean found = false;
        for (Address adr : message.getRecipients(RecipientType.TO)) {
            if (name.equals(adr.toString())) {
                found = true;
            }
        }
        assertTrue("Should have found the recipient to in the mail: " + name, found);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("pop3://route-test-james@localhost?consumer.delay=1000")
                    .to("direct:a");

                // must use fixed to option to send the mail to the given receiver, as we have polled
                // a mail from a mailbox where it already has the 'old' To as header value
                // here we send the mail to 2 receivers. notice we can use a plain string with semi colon
                // to separate the mail addresses
                from("direct:a")
                    .setHeader("to", constant("route-test-result@localhost; route-test-copy@localhost"))
                    .to("smtp://localhost");

                from("pop3://route-test-result@localhost?consumer.delay=1000")
                    .convertBodyTo(String.class).to("mock:result");
            }
        };
    }
    
    @Configuration
    public static Option[] configure() throws Exception {
        Option[] options = combine(

            getDefaultCamelKarafOptions(),
            // using the features to install the camel components
            loadCamelFeatures("jetty"),

            // using the java mail API bundle
            mavenBundle().groupId("org.apache.servicemix.specs").artifactId("org.apache.servicemix.specs.javamail-api-1.4").version("1.3.0"),
                          
            mavenBundle().groupId("org.apache.camel").artifactId("camel-mail").versionAsInProject(),
            
            // Added the mock_java_mail bundle for testing
            // just using the mock_java_mail version for testing
            mavenBundle().groupId("org.apache.camel.tests").artifactId("org.apache.camel.tests.mock-javamail_1.7").version("2.8.3"),
            
            workingDirectory("target/paxrunner/"),

            // does not work in felix
            equinox());
        
        return options;
    }

}
