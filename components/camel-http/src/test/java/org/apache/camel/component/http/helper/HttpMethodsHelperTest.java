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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.http.common.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HttpMethodsHelperTest {

    @Test
    public void createMethodAlwaysUseUserChoosenMethod() throws URISyntaxException {
        HttpMethods method = HttpHelper.createMethod(
                createExchangeWithOptionalHttpQueryAndHttpMethodHeader("q=camel", HttpMethods.POST),
                createHttpEndpoint(true, "http://www.google.com/search"), false);
        assertEquals(HttpMethods.POST, method);
    }

    @Test
    public void createMethodUseGETIfQueryIsProvidedInHeader() throws URISyntaxException {
        HttpMethods method = HttpHelper.createMethod(
                createExchangeWithOptionalHttpQueryAndHttpMethodHeader("q=camel", null),
                createHttpEndpoint(true, "http://www.google.com/search"), false);
        assertEquals(HttpMethods.GET, method);
    }

    @Test
    public void createMethodUseGETIfQueryIsProvidedInEndpointURI() throws URISyntaxException {
        HttpMethods method = HttpHelper.createMethod(
                createExchangeWithOptionalHttpQueryAndHttpMethodHeader(null, null),
                createHttpEndpoint(true, "http://www.google.com/search?q=test"), false);
        assertEquals(HttpMethods.GET, method);
    }

    @Test
    public void createMethodUseGETIfNoneQueryOrPayloadIsProvided() throws URISyntaxException {
        HttpMethods method = HttpHelper.createMethod(
                createExchangeWithOptionalHttpQueryAndHttpMethodHeader(null, null),
                createHttpEndpoint(true, "http://www.google.com/search"), false);
        assertEquals(HttpMethods.GET, method);
    }

    @Test
    public void createMethodUsePOSTIfNoneQueryButPayloadIsProvided() throws URISyntaxException {
        HttpMethods method = HttpHelper.createMethod(
                createExchangeWithOptionalHttpQueryAndHttpMethodHeader(null, null),
                createHttpEndpoint(true, "http://www.google.com/search"), true);
        assertEquals(HttpMethods.POST, method);
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

    private HttpEndpoint createHttpEndpoint(boolean bridgeEndpoint, String endpointURI) throws URISyntaxException {
        HttpEndpoint endpoint = new HttpEndpoint();
        endpoint.setBridgeEndpoint(bridgeEndpoint);
        if (endpointURI != null) {
            endpoint.setHttpUri(new URI(endpointURI));
        }

        return endpoint;
    }

}
