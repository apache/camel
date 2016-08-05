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
package org.apache.camel.component.weather.http;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;


public final class AuthenticationHttpClientConfigurer implements HttpClientConfigurer {

    private final boolean proxy;
    private final Credentials credentials;

    private AuthenticationHttpClientConfigurer(boolean proxy, Credentials credentials) {
        this.proxy = proxy;
        this.credentials = credentials;
    }

    @Override
    public HttpClient configureHttpClient(HttpClient client) {
        if (proxy) {
            client.getState().setProxyCredentials(AuthScope.ANY, this.credentials);
        } else {
            client.getState().setCredentials(AuthScope.ANY, this.credentials);
        }

        return client;
    }

    public static HttpClientConfigurer basicAutenticationConfigurer(boolean proxy, String user, String pwd) {
        return new AuthenticationHttpClientConfigurer(proxy, new UsernamePasswordCredentials(user, pwd));
    }

    public static HttpClientConfigurer ntlmAutenticationConfigurer(boolean proxy, String user, String pwd, String domain, String host) {
        return new AuthenticationHttpClientConfigurer(proxy, new NTCredentials(user, pwd, host, domain));
    }
}
