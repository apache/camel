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

import java.util.concurrent.atomic.AtomicBoolean;

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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestProducerBindingCallbackTest {

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
    void testCallbackWithJsonResponse() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            Exchange exchange = invocation.getArgument(0);
            AsyncCallback callback = invocation.getArgument(1);
            exchange.getMessage().setBody("{\"result\": \"ok\"}");
            exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/json");
            callback.done(true);
            return true;
        }).when(mockProcessor).process(any(Exchange.class), any(AsyncCallback.class));

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        processor.process(exchange, doneSync -> callbackCalled.set(true));

        assertThat(callbackCalled.get()).isTrue();
    }

    @Test
    void testCallbackWithXmlResponse() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            Exchange exchange = invocation.getArgument(0);
            AsyncCallback callback = invocation.getArgument(1);
            exchange.getMessage().setBody("<result>ok</result>");
            exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/xml");
            callback.done(true);
            return true;
        }).when(mockProcessor).process(any(Exchange.class), any(AsyncCallback.class));

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "xml", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        processor.process(exchange, doneSync -> callbackCalled.set(true));

        assertThat(callbackCalled.get()).isTrue();
    }

    @Test
    void testCallbackSkipsBindingOnErrorCode() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            Exchange exchange = invocation.getArgument(0);
            AsyncCallback callback = invocation.getArgument(1);
            exchange.getMessage().setBody("Error");
            exchange.getMessage().setHeader(RestConstants.HTTP_RESPONSE_CODE, 500);
            exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/json");
            callback.done(true);
            return true;
        }).when(mockProcessor).process(any(Exchange.class), any(AsyncCallback.class));

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        processor.process(exchange, doneSync -> callbackCalled.set(true));

        assertThat(callbackCalled.get()).isTrue();
        // Body should not have been transformed due to error code skip
        assertThat(exchange.getMessage().getBody(String.class)).isEqualTo("Error");
    }

    @Test
    void testCallbackWithEmptyResponseBody() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            Exchange exchange = invocation.getArgument(0);
            AsyncCallback callback = invocation.getArgument(1);
            exchange.getMessage().setBody(null);
            exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/json");
            callback.done(true);
            return true;
        }).when(mockProcessor).process(any(Exchange.class), any(AsyncCallback.class));

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        processor.process(exchange, doneSync -> callbackCalled.set(true));

        assertThat(callbackCalled.get()).isTrue();
    }

    @Test
    void testCallbackWithExceptionInExchange() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            Exchange exchange = invocation.getArgument(0);
            AsyncCallback callback = invocation.getArgument(1);
            exchange.setException(new RuntimeException("Test error"));
            callback.done(true);
            return true;
        }).when(mockProcessor).process(any(Exchange.class), any(AsyncCallback.class));

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        processor.process(exchange, doneSync -> callbackCalled.set(true));

        assertThat(callbackCalled.get()).isTrue();
        assertThat(exchange.getException()).isNotNull();
    }

    @Test
    void testCallbackWithBindingModeOff() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            Exchange exchange = invocation.getArgument(0);
            AsyncCallback callback = invocation.getArgument(1);
            exchange.getMessage().setBody("{\"result\": \"ok\"}");
            exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/json");
            callback.done(true);
            return true;
        }).when(mockProcessor).process(any(Exchange.class), any(AsyncCallback.class));

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "off", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        processor.process(exchange, doneSync -> callbackCalled.set(true));

        assertThat(callbackCalled.get()).isTrue();
    }

    @Test
    void testCallbackWithBindingModeAuto() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            Exchange exchange = invocation.getArgument(0);
            AsyncCallback callback = invocation.getArgument(1);
            exchange.getMessage().setBody("{\"result\": \"ok\"}");
            exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/json");
            callback.done(true);
            return true;
        }).when(mockProcessor).process(any(Exchange.class), any(AsyncCallback.class));

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "auto", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        processor.process(exchange, doneSync -> callbackCalled.set(true));

        assertThat(callbackCalled.get()).isTrue();
    }

    @Test
    void testCallbackWithNoContentType() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            Exchange exchange = invocation.getArgument(0);
            AsyncCallback callback = invocation.getArgument(1);
            exchange.getMessage().setBody("{\"result\": \"ok\"}");
            // No content type set
            callback.done(true);
            return true;
        }).when(mockProcessor).process(any(Exchange.class), any(AsyncCallback.class));

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        processor.process(exchange, doneSync -> callbackCalled.set(true));

        assertThat(callbackCalled.get()).isTrue();
        // Content-Type should be set by the callback
        assertThat(exchange.getMessage().getHeader(RestConstants.CONTENT_TYPE)).isEqualTo("application/json");
    }

    @Test
    void testCallbackWithXmlContentTypeWhenJsonMode() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            Exchange exchange = invocation.getArgument(0);
            AsyncCallback callback = invocation.getArgument(1);
            exchange.getMessage().setBody("<result>ok</result>");
            exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/xml");
            callback.done(true);
            return true;
        }).when(mockProcessor).process(any(Exchange.class), any(AsyncCallback.class));

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        processor.process(exchange, doneSync -> callbackCalled.set(true));

        assertThat(callbackCalled.get()).isTrue();
    }

    @Test
    void testCallbackWithJsonXmlBindingMode() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            Exchange exchange = invocation.getArgument(0);
            AsyncCallback callback = invocation.getArgument(1);
            exchange.getMessage().setBody("{\"result\": \"ok\"}");
            exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/json");
            callback.done(true);
            return true;
        }).when(mockProcessor).process(any(Exchange.class), any(AsyncCallback.class));

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json_xml", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        processor.process(exchange, doneSync -> callbackCalled.set(true));

        assertThat(callbackCalled.get()).isTrue();
    }

    @Test
    void testCallbackWith3xxResponseCode() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            Exchange exchange = invocation.getArgument(0);
            AsyncCallback callback = invocation.getArgument(1);
            exchange.getMessage().setBody("Redirect");
            exchange.getMessage().setHeader(RestConstants.HTTP_RESPONSE_CODE, 302);
            exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/json");
            callback.done(true);
            return true;
        }).when(mockProcessor).process(any(Exchange.class), any(AsyncCallback.class));

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        processor.process(exchange, doneSync -> callbackCalled.set(true));

        assertThat(callbackCalled.get()).isTrue();
    }

    @Test
    void testCallbackWithNoUnmarshallers() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            Exchange exchange = invocation.getArgument(0);
            AsyncCallback callback = invocation.getArgument(1);
            exchange.getMessage().setBody("{\"result\": \"ok\"}");
            exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/json");
            callback.done(true);
            return true;
        }).when(mockProcessor).process(any(Exchange.class), any(AsyncCallback.class));

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, null, null,
                null, null, "json", true, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        processor.process(exchange, doneSync -> callbackCalled.set(true));

        assertThat(callbackCalled.get()).isTrue();
    }

    @Test
    void testCallbackWithSkipBindingOnErrorCodeDisabled() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            Exchange exchange = invocation.getArgument(0);
            AsyncCallback callback = invocation.getArgument(1);
            exchange.getMessage().setBody("Error");
            exchange.getMessage().setHeader(RestConstants.HTTP_RESPONSE_CODE, 400);
            exchange.getMessage().setHeader(RestConstants.CONTENT_TYPE, "application/json");
            callback.done(true);
            return true;
        }).when(mockProcessor).process(any(Exchange.class), any(AsyncCallback.class));

        RestProducerBindingProcessor processor = new RestProducerBindingProcessor(
                mockProcessor, camelContext, jsonDataFormat, xmlDataFormat,
                outJsonDataFormat, outXmlDataFormat, "json", false, "com.example.Response");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody(null);

        processor.process(exchange, doneSync -> callbackCalled.set(true));

        assertThat(callbackCalled.get()).isTrue();
    }
}
