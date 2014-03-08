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
package org.apache.camel.component.gae.login;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.apache.camel.component.gae.login.GLoginBinding.GLOGIN_COOKIE;
import static org.apache.camel.component.gae.login.GLoginBinding.GLOGIN_HOST_NAME;
import static org.apache.camel.component.gae.login.GLoginBinding.GLOGIN_PASSWORD;
import static org.apache.camel.component.gae.login.GLoginBinding.GLOGIN_TOKEN;
import static org.apache.camel.component.gae.login.GLoginBinding.GLOGIN_USER_NAME;
import static org.apache.camel.component.gae.login.GLoginTestUtils.createEndpoint;
import static org.apache.camel.component.gae.login.GLoginTestUtils.getCamelContext;
import static org.junit.Assert.assertEquals;

public class GLoginBindingTest {

    private static GLoginBinding binding;

    private static GLoginEndpoint endpoint;

    private Exchange exchange;
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        binding = new GLoginBinding();
        StringBuilder buffer = new StringBuilder("glogin:test.appspot.com")
            .append("?").append("userName=testUserName")
            .append("&").append("password=testPassword");
        endpoint = createEndpoint(buffer.toString());
    }
    
    @Before
    public void setUp() throws Exception {
        exchange = new DefaultExchange(getCamelContext());
    }
    
    @Test
    public void testWriteRequestDefault() {
        GLoginData data = binding.writeRequest(endpoint, exchange, null);
        assertEquals("apache-camel-2.x", data.getClientName());
        assertEquals("test.appspot.com", data.getHostName());
        assertEquals("testUserName", data.getUserName());
        assertEquals("testPassword", data.getPassword());
    }
    
    @Test
    public void testWriteRequestCustom() {
        exchange.getIn().setHeader(GLOGIN_HOST_NAME, "custom.appspot.com");
        exchange.getIn().setHeader(GLOGIN_USER_NAME, "customUserName");
        exchange.getIn().setHeader(GLOGIN_PASSWORD, "customPassword");
        GLoginData data = binding.writeRequest(endpoint, exchange, null);
        assertEquals("custom.appspot.com", data.getHostName());
        assertEquals("customUserName", data.getUserName());
        assertEquals("customPassword", data.getPassword());
    }
    
    @Test
    public void testReadResponse() throws Exception {
        GLoginData data = new GLoginData();
        data.setAuthenticationToken("testToken");
        data.setAuthorizationCookie("testCookie=1234ABCD");
        binding.readResponse(endpoint, exchange, data);
        assertEquals("testToken", exchange.getOut().getHeader(GLOGIN_TOKEN));
        assertEquals("testCookie=1234ABCD", exchange.getOut().getHeader(GLOGIN_COOKIE));
    }
    
}
