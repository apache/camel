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
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;

public class PreemptiveAuthExecChainHandler implements ExecChainHandler {

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
                throw new HttpException("No credentials for preemptive authentication");
            }
            BasicScheme authScheme = new BasicScheme();
            authScheme.initPreemptive(credentials);
            authCache = new BasicAuthCache();
            authCache.put(httpHost, authScheme);
            context.setAuthCache(authCache);
        }
        return chain.proceed(request, scope);
    }
}
