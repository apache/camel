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
package org.apache.camel.component.rest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestProducerBindingProcessorTest {

    private CamelContext camelContext;

    @Mock
    private AsyncProcessor mockProcessor;

    @Mock
    private DataFormat jsonDataFormat;

    @Mock
    private DataFormat xmlDataFormat;

    @Mock
    private DataFormat outJsonDataFormat;

    @Mock
    private DataFormat outXmlDataFormat;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        camelContext.stop();
    }

    @Test
    void testProcessWithEmptyBody() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", true, null);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }

    @Test
    void testProcessWithStringBody() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", true, null);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody("{\"name\": \"test\"}");

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }

    @Test
    void testProcessWithByteArrayBody() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", true, null);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody("{\"name\": \"test\"}".getBytes(StandardCharsets.UTF_8));

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }

    @Test
    void testProcessWithInputStreamBody() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", true, null);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(new ByteArrayInputStream("{\"name\": \"test\"}".getBytes(StandardCharsets.UTF_8)));

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }

    @Test
    void testProcessWithBindingModeOff() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, null, null, null, null, "off", true, null);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(new Object());

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }

    @Test
    void testProcessWithBindingModeAuto() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, null, null, null, null, "auto", true, null);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(new Object());

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }

    @Test
    void testProcessWithJsonContentType() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "auto", true, null);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody("test");
        exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/json");

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }

    @Test
    void testProcessWithXmlContentType() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "auto", true, null);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody("test");
        exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/xml");

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }

    @Test
    void testProcessWithOutType() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }

    @Test
    void testProcessWithSkipBindingOnErrorCodeEnabled() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody("test");
        exchange.getMessage().setHeader(RestConstants.HTTP_RESPONSE_CODE, 500);

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }

    @Test
    void testProcessWithSkipBindingOnErrorCodeDisabled() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", false, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody("test");
        exchange.getMessage().setHeader(RestConstants.HTTP_RESPONSE_CODE, 500);

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }

    @Test
    void testProcessWithNoDataFormats() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, null, null, null, null, "json", true, null);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody("plain text");

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }

    @Test
    void testProcessWithJsonBindingModeNoXml() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, null,
                outJsonDataFormat, null, "json", true, null);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody("test");
        exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/json");

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }

    @Test
    void testProcessWithXmlBindingModeNoJson() {
        when(mockProcessor.process(any(Exchange.class), any(AsyncCallback.class))).thenReturn(true);

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, null, xmlDataFormat,
                null, outXmlDataFormat, "xml", true, null);

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody("test");
        exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/xml");

        boolean result = processor.process(exchange, doneSync -> {
        });

        assertThat(result).isTrue();
    }
}
