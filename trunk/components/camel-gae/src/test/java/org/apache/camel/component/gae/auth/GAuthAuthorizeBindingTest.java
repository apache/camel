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
package org.apache.camel.component.gae.auth;

import static java.net.URLEncoder.encode;

import com.google.gdata.client.authn.oauth.GoogleOAuthParameters;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.apache.camel.component.gae.auth.GAuthAuthorizeBinding.GAUTH_CALLBACK;
import static org.apache.camel.component.gae.auth.GAuthAuthorizeBinding.GAUTH_SCOPE;
import static org.apache.camel.component.gae.auth.GAuthTestUtils.createComponent;
import static org.junit.Assert.assertEquals;

public class GAuthAuthorizeBindingTest {

    private static GAuthAuthorizeBinding binding;

    private static GAuthComponent component;

    private static GAuthEndpoint endpoint;

    private Exchange exchange;
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        component = createComponent();
        binding = new GAuthAuthorizeBinding();
        StringBuilder buffer = new StringBuilder("gauth:authorize")
            .append("?").append("scope=" + encode("http://scope.example.org", "UTF-8"))
            .append("&").append("callback=" + encode("http://test.example.org/handler", "UTF-8"));
        endpoint = component.createEndpoint(buffer.toString());
    }
    
    @Before
    public void setUp() throws Exception {
        exchange = new DefaultExchange(component.getCamelContext());
    }
    
    @Test
    public void testWriteRequestDefault() {
        GoogleOAuthParameters params = binding.writeRequest(endpoint, exchange, null);
        assertEquals("testConsumerKey", params.getOAuthConsumerKey());
        assertEquals("testConsumerSecret", params.getOAuthConsumerSecret());
        assertEquals("http://test.example.org/handler", params.getOAuthCallback());
        assertEquals("http://scope.example.org", params.getScope());
    }
    
    @Test
    public void testWriteRequestCustom() {
        exchange.getIn().setHeader(GAUTH_SCOPE, "http://scope.custom.org");
        exchange.getIn().setHeader(GAUTH_CALLBACK, "http://test.custom.org/handler");
        GoogleOAuthParameters params = binding.writeRequest(endpoint, exchange, null);
        assertEquals("http://test.custom.org/handler", params.getOAuthCallback());
        assertEquals("http://scope.custom.org", params.getScope());
    }
    
    @Test
    public void testReadResponse() throws Exception {
        GoogleOAuthParameters params = binding.writeRequest(endpoint, exchange, null);
        params.setOAuthToken("testToken");
        params.setOAuthTokenSecret("testTokenSecret");
        binding.readResponse(endpoint, exchange, params);
        String redirectUrl = "https://www.google.com/accounts/OAuthAuthorizeToken"
            + "?oauth_token=testToken"
            + "&oauth_callback=http%3A%2F%2Ftest.example.org%2Fhandler";
        String secretCookie = "gauth-token-secret=testTokenSecret";
        assertEquals(302, exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals(redirectUrl, exchange.getOut().getHeader("Location"));
        assertEquals(secretCookie, exchange.getOut().getHeader("Set-Cookie"));
    }
    
}
