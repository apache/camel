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

import com.google.gdata.client.authn.oauth.GoogleOAuthParameters;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.apache.camel.component.gae.auth.GAuthTestUtils.createComponent;
import static org.apache.camel.component.gae.auth.GAuthTokenSecret.COOKIE_NAME;
import static org.apache.camel.component.gae.auth.GAuthUpgradeBinding.GAUTH_ACCESS_TOKEN;
import static org.apache.camel.component.gae.auth.GAuthUpgradeBinding.GAUTH_ACCESS_TOKEN_SECRET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GAuthUpgradeBindingTest {

    private static GAuthUpgradeBinding binding;

    private static GAuthComponent component;

    private static GAuthEndpoint endpoint;

    private Exchange exchange;
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        component = createComponent();
        binding = new GAuthUpgradeBinding();
        endpoint = component.createEndpoint("gauth:upgrade");
    }
    
    @Before
    public void setUp() throws Exception {
        exchange = new DefaultExchange(component.getCamelContext());
    }
    
    @Test
    public void testWriteRequest() throws Exception {
        exchange.getIn().setHeader("oauth_token", "token1");
        exchange.getIn().setHeader("oauth_verifier", "verifier1");
        exchange.getIn().setHeader("Cookie", COOKIE_NAME + "=secret1");
        GoogleOAuthParameters params = binding.writeRequest(endpoint, exchange, null);
        assertEquals("testConsumerKey", params.getOAuthConsumerKey());
        assertEquals("testConsumerSecret", params.getOAuthConsumerSecret());
        assertEquals("token1", params.getOAuthToken());
        assertEquals("secret1", params.getOAuthTokenSecret());
        assertEquals("verifier1", params.getOAuthVerifier());
    }
    
    @Test(expected = GAuthException.class)
    public void testWriteRequestNoCookie() throws Exception {
        exchange.getIn().setHeader("oauth_token", "token1");
        exchange.getIn().setHeader("oauth_verifier", "verifier1");
        binding.writeRequest(endpoint, exchange, null);
    }
    
    @Test
    public void testReadResponse() throws Exception {
        GoogleOAuthParameters params = new GoogleOAuthParameters();
        params.setOAuthToken("token2");
        params.setOAuthTokenSecret("tokenSecret2");
        binding.readResponse(endpoint, exchange, params);
        assertEquals("token2", exchange.getOut().getHeader(GAUTH_ACCESS_TOKEN));
        assertEquals("tokenSecret2", exchange.getOut().getHeader(GAUTH_ACCESS_TOKEN_SECRET));
    }
    
    @Test
    public void testReadResponseNoToken() throws Exception {
        GoogleOAuthParameters params = new GoogleOAuthParameters();
        binding.readResponse(endpoint, exchange, params);
        assertNull(exchange.getOut().getHeader(GAUTH_ACCESS_TOKEN));
        assertNull(exchange.getOut().getHeader(GAUTH_ACCESS_TOKEN_SECRET));
    }

}
