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
package org.apache.camel.attachment;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeanMethodWithExchangeTest extends CamelTestSupport {
    
    @Test
    void testBeanWithAnnotationAndExchangeTest() {
        Exchange result = template.request("direct:start1", new Processor() {

            public void process(Exchange exchange) {
                AttachmentMessage m = exchange.getIn(AttachmentMessage.class);
                m.addAttachment("attachment", new DataHandler(new FileDataSource("src/test/org/apache/camel/attachment/BeanMethodWithExchangeTest.java")));
            }
            
        });

        assertTrue(result.getMessage(AttachmentMessage.class).getAttachmentObjects().containsKey("attachment2"));
        assertTrue(result.getMessage(AttachmentMessage.class).getAttachments().containsKey("attachment1"));
        assertEquals("attachmentValue1", result.getMessage(AttachmentMessage.class).getAttachmentObjects().get("attachment1").getHeader("attachmentHeader1"));
        assertFalse(result.getMessage(AttachmentMessage.class).getAttachments().containsKey("attachment"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start1").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // remove the old attachment
                        exchange.getMessage(AttachmentMessage.class).removeAttachment("attachment");
                        // and add 2 new attachments
                        Attachment att = new DefaultAttachment(new FileDataSource("src/test/org/apache/camel/attachment/BeanMethodWithExchangeTest.java"));
                        att.addHeader("attachmentHeader1", "attachmentValue1");
                        exchange.getMessage(AttachmentMessage.class).addAttachmentObject("attachment1", att);
                        exchange.getMessage(AttachmentMessage.class).addAttachment("attachment2", new DataHandler(new FileDataSource("src/test/org/apache/camel/support/attachments/BeanMethodWithExchangeTest.java")));
                    }
                });
            }
        };
    }

}
