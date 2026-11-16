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
package org.apache.camel.component.http;

import java.io.IOException;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.BearerToken;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.BearerScheme;
import org.apache.hc.client5.http.impl.auth.NTLMScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;

public class PreemptiveAuthExecChainHandler implements ExecChainHandler {

    private final HttpEndpoint endpoint;

    public PreemptiveAuthExecChainHandler(HttpEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public ClassicHttpResponse execute(
            ClassicHttpRequest request,
            ExecChain.Scope scope,
            ExecChain chain)
            throws IOException, HttpException {

        HttpClientContext context = scope.clientContext;
        AuthCache authCache = context.getAuthCache();
        // If no auth scheme available yet, try to initialize it preemptively
        if (authCache == null) {
            CredentialsProvider credentialsProvider = context.getCredentialsProvider();
            HttpHost httpHost = scope.route.getTargetHost();
            Credentials credentials = credentialsProvider.getCredentials(new AuthScope(httpHost), context);
            if (credentials == null) {
                credentials = HttpCredentialsHelper.getCredentials(endpoint.getAuthMethod(), endpoint.getAuthUsername(),
                        endpoint.getAuthPassword(), endpoint.getAuthHost(), endpoint.getAuthHost(),
                        endpoint.getAuthBearerToken());
            }
            if (credentials == null) {
                throw new HttpException("No credentials for preemptive authentication");
            }
            AuthScheme authScheme;
            if ("NTLM".equalsIgnoreCase(endpoint.getAuthMethod())) {
                authScheme = new NTLMScheme();
            } else if ("BEARER".equalsIgnoreCase(endpoint.getAuthMethod())) {
                authScheme = preemptiveBearerScheme(httpHost, credentials, context);
            } else {
                BasicScheme bs = new BasicScheme();
                bs.initPreemptive(credentials);
                authScheme = bs;
            }
            authCache = new BasicAuthCache();
            authCache.put(httpHost, authScheme);
            context.setAuthCache(authCache);
        }
        return chain.proceed(request, scope);
    }

    /**
     * The auth cache keeps the state of a scheme only once its challenge is complete, and a bearer scheme takes its
     * token in {@link BearerScheme#isResponseReady}, so a new scheme would be cached without the token.
     */
    private static AuthScheme preemptiveBearerScheme(HttpHost host, Credentials credentials, HttpClientContext context)
            throws HttpException {
        if (!(credentials instanceof BearerToken)) {
            throw new HttpException("No bearer token for preemptive authentication");
        }
        BearerScheme scheme = new BearerScheme();
        scheme.processChallenge(new AuthChallenge(ChallengeType.TARGET, StandardAuthScheme.BEARER), context);
        scheme.isResponseReady(host, (authScope, ctx) -> credentials, context);
        return scheme;
    }
}
