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
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

public class DefaultCxfMessageMapperTest extends Assert {

    @Test
    public void testRequestUriAndPath() {
        final String requestURI = "/path;a=b";
        final String requestPath = "/path";

        DefaultCxfMessageMapper mapper = new DefaultCxfMessageMapper();

        Exchange camelExchange = setupCamelExchange(requestURI, requestPath, null);
        Message cxfMessage = mapper.createCxfMessageFromCamelExchange(
            camelExchange, EasyMock.createMock(HeaderFilterStrategy.class));

        assertEquals(requestURI, cxfMessage.get(Message.REQUEST_URI).toString());
        assertEquals(requestPath, cxfMessage.get(Message.BASE_PATH).toString());
    }
    
    @Test
    public void testSecurityContext() {
        DefaultCxfMessageMapper mapper = new DefaultCxfMessageMapper();

        HttpServletRequest request = EasyMock.createMock(HttpServletRequest.class);
        request.getUserPrincipal();
        EasyMock.expectLastCall().andReturn(new SimplePrincipal("barry"));
        request.isUserInRole("role1");
        EasyMock.expectLastCall().andReturn(true);
        request.isUserInRole("role2");
        EasyMock.expectLastCall().andReturn(false);
        EasyMock.replay(request);
        Exchange camelExchange = setupCamelExchange("/", "/", request);
        
        Message cxfMessage = mapper.createCxfMessageFromCamelExchange(
            camelExchange, EasyMock.createMock(HeaderFilterStrategy.class));
        SecurityContext sc = cxfMessage.get(SecurityContext.class);
        assertNotNull(sc);
        assertEquals("barry", sc.getUserPrincipal().getName());
        assertTrue(sc.isUserInRole("role1"));
        assertFalse(sc.isUserInRole("role2"));
    }

    private Exchange setupCamelExchange(String requestURI, String requestPath, HttpServletRequest request) {
        org.apache.camel.Message camelMessage = EasyMock
            .createMock(org.apache.camel.Message.class);
        Exchange camelExchange = EasyMock.createMock(Exchange.class);
        camelExchange.getProperty(CamelTransportConstants.CXF_EXCHANGE,
            org.apache.cxf.message.Exchange.class);
        EasyMock.expectLastCall().andReturn(new ExchangeImpl());
        camelExchange.hasOut();
        EasyMock.expectLastCall().andReturn(false);
        camelExchange.getIn();
        EasyMock.expectLastCall().andReturn(camelMessage).times(3);
        camelMessage.getHeaders();
        EasyMock.expectLastCall().andReturn(Collections.emptyMap()).times(2);
        camelMessage.getHeader(Exchange.CONTENT_TYPE, String.class);
        EasyMock.expectLastCall().andReturn(null);
        camelMessage.getHeader("Accept", String.class);
        EasyMock.expectLastCall().andReturn(null);
        camelMessage.getHeader(Exchange.HTTP_CHARACTER_ENCODING, String.class);
        EasyMock.expectLastCall().andReturn(null);
        camelMessage.getHeader(Exchange.CHARSET_NAME, String.class);
        EasyMock.expectLastCall().andReturn(null);
        camelMessage.getHeader(Exchange.HTTP_URI, String.class);
        EasyMock.expectLastCall().andReturn(requestURI);
        camelMessage.getHeader(Exchange.HTTP_PATH, String.class);
        EasyMock.expectLastCall().andReturn(requestPath);
        camelMessage.getHeader(Exchange.HTTP_BASE_URI, String.class);
        EasyMock.expectLastCall().andReturn(requestPath);
        camelMessage.getHeader(Exchange.HTTP_METHOD, String.class);
        EasyMock.expectLastCall().andReturn("GET");
        camelMessage.getHeader(Exchange.HTTP_QUERY, String.class);
        EasyMock.expectLastCall().andReturn("");
        camelMessage.getHeader(Exchange.HTTP_SERVLET_REQUEST);
        EasyMock.expectLastCall().andReturn(request);
        camelMessage.getHeader(Exchange.HTTP_SERVLET_RESPONSE);
        EasyMock.expectLastCall().andReturn(null);

        camelMessage.getBody(InputStream.class);
        EasyMock.expectLastCall().andReturn(
            new ByteArrayInputStream("".getBytes()));
        EasyMock.replay(camelExchange, camelMessage);
        return camelExchange;
    }
}
