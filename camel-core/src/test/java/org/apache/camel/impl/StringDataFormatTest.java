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
package org.apache.camel.impl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.TestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test of the string data format.
 */
public class StringDataFormatTest extends TestSupport {

    private CamelContext context;
    private ProducerTemplate template;

    protected void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.setTracing(true);
        template = context.createProducerTemplate();
        template.start();
    }

    protected void tearDown() throws Exception {
        template.stop();
        context.stop();
    }

    public void testMarshalUTF8() throws Exception {
        // NOTE: We are using a processor to do the assertions as the mock endpoint (Camel) does not yet support
        // type conversion using byte and strings where you can set a charset encoding

        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        final String title = "Hello Thai Elephant \u0E08";

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().string("UTF-8").process(new MyBookProcessor("UTF-8", title));
            }
        });
        context.start();

        MyBook book = new MyBook();
        book.setTitle(title);

        template.sendBody("direct:start", book);
    }

    public void testMarshalNoEncoding() throws Exception {
        // NOTE: We are using a processor to do the assertions as the mock endpoint (Camel) does not yet support
        // type conversion using byte and strings where you can set a charset encoding

        final String title = "Hello World";

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").marshal().string().process(new MyBookProcessor(null, title));
            }
        });
        context.start();

        MyBook book = new MyBook();
        book.setTitle(title);

        template.sendBody("direct:start", book);
    }


    public void testUnmarshalUTF8() throws Exception {
        // NOTE: Here we can use a MockEndpoint as we unmarshal the inputstream to String

        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        final String title = "Hello Thai Elephant \u0E08";

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").unmarshal().string("UTF-8").to("mock:unmarshal");
            }
        });
        context.start();

        byte[] bytes = title.getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(bytes);

        template.sendBody("direct:start", in);

        MockEndpoint mock = context.getEndpoint("mock:unmarshal", MockEndpoint.class);
        mock.setExpectedMessageCount(1);
        mock.expectedBodiesReceived(title);
    }

    public void testUnmarshalNoEncoding() throws Exception {
        // NOTE: Here we can use a MockEndpoint as we unmarshal the inputstream to String

        final String title = "Hello World";

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:start").unmarshal().string().to("mock:unmarshal");
            }
        });
        context.start();

        byte[] bytes = title.getBytes();
        InputStream in = new ByteArrayInputStream(bytes);

        template.sendBody("direct:start", in);

        MockEndpoint mock = context.getEndpoint("mock:unmarshal", MockEndpoint.class);
        mock.setExpectedMessageCount(1);
        mock.expectedBodiesReceived(title);
    }

    private static class MyBookProcessor implements Processor {

        private String encoding;
        private String title;

        MyBookProcessor(String encoding, String title) {
            this.encoding = encoding;
            this.title = title;
        }

        public void process(Exchange exchange) throws Exception {
            byte[] body = exchange.getIn().getBody(byte[].class);

            String text;
            if (encoding != null) {
                text = new String(body, encoding);
            } else {
                text = new String(body);
            }

            // does the testing
            assertEquals(text, title);
        }
    }

    private static class MyBook {
        private String title;

        public void setTitle(String title) {
            this.title = title;
        }

        public String toString() {
            // Camel will fallback to object toString converter and thus we get this text
            return title;
        }
    }

}
