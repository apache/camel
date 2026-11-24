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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A field of an application/x-www-form-urlencoded body without = is a name with an empty value, as in the URL-encoded
 * form parser of the WHATWG URL standard, and a header whose name is not a valid HTTP header name, such as one taken
 * from such a field, is not written to the response.
 */
class DefaultHttpBindingFormUrlEncodedTest {

    private static final String TEXT = "Hi, I'm alice@example.com. My order is late, can you refund it?";

    private final DefaultCamelContext context = new DefaultCamelContext();
    private final DefaultHttpBinding binding = new DefaultHttpBinding();

    @AfterEach
    void stopContext() {
        context.stop();
    }

    @Test
    void testFieldWithoutEqualsIsNameWithEmptyValue() throws Exception {
        Message message = readForm(TEXT);

        assertEquals("", message.getHeader(TEXT));
    }

    @Test
    void testFieldsWithAndWithoutEqualsAndEmptyFields() throws Exception {
        Message message = readForm("a=1&flag&&b=x+y%21&c=");

        assertEquals("1", message.getHeader("a"));
        assertEquals("", message.getHeader("flag"));
        assertEquals("x y!", message.getHeader("b"));
        assertEquals("", message.getHeader("c"));
        assertFalse(message.getHeaders().containsKey(""), "an empty field must not be mapped to a header");
    }

    @Test
    void testPercentNotFollowedByTwoHexDigitsIsKept() throws Exception {
        assertEquals("", readForm("Save 50% now").getHeader("Save 50% now"));
        assertEquals("", readForm("{\"discount\":\"50%\"}").getHeader("{\"discount\":\"50%\"}"));

        Message message = readForm("off=50%&price=10%25");
        assertEquals("50%", message.getHeader("off"));
        assertEquals("10%", message.getHeader("price"));
    }

    @Test
    void testInvalidHeaderNameIsNotWrittenToResponse() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message message = exchange.getMessage();
        message.setHeader(TEXT, "");
        message.setHeader("X-Valid", "value");
        HttpServletResponse response = mock(HttpServletResponse.class);

        binding.doWriteResponse(message, response, exchange);

        verify(response).addHeader("X-Valid", "value");
        verify(response, never()).addHeader(eq(TEXT), anyString());
    }

    private Message readForm(String body) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Message message = exchange.getIn();
        message.setBody(body);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getContentType()).thenReturn("application/x-www-form-urlencoded");

        binding.readFormUrlEncodedBody(request, message);
        return message;
    }
}
