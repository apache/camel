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
package org.apache.camel.component.undertow;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class UndertowBasicAuthHandler implements CamelUndertowHttpHandler {

    private HttpHandler next;
    private HttpHandler securityHandler;
    private IdentityManager identityManager;

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (identityManager == null) {
            buildIdMgr();
        }
        if (securityHandler == null) {
            buildSecurityHandler();
        }
        this.securityHandler.handleRequest(exchange);
    }

    private void buildSecurityHandler() {
        HttpHandler handler = this.next;
        handler = new AuthenticationCallHandler(handler);
        handler = new AuthenticationConstraintHandler(handler);
        final List<AuthenticationMechanism> mechanisms
            = Collections.<AuthenticationMechanism>singletonList(new BasicAuthenticationMechanism("My Realm"));
        handler = new AuthenticationMechanismsHandler(handler, mechanisms);
        this.securityHandler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, handler);

    }


    private void buildIdMgr() {
        final Map<String, char[]> users = new HashMap<>(1);
        users.put("guest", "secret".toCharArray());

        identityManager = new MapIdentityManager(users);
    }
    
    public void setNext(HttpHandler nextHandler) {
        this.next = nextHandler;
    }

}
