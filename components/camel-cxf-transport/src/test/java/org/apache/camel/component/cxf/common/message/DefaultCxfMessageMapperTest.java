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
package org.apache.camel.component.cxf.common.message;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.transport.CamelTransportConstants;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.cxf.common.security.SimplePrincipal;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.security.SecurityContext;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultCxfMessageMapperTest extends Assert {

    @Test
    public void testRequestUriAndPath() {
        final String requestURI = "/path;a=b";
        final String requestPath = "/path";

        DefaultCxfMessageMapper mapper = new DefaultCxfMessageMapper();

        Exchange camelExchange = setupCamelExchange(requestURI, requestPath, null);
        Message cxfMessage = mapper.createCxfMessageFromCamelExchange(
            camelExchange, mock(HeaderFilterStrategy.class));

        assertEquals(requestURI, cxfMessage.get(Message.REQUEST_URI).toString());
        assertEquals(requestPath, cxfMessage.get(Message.BASE_PATH).toString());
    }
    
    @Test
    public void testSecurityContext() {
        DefaultCxfMessageMapper mapper = new DefaultCxfMessageMapper();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getUserPrincipal()).thenReturn(new SimplePrincipal("barry"));
        when(request.isUserInRole("role1")).thenReturn(true);
        when(request.isUserInRole("role2")).thenReturn(false);
        Exchange camelExchange = setupCamelExchange("/", "/", request);
        
        Message cxfMessage = mapper.createCxfMessageFromCamelExchange(
            camelExchange, mock(HeaderFilterStrategy.class));
        SecurityContext sc = cxfMessage.get(SecurityContext.class);
        assertNotNull(sc);
        assertEquals("barry", sc.getUserPrincipal().getName());
        assertTrue(sc.isUserInRole("role1"));
        assertFalse(sc.isUserInRole("role2"));
    }

    private Exchange setupCamelExchange(String requestURI, String requestPath, HttpServletRequest request) {
        org.apache.camel.Message camelMessage = mock(org.apache.camel.Message.class);
        Exchange camelExchange = mock(Exchange.class);
        when(camelExchange.getProperty(CamelTransportConstants.CXF_EXCHANGE,
            org.apache.cxf.message.Exchange.class)).thenReturn(new ExchangeImpl());
        when(camelExchange.hasOut()).thenReturn(false);
        when(camelExchange.getIn()).thenReturn(camelMessage);
        when(camelMessage.getHeaders()).thenReturn(Collections.emptyMap());
        when(camelMessage.getHeader(Exchange.CONTENT_TYPE, String.class)).thenReturn(null);
        when(camelMessage.getHeader("Accept", String.class)).thenReturn(null);
        when(camelMessage.getHeader(Exchange.HTTP_CHARACTER_ENCODING, String.class)).thenReturn(null);
        when(camelMessage.getHeader(Exchange.CHARSET_NAME, String.class)).thenReturn(null);
        when(camelMessage.getHeader(Exchange.HTTP_URI, String.class)).thenReturn(requestURI);
        when(camelMessage.getHeader(Exchange.HTTP_PATH, String.class)).thenReturn(requestPath);
        when(camelMessage.getHeader(Exchange.HTTP_BASE_URI, String.class)).thenReturn(requestPath);
        when(camelMessage.getHeader(Exchange.HTTP_METHOD, String.class)).thenReturn("GET");
        when(camelMessage.getHeader(Exchange.HTTP_QUERY, String.class)).thenReturn("");
        when(camelMessage.getHeader(Exchange.HTTP_SERVLET_REQUEST)).thenReturn(request);
        when(camelMessage.getHeader(Exchange.HTTP_SERVLET_RESPONSE)).thenReturn(null);
        when(camelMessage.getBody(InputStream.class)).thenReturn(new ByteArrayInputStream("".getBytes()));
        return camelExchange;
    }
}
