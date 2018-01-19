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
package org.apache.camel.component.http.helper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HttpHelperTest {

    @Test
    public void testAppendHeader() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        HttpHelper.appendHeader(headers, "foo", "a");
        HttpHelper.appendHeader(headers, "bar", "b");
        HttpHelper.appendHeader(headers, "baz", "c");

        assertEquals(3, headers.size());
        assertEquals("a", headers.get("foo"));
        assertEquals("b", headers.get("bar"));
        assertEquals("c", headers.get("baz"));
    }

    @Test
    public void testAppendHeaderMultipleValues() throws Exception {
        Map<String, Object> headers = new HashMap<String, Object>();
        HttpHelper.appendHeader(headers, "foo", "a");
        HttpHelper.appendHeader(headers, "bar", "b");
        HttpHelper.appendHeader(headers, "bar", "c");

        assertEquals(2, headers.size());
        assertEquals("a", headers.get("foo"));

        List<?> list = (List<?>) headers.get("bar");
        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals("b", list.get(0));
        assertEquals("c", list.get(1));
    }

    @Test
    public void createURLShouldReturnTheHeaderURIIfNotBridgeEndpoint() throws URISyntaxException {
        String url = HttpHelper.createURL(
                createExchangeWithOptionalCamelHttpUriHeader("http://apache.org", null),
                createHttpEndpoint(false, "http://camel.apache.org"));
        assertEquals("http://apache.org", url);
    }

    @Test
    public void createURLShouldReturnTheEndpointURIIfBridgeEndpoint() throws URISyntaxException {
        String url = HttpHelper.createURL(
                createExchangeWithOptionalCamelHttpUriHeader("http://apache.org", null),
                createHttpEndpoint(true, "http://camel.apache.org"));
        assertEquals("http://camel.apache.org", url);
    }

    @Test
    public void createURLShouldReturnTheEndpointURIIfNotBridgeEndpoint() throws URISyntaxException {
        String url = HttpHelper.createURL(
                createExchangeWithOptionalCamelHttpUriHeader(null, null),
                createHttpEndpoint(false, "http://camel.apache.org"));
        assertEquals("http://camel.apache.org", url);
    }

    @Test
    public void createURLShouldReturnTheEndpointURIWithHeaderHttpPathAndAddOneSlash() throws URISyntaxException {
        String url = HttpHelper.createURL(
                createExchangeWithOptionalCamelHttpUriHeader(null, "search"),
                createHttpEndpoint(true, "http://www.google.com"));
        assertEquals("http://www.google.com/search", url);
    }

    @Test
    public void createURLShouldReturnTheEndpointURIWithHeaderHttpPathAndRemoveOneSlash() throws URISyntaxException {
        String url = HttpHelper.createURL(
                createExchangeWithOptionalCamelHttpUriHeader(null, "/search"),
                createHttpEndpoint(true, "http://www.google.com/"));
        assertEquals("http://www.google.com/search", url);
    }

    @Test
    public void createURIShouldKeepQueryParametersGivenInUrlParameter() throws URISyntaxException {
        URI uri = HttpHelper.createURI(
                createExchangeWithOptionalCamelHttpUriHeader(null, null),
                "http://apache.org/?q=%E2%82%AC", createHttpEndpoint(false, "http://apache.org"));
        assertEquals("http://apache.org/?q=%E2%82%AC", uri.toString());
    }

    @Test
    public void createURIShouldEncodeExchangeHttpQuery() throws URISyntaxException {
        URI uri = HttpHelper.createURI(
                createExchangeWithOptionalHttpQueryAndHttpMethodHeader("q= ", null),
                "http://apache.org/?q=%E2%82%AC", createHttpEndpoint(false, "http://apache.org"));
        assertEquals("http://apache.org/?q=%20", uri.toString());
    }

    @Test
    public void createURIShouldNotDoubleEncodeExchangeHttpQuery() throws URISyntaxException {
        URI uri = HttpHelper.createURI(
                createExchangeWithOptionalHttpQueryAndHttpMethodHeader("q=%E2%82%AC", null),
                "http://apache.org/?q=%E2%82%AC", createHttpEndpoint(false, "http://apache.org"));
        assertEquals("http://apache.org/?q=%E2%82%AC", uri.toString());
    }

    @Test
    public void createURIShouldKeepQueryParametersGivenInEndPointUri() throws URISyntaxException {
        URI uri = HttpHelper.createURI(
                createExchangeWithOptionalHttpQueryAndHttpMethodHeader(null, null),
                "http://apache.org/", createHttpEndpoint(false, "http://apache.org/?q=%E2%82%AC"));
        assertEquals("http://apache.org/?q=%E2%82%AC", uri.toString());
    }

    @Test
    public void createURLShouldNotRemoveTrailingSlash() throws Exception {
        String url = HttpHelper.createURL(
                createExchangeWithOptionalCamelHttpUriHeader(null, "/"),
                createHttpEndpoint(true, "http://www.google.com"));
        assertEquals("http://www.google.com/", url);
    }
    @Test
    public void createURLShouldAddPathAndQueryParamsAndSlash() throws Exception {
        String url = HttpHelper.createURL(
                createExchangeWithOptionalCamelHttpUriHeader(null, "search"),
                createHttpEndpoint(true, "http://www.google.com/context?test=true"));
        assertEquals("http://www.google.com/context/search?test=true", url);
    }
    @Test
    public void createURLShouldAddPathAndQueryParamsAndRemoveDuplicateSlash() throws Exception {
        String url = HttpHelper.createURL(
                createExchangeWithOptionalCamelHttpUriHeader(null, "/search"),
                createHttpEndpoint(true, "http://www.google.com/context/?test=true"));
        assertEquals("http://www.google.com/context/search?test=true", url);
    }

    @Test
    public void testIsStatusCodeOkSimpleRange() throws Exception {
        assertFalse(HttpHelper.isStatusCodeOk(199, "200-299"));
        assertTrue(HttpHelper.isStatusCodeOk(200, "200-299"));
        assertTrue(HttpHelper.isStatusCodeOk(299, "200-299"));
        assertFalse(HttpHelper.isStatusCodeOk(300, "200-299"));
        assertFalse(HttpHelper.isStatusCodeOk(300, "301-304"));
        assertTrue(HttpHelper.isStatusCodeOk(301, "301-304"));
        assertTrue(HttpHelper.isStatusCodeOk(304, "301-304"));
        assertFalse(HttpHelper.isStatusCodeOk(305, "301-304"));
    }

    @Test
    public void testIsStatusCodeOkComplexRange() throws Exception {
        assertFalse(HttpHelper.isStatusCodeOk(199, "200-299,404,301-304"));
        assertTrue(HttpHelper.isStatusCodeOk(200, "200-299,404,301-304"));
        assertTrue(HttpHelper.isStatusCodeOk(299, "200-299,404,301-304"));
        assertFalse(HttpHelper.isStatusCodeOk(300, "200-299,404,301-304"));
        assertTrue(HttpHelper.isStatusCodeOk(301, "200-299,404,301-304"));
        assertTrue(HttpHelper.isStatusCodeOk(304, "200-299,404,301-304"));
        assertFalse(HttpHelper.isStatusCodeOk(305, "200-299,404,301-304"));
        assertTrue(HttpHelper.isStatusCodeOk(404, "200-299,404,301-304"));
    }

    private Exchange createExchangeWithOptionalHttpQueryAndHttpMethodHeader(String httpQuery, HttpMethods httpMethod) {
        CamelContext context = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(context);
        Message inMsg = exchange.getIn();
        if (httpQuery != null) {
            inMsg.setHeader(Exchange.HTTP_QUERY, httpQuery);
        }
        if (httpMethod != null) {
            inMsg.setHeader(Exchange.HTTP_METHOD, httpMethod);
        }

        return exchange;
    }

    private Exchange createExchangeWithOptionalCamelHttpUriHeader(String endpointURI, String httpPath) throws URISyntaxException {
        CamelContext context = new DefaultCamelContext();
        DefaultExchange exchange = new DefaultExchange(context);
        Message inMsg = exchange.getIn();
        if (endpointURI != null) {
            inMsg.setHeader(Exchange.HTTP_URI, endpointURI);
        }
        if (httpPath != null) {
            inMsg.setHeader(Exchange.HTTP_PATH, httpPath);
        }

        return exchange;
    }

    private HttpEndpoint createHttpEndpoint(boolean bridgeEndpoint, String endpointURI) throws URISyntaxException {
        HttpEndpoint endpoint = new HttpEndpoint();
        endpoint.setBridgeEndpoint(bridgeEndpoint);
        if (endpointURI != null) {
            endpoint.setHttpUri(new URI(endpointURI));
        }

        return endpoint;
    }
}