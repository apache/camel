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
package org.apache.camel.processor;

import java.io.ByteArrayInputStream;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Date;
import java.util.Locale;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class ConvertBodyTest extends ContextTestSupport {
    
    public void testConvertBodyTo() {
        try {
            context.addRoutes(new RouteBuilder() {
                public void configure() {
                    // set an invalid charset
                    from("direct:invalid").convertBodyTo(String.class, "ASSI").to("mock:endpoint");
                }
            });
            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertIsInstanceOf(UnsupportedCharsetException.class, e.getCause());
        }
    }

    public void testConvertBodyCharset() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:foo").convertBodyTo(byte[].class, "iso-8859-1").to("mock:foo");
            }
        });

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        // do not propagate charset to avoid side effects with double conversion etc
        getMockEndpoint("mock:foo").message(0).exchangeProperty(Exchange.CHARSET_NAME).isNull();

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testConvertToInteger() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived(11);

        template.sendBody("direct:start", "11");

        assertMockEndpointsSatisfied();
    }

    public void testConvertNullBody() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.message(0).body().isNull();

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();
    }

    public void testConvertFailed() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:invalid", "11");
            fail("Should have thrown an exception");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof InvalidPayloadException);
        }

        assertMockEndpointsSatisfied();
    }

    public void testConvertToBytesCharset() throws Exception {
        byte[] body = "Hello World".getBytes("iso-8859-1");

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived(body);

        template.sendBody("direct:charset", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testConvertToStringCharset() throws Exception {

        String body = "Hello World";

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived(body);

        template.sendBody("direct:charset3", new ByteArrayInputStream(body.getBytes("utf-16")));

        assertMockEndpointsSatisfied();
    }

    public void testConvertToBytesCharsetFail() throws Exception {
        byte[] body = "Hello World".getBytes("utf-8");

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived(body);

        template.sendBody("direct:charset2", "Hello World");

        // should NOT be okay as we expected utf-8 but got it in utf-16
        result.assertIsNotSatisfied();
    }

    public void testConvertToStringCharsetFail() throws Exception {

        // does not work on AIX
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        boolean aix = osName.indexOf("aix") > -1;
        if (aix) {
            return;
        }

        String body = "Hell\u00F6 W\u00F6rld";

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived(body);

        template.sendBody("direct:charset3", new ByteArrayInputStream(body.getBytes("utf-8")));

        // should NOT be okay as we expected utf-8 but got it in utf-16
        result.assertIsNotSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").convertBodyTo(Integer.class).to("mock:result");
                from("direct:invalid").convertBodyTo(Date.class).to("mock:result");
                from("direct:charset").convertBodyTo(byte[].class, "iso-8859-1").to("mock:result");
                from("direct:charset2").convertBodyTo(byte[].class, "utf-16").to("mock:result");
                from("direct:charset3").convertBodyTo(String.class, "utf-16").to("mock:result");
            }
        };
    }

}
