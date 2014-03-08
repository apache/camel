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
import org.apache.camel.component.gae.bind.OutboundBinding;

/**
 * Binds {@link GLoginData} to a Camel {@link Exchange}.
 */
public class GLoginBinding implements OutboundBinding<GLoginEndpoint, GLoginData, GLoginData> {

    /**
     * Name of the Camel header defining the host name. Overrides
     * {@link GLoginEndpoint#getHostName()}.
     */
    public static final String GLOGIN_HOST_NAME = "CamelGloginHostName";
    
    /**
     * Name of the Camel header defining the login username. Overrides
     * {@link GLoginEndpoint#getUserName()}.
     */
    public static final String GLOGIN_USER_NAME = "CamelGloginUserName";
    
    /**
     * Name of the Camel header defining the login password. Overrides
     * {@link GLoginEndpoint#getPassword()}.
     */
    public static final String GLOGIN_PASSWORD = "CamelGloginPassword";
    
    /**
     * Name of the Camel header containing the resulting authentication token. 
     */
    public static final String GLOGIN_TOKEN = "CamelGloginToken";
    
    /**
     * Name of the Camel header containing the resulting authorization cookie. 
     */
    public static final String GLOGIN_COOKIE = "CamelGloginCookie";

    /**
     * Creates a {@link GLoginData} object from endpoint and
     * <code>exchange.getIn()</code> header data. The created object is used to
     * obtain an authentication token and an authorization cookie.
     */
    public GLoginData writeRequest(GLoginEndpoint endpoint, Exchange exchange, GLoginData request) {
        String hostName = exchange.getIn().getHeader(GLOGIN_HOST_NAME, String.class); 
        String userName = exchange.getIn().getHeader(GLOGIN_USER_NAME, String.class); 
        String password = exchange.getIn().getHeader(GLOGIN_PASSWORD, String.class); 
        
        request = new GLoginData();
        if (hostName == null) {
            hostName = endpoint.getHostName();
        }
        if (userName == null) {
            userName = endpoint.getUserName();
        }
        if (password == null) {
            password = endpoint.getPassword();
        }
        request.setClientName(endpoint.getClientName());
        request.setDevAdmin(endpoint.isDevAdmin());
        request.setDevPort(endpoint.getDevPort());
        request.setDevMode(endpoint.isDevMode());
        request.setHostName(hostName);
        request.setUserName(userName);
        request.setPassword(password);
        return request;
    }

    /**
     * Creates an <code>exchange.getOut()</code> message with a
     * {@link #GLOGIN_TOKEN} header containing an authentication token and a
     * {@link #GLOGIN_COOKIE} header containing an authorization cookie. If the
     * endpoint is configured to run in development mode, no authentication
     * token will be set, only an authorization cookie.
     */
    public Exchange readResponse(GLoginEndpoint endpoint, Exchange exchange, GLoginData response) throws Exception {
        if (response.getAuthenticationToken() != null) {
            exchange.getOut().setHeader(GLOGIN_TOKEN, response.getAuthenticationToken());
        }
        if (response.getAuthorizationCookie() != null) {
            exchange.getOut().setHeader(GLOGIN_COOKIE, response.getAuthorizationCookie());
        }
        return exchange;
    }

}
