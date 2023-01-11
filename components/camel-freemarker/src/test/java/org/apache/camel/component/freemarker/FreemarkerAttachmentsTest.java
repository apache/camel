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
package org.apache.camel.component.freemarker;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Freemarker unit test
 */
public class FreemarkerAttachmentsTest extends CamelTestSupport {

    @Test
    public void testFreemarkerAttachments() {
        Exchange exchange = template.request("direct:a", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.setProperty("item", "7");

                AttachmentMessage am = exchange.getIn(AttachmentMessage.class);
                am.setBody("Monday");
                am.setHeader("name", "Christian");
                am.addAttachment("123", new DataHandler(new FileDataSource("pom.xml")));
            }
        });

        assertEquals("Dear Christian. You ordered item 7 on Monday.", exchange.getMessage().getBody());
        assertEquals("Christian", exchange.getMessage().getHeader("name"));

        AttachmentMessage am = exchange.getMessage(AttachmentMessage.class);
        assertNotNull(am);
        assertEquals("123", am.getAttachmentNames().iterator().next());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:a").to(
                        "freemarker:org/apache/camel/component/freemarker/example.ftl?allowTemplateFromHeader=true&allowContextMapAll=true");
                // END SNIPPET: example
            }
        };
    }
}
