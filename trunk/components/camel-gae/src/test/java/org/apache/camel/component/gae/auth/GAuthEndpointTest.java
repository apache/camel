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

import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.gae.auth.GAuthEndpoint.Name.AUTHORIZE;
import static org.apache.camel.component.gae.auth.GAuthEndpoint.Name.UPGRADE;
import static org.apache.camel.component.gae.auth.GAuthTestUtils.createComponent;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GAuthEndpointTest {

    private GAuthComponent component;
    
    @Before
    public void setUp() {
        component = createComponent();
    }
        
    @Test
    public void testCustomParams() throws Exception {
        String scope = "http://scope1.example.org,http://scope2.example.org";
        StringBuilder buffer = new StringBuilder("gauth://authorize")
            .append("?").append("scope=" + encode(scope, "UTF-8"))
            .append("&").append("callback=" + encode("http://callback.example.org", "UTF-8"))
            .append("&").append("consumerKey=customConsumerKey")
            .append("&").append("consumerSecret=customConsumerSecret")
            .append("&").append("authorizeBindingRef=" + encode("#customAuthorizeBinding", "UTF-8"))
            .append("&").append("upgradeBindingRef=" + encode("#customUpgradeBinding", "UTF-8"))
            .append("&").append("keyLoaderRef=" + encode("#gAuthKeyLoader", "UTF-8"))
            .append("&").append("serviceRef=" + encode("#gAuthService", "UTF-8"));
        GAuthEndpoint endpoint = component.createEndpoint(buffer.toString());
        assertEquals(AUTHORIZE, endpoint.getName());
        assertArrayEquals(new String [] {
            "http://scope1.example.org", 
            "http://scope2.example.org"}, endpoint.getScopeArray());
        assertEquals(scope, endpoint.getScope());
        assertEquals("http://callback.example.org", endpoint.getCallback());
        assertEquals("customConsumerKey", endpoint.getConsumerKey());
        assertEquals("customConsumerSecret", endpoint.getConsumerSecret());
        assertFalse(endpoint.getAuthorizeBinding().getClass().equals(GAuthAuthorizeBinding.class));
        assertFalse(endpoint.getUpgradeBinding().getClass().equals(GAuthUpgradeBinding.class));
        assertTrue(endpoint.getAuthorizeBinding() instanceof GAuthAuthorizeBinding);
        assertTrue(endpoint.getUpgradeBinding() instanceof GAuthUpgradeBinding);
        assertTrue(endpoint.getKeyLoader() instanceof GAuthPk8Loader);
        assertTrue(endpoint.getService() instanceof GAuthServiceMock);
        
    }
    
    @Test
    public void testDefaultParams() throws Exception {
        component.setConsumerKey(null);
        component.setConsumerSecret(null);
        GAuthEndpoint endpoint = component.createEndpoint("gauth:authorize");
        assertNull(endpoint.getScope());
        assertNull(endpoint.getCallback());
        assertNull(endpoint.getConsumerKey());
        assertNull(endpoint.getConsumerSecret());
        assertNull(endpoint.getKeyLoader());
        assertTrue(endpoint.getAuthorizeBinding().getClass().equals(GAuthAuthorizeBinding.class));
        assertTrue(endpoint.getUpgradeBinding().getClass().equals(GAuthUpgradeBinding.class));
        assertTrue(endpoint.getService().getClass().equals(GAuthServiceImpl.class));
    }
    
    @Test
    public void testComponentDefaultParams() throws Exception {
        component.setKeyLoader(new GAuthPk8Loader());
        GAuthEndpoint endpoint = component.createEndpoint("gauth:authorize");
        assertNull(endpoint.getScope());
        assertNull(endpoint.getCallback());
        assertEquals("testConsumerKey", endpoint.getConsumerKey());
        assertEquals("testConsumerSecret", endpoint.getConsumerSecret());
        assertTrue(endpoint.getAuthorizeBinding().getClass().equals(GAuthAuthorizeBinding.class));
        assertTrue(endpoint.getUpgradeBinding().getClass().equals(GAuthUpgradeBinding.class));
        assertTrue(endpoint.getService().getClass().equals(GAuthServiceImpl.class));
        assertTrue(endpoint.getKeyLoader().getClass().equals(GAuthPk8Loader.class));
    }
    
    public void testCreateUpgradeEndpoint() throws Exception {
        GAuthEndpoint endpoint = component.createEndpoint("gauth:upgrade");
        assertEquals(UPGRADE, endpoint.getName());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidEndpointName() throws Exception {
        component.createEndpoint("gauth:invalid");
    }

}
