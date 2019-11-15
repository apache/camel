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
package org.apache.camel.component.servicenow.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.Provider;

import org.apache.camel.component.servicenow.ServiceNowConfiguration;

@Provider
@Priority(Priorities.AUTHENTICATION)
public final class AuthenticationRequestFilter implements ClientRequestFilter {
    private final OAuthToken authToken;
    private final String authString;

    public AuthenticationRequestFilter(ServiceNowConfiguration conf) {
        this.authToken = conf.hasOAuthAuthentication() ? new OAuthToken(conf) : null;
        this.authString = conf.hasBasicAuthentication() ? getBasicAuthenticationString(conf) : null;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        if (authToken != null) {
            requestContext.getHeaders().add(HttpHeaders.AUTHORIZATION, authToken.getAuthString());
        } else if (authString != null) {
            requestContext.getHeaders().add(HttpHeaders.AUTHORIZATION, authString);
        }
    }

    private static String getBasicAuthenticationString(ServiceNowConfiguration conf) {
        String userAndPassword = conf.getUserName() + ":" + conf.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(userAndPassword.getBytes(StandardCharsets.UTF_8));
    }
}
