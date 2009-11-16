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
package org.apache.camel.component.gae.http;

import java.io.InputStream;

import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.MockHttpResponse;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.apache.camel.component.gae.http.GHttpTestUtils.createEndpoint;
import static org.apache.camel.component.gae.http.GHttpTestUtils.createRequest;
import static org.apache.camel.component.gae.http.GHttpTestUtils.getCamelContext;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GHttpBindingTest {

    private static GHttpBinding binding;

    private Exchange exchange;
    
    
    @BeforeClass
    public static void setUpClass() {
        binding = new GHttpBinding();
    }
    
    @Before
    public void setUp() throws Exception {
        exchange = new DefaultExchange(getCamelContext());
    }

    @Test
    public void testGetRequestMethod() {
        assertEquals(HTTPMethod.GET, binding.getRequestMethod(null, exchange));
        exchange.getIn().setBody("test");
        assertEquals(HTTPMethod.POST, binding.getRequestMethod(null, exchange));
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, "DELETE");
        assertEquals(HTTPMethod.DELETE, binding.getRequestMethod(null, exchange));
    }
    
    @Test
    public void testGetRequestUrl() throws Exception {
        GHttpEndpoint endpoint = createEndpoint("ghttp://somewhere.com:9090/path");
        assertEquals("http://somewhere.com:9090/path", binding.getRequestUrl(endpoint, exchange).toString());
        exchange.getIn().setHeader(Exchange.HTTP_URI, "http://custom.org:8080/path");
        assertEquals("http://somewhere.com:9090/path", binding.getRequestUrl(endpoint, exchange).toString());
        endpoint = createEndpoint("ghttp://somewhere.com:9090/path?bridgeEndpoint=false");
        assertEquals("http://custom.org:8080/path", binding.getRequestUrl(endpoint, exchange).toString());
        exchange.getIn().setHeader(Exchange.HTTP_URI, "ghttp://another.org:8080/path");
        assertEquals("http://another.org:8080/path", binding.getRequestUrl(endpoint, exchange).toString());
        exchange.getIn().removeHeader(Exchange.HTTP_URI);
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "a=b");
        assertEquals("http://somewhere.com:9090/path?a=b", binding.getRequestUrl(endpoint, exchange).toString());
        exchange.getIn().setHeader(Exchange.HTTP_URI, "http://custom.org:8080/path");
        assertEquals("http://custom.org:8080/path?a=b", binding.getRequestUrl(endpoint, exchange).toString());
    }
    
    @Test
    public void testGetRequestUrlEncoding() throws Exception {
        GHttpEndpoint endpoint = createEndpoint("ghttp://somewhere.com:9090/path?bridgeEndpoint=false&a=b c");
        assertEquals("http://somewhere.com:9090/path?a=b+c", binding.getRequestUrl(endpoint, exchange).toString());
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "x=y z");
        assertEquals("http://somewhere.com:9090/path?x=y+z", binding.getRequestUrl(endpoint, exchange).toString());
        exchange.getIn().removeHeader(Exchange.HTTP_QUERY);
        exchange.getIn().setHeader(Exchange.HTTP_URI, "http://custom.org:8080/path?d=e f");
        assertEquals("http://custom.org:8080/path?d=e+f", binding.getRequestUrl(endpoint, exchange).toString());
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "x=y z");
        assertEquals("http://custom.org:8080/path?x=y+z", binding.getRequestUrl(endpoint, exchange).toString());
    }
    
    @Test
    public void testWriteRequestHeaders() throws Exception {
        GHttpEndpoint endpoint = createEndpoint("ghttp://somewhere.com:9090/path");
        HTTPRequest request = createRequest();
        // Shouldn't be filtered out
        exchange.getIn().setHeader("test", "abc");
        // Should be filtered out
        exchange.getIn().setHeader("org.apache.camel.whatever", "xyz");
        exchange.getIn().setHeader("CamelWhatever", "xyz");
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "x=y");
        binding.writeRequestHeaders(endpoint, exchange, request);
        assertEquals(1, request.getHeaders().size());
        assertEquals("test", request.getHeaders().get(0).getName());
        assertEquals("abc", request.getHeaders().get(0).getValue());
    }

    @Test
    public void testWriteRequestBody() throws Exception {
        HTTPRequest request = createRequest();
        String body = "abc";
        exchange.getIn().setBody(body);
        binding.writeRequestBody(null, exchange, request);
        assertArrayEquals(body.getBytes(), request.getPayload());
    }
    
    @Test
    public void testWriteRequest() throws Exception {
        GHttpEndpoint endpoint = createEndpoint("ghttp://somewhere.com:9090/path");
        HTTPRequest request = binding.writeRequest(endpoint, exchange, null);
        assertEquals("http://somewhere.com:9090/path", request.getURL().toString());
        assertEquals(HTTPMethod.GET, request.getMethod());
    }
    
    @Test
    public void testReadResponseHeaders() throws Exception {
        GHttpEndpoint endpoint = createEndpoint("ghttp://somewhere.com:9090/path");
        MockHttpResponse response = new MockHttpResponse(200);
        response.addHeader("test", "abc");
        response.addHeader("content-type", "text/plain");
        binding.readResponseHeaders(endpoint, exchange, response);
        assertEquals(200, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("abc", exchange.getOut().getHeader("test"));
        assertEquals("text/plain", exchange.getOut().getHeader("content-type"));
    }
    
    @Test
    public void testReadResponseBody() throws Exception {
        MockHttpResponse response = new MockHttpResponse(200);
        response.setContent("abc".getBytes());
        binding.readResponseBody(null, exchange, response);
        InputStream stream = exchange.getOut().getBody(InputStream.class);
        assertEquals("abc".getBytes()[0], stream.read());
    }
    
    @Test
    public void testReadNullResponseBody() throws Exception {
        MockHttpResponse response = new MockHttpResponse(200);
        binding.readResponseBody(null, exchange, response);
        InputStream stream = exchange.getOut().getBody(InputStream.class);
        assertNull(stream);
    }
    
    @Test(expected = GHttpException.class)
    public void testFailureException() throws Exception {
        GHttpEndpoint endpoint = createEndpoint("ghttp://somewhere.com:9090/path");
        MockHttpResponse response = new MockHttpResponse(500);
        binding.readResponse(endpoint, exchange, response);
    }
    
    @Test
    public void testFailureNoException() throws Exception {
        GHttpEndpoint endpoint = createEndpoint("ghttp://somewhere.com:9090/path?throwExceptionOnFailure=false");
        MockHttpResponse response = new MockHttpResponse(500);
        binding.readResponse(endpoint, exchange, response);
        assertEquals(500, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }
    
}
