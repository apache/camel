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

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * Tests for extracting email attachments.
 *
 * @author simonvandersluis
 */
public class MailExtractAttachmentsTest extends CamelTestSupport {

  @Test
  public void testExtractAttachments() throws Exception {

    // clear mailbox
    Mailbox.clearAll();

    // create an exchange with a normal body and attachment to be produced as email
    Endpoint endpoint = context.getEndpoint("smtp://james@mymailserver.com?password=secret");

    // create the exchange with the mail message that is multipart with a file and a Hello World text/plain message.
    Exchange exchange = endpoint.createExchange();
    Message in = exchange.getIn();
    in.setBody("Hello World");
    in.addAttachment("logo.jpeg", new DataHandler(new FileDataSource("src/test/data/logo.jpeg")));
    in.addAttachment("license.txt", new DataHandler(new FileDataSource("src/main/resources/META-INF/LICENSE.txt")));

    Producer producer = endpoint.createProducer();
    producer.start();
    producer.process(exchange);

    Thread.sleep(2000);

    MockEndpoint mock = getMockEndpoint("mock:split");
    mock.expectedMessageCount(2);

    mock.assertIsSatisfied();

    Message first = mock.getReceivedExchanges().get(0).getIn();
    Message second = mock.getReceivedExchanges().get(1).getIn();

    // check it's no longer an attachment, but is the message body
    assertEquals(0, first.getAttachments().size());
    assertEquals(0, second.getAttachments().size());

    assertEquals("logo.jpeg", first.getHeader("CamelSplitAttachmentName"));
    assertEquals("license.txt", second.getHeader("CamelSplitAttachmentName"));

    byte[] expected1 = IOUtils.toByteArray(new FileDataSource("src/test/data/logo.jpeg").getInputStream());
    byte[] expected2 = IOUtils.toByteArray(new FileDataSource("src/main/resources/META-INF/LICENSE.txt").getInputStream());

    assertArrayEquals(expected1, (byte[]) first.getBody());
    assertArrayEquals(expected2, (byte[]) second.getBody());
  }

  @Override
  protected RouteBuilder createRouteBuilder() throws Exception {
    return new RouteBuilder() {
      @Override
      public void configure() throws Exception {
        // START SNIPPET: e1
        from("pop3://james@mymailserver.com?password=secret&consumer.delay=1000")
                .to("log:email")
                        // use the SplitAttachmentsExpression which will split the message per attachment
                .split(new ExtractAttachmentsExpression())
                        // each message going to this mock has a single attachment
                .to("mock:split")
                .end();
        // END SNIPPET: e1
      }
    };
  }
}
