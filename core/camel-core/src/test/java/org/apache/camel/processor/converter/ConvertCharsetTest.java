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
package org.apache.camel.processor.converter;

import java.nio.charset.StandardCharsets;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

/**
 * The charset configured on convertBodyTo, convertHeaderTo and convertVariableTo is used even when the message has a
 * charset header, and the exchange charset is restored also when the conversion fails.
 */
class ConvertCharsetTest extends ContextTestSupport {

    private static final byte[] CAFE = "café".getBytes(StandardCharsets.UTF_8);

    @Test
    void testConvertBodyWithCharsetHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("café");
        mock.expectedHeaderReceived(Exchange.CHARSET_NAME, "ISO-8859-1");
        mock.message(0).exchangeProperty(Exchange.CHARSET_NAME).isNull();

        template.sendBodyAndHeader("direct:body", CAFE, Exchange.CHARSET_NAME, "ISO-8859-1");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testConvertHeaderWithCharsetHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedHeaderReceived("data", "café");
        mock.expectedHeaderReceived(Exchange.CHARSET_NAME, "ISO-8859-1");
        mock.message(0).exchangeProperty(Exchange.CHARSET_NAME).isNull();

        template.send("direct:header", e -> {
            e.getMessage().setHeader("data", CAFE);
            e.getMessage().setHeader(Exchange.CHARSET_NAME, "ISO-8859-1");
        });

        assertMockEndpointsSatisfied();
    }

    @Test
    void testConvertVariableWithCharsetHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedVariableReceived("data", "café");
        mock.expectedHeaderReceived(Exchange.CHARSET_NAME, "ISO-8859-1");
        mock.message(0).exchangeProperty(Exchange.CHARSET_NAME).isNull();

        template.send("direct:variable", e -> {
            e.setVariable("data", CAFE);
            e.getMessage().setHeader(Exchange.CHARSET_NAME, "ISO-8859-1");
        });

        assertMockEndpointsSatisfied();
    }

    @Test
    void testConvertBodyFailedRestoresCharset() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedMessageCount(2);
        mock.message(0).exchangeProperty(Exchange.CHARSET_NAME).isNull();
        mock.message(1).exchangeProperty(Exchange.CHARSET_NAME).isEqualTo("ISO-8859-1");
        mock.message(1).header(Exchange.CHARSET_NAME).isEqualTo("ISO-8859-1");

        template.sendBody("direct:bodyFail", "abc");
        template.send("direct:bodyFail", e -> {
            e.setProperty(Exchange.CHARSET_NAME, "ISO-8859-1");
            e.getMessage().setHeader(Exchange.CHARSET_NAME, "ISO-8859-1");
            e.getMessage().setBody("abc");
        });

        assertMockEndpointsSatisfied();
    }

    @Test
    void testConvertHeaderFailedRestoresCharset() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedMessageCount(1);
        mock.message(0).exchangeProperty(Exchange.CHARSET_NAME).isNull();

        template.sendBodyAndHeader("direct:headerFail", "Hello", "data", "abc");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testConvertVariableFailedRestoresCharset() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedMessageCount(1);
        mock.message(0).exchangeProperty(Exchange.CHARSET_NAME).isNull();

        template.send("direct:variableFail", e -> e.setVariable("data", "abc"));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class).handled(true).to("mock:error");

                from("direct:body").convertBodyTo(String.class, "UTF-8").to("mock:result");
                from("direct:header").convertHeaderTo("data", String.class, "UTF-8").to("mock:result");
                from("direct:variable").convertVariableTo("data", String.class, "UTF-8").to("mock:result");

                from("direct:bodyFail").convertBodyTo(Integer.class, "UTF-16").to("mock:result");
                from("direct:headerFail").convertHeaderTo("data", Integer.class, "UTF-16").to("mock:result");
                from("direct:variableFail").convertVariableTo("data", Integer.class, "UTF-16").to("mock:result");
            }
        };
    }
}
