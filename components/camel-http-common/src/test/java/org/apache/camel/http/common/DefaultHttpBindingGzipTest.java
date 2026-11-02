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
package org.apache.camel.http.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A gzip response writes the body of the message it is given (not the body of the IN message), and a body that is not a
 * stream or byte array is written as its String form.
 */
class DefaultHttpBindingGzipTest {

    private DefaultCamelContext context;
    private final ByteArrayOutputStream written = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private HttpServletResponse response() throws IOException {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getBufferSize()).thenReturn(8192);
        when(response.getOutputStream()).thenReturn(new CapturingServletOutputStream(written));
        return response;
    }

    private String gunzipped() throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(written.toByteArray()))) {
            return new String(gis.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void writesTheBodyOfTheReplyMessageNotTheRequestStream() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        // the request body is a stream, the reply is another message
        exchange.getIn().setBody(new ByteArrayInputStream("the request".getBytes(StandardCharsets.UTF_8)));
        Message reply = new DefaultMessage(exchange);
        reply.setBody("the reply");

        new DefaultHttpBinding().doWriteGZIPResponse(reply, response(), exchange);

        assertEquals("the reply", gunzipped());
    }

    @Test
    void writesAMapBodyAsItsStringForm() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(Map.of("a", 1));

        new DefaultHttpBinding().doWriteGZIPResponse(exchange.getMessage(), response(), exchange);

        assertEquals("{a=1}", gunzipped());
    }

    private static final class CapturingServletOutputStream extends ServletOutputStream {

        private final ByteArrayOutputStream target;

        private CapturingServletOutputStream(ByteArrayOutputStream target) {
            this.target = target;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // not used
        }

        @Override
        public void write(int b) {
            target.write(b);
        }
    }
}
