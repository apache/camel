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

import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHost;

/**
 * Strategy for configuring the HttpClient with a proxy
 */
public class ProxyHttpClientConfigurer implements HttpClientConfigurer {

    private final String host;
    private final Integer port;
    private final String scheme;
    private final String username;
    private final char[] password;
    private final String domain;
    private final String ntHost;
    private final HttpCredentialsHelper credentialsHelper;

    public ProxyHttpClientConfigurer(String host, Integer port, String scheme) {
        this(host, port, scheme, null, null, null, null, null);
    }

    public ProxyHttpClientConfigurer(String host, Integer port, String scheme, String username, String password, String domain,
                                     String ntHost, HttpCredentialsHelper credentialsHelper) {
        this.host = host;
        this.port = port;
        this.scheme = scheme;
        this.username = username;
        this.password = password == null ? new char[0] : password.toCharArray();
        this.domain = domain;
        this.ntHost = ntHost;
        this.credentialsHelper = credentialsHelper;
    }

    @Override
    public void configureHttpClient(HttpClientBuilder clientBuilder) {
        clientBuilder.setProxy(new HttpHost(scheme, host, port));

        if (username != null && password != null) {
            Credentials defaultcreds;
            if (domain != null) {
                defaultcreds = new NTCredentials(username, password, ntHost, domain);
            } else {
                defaultcreds = new UsernamePasswordCredentials(username, password);
            }
            clientBuilder.setDefaultCredentialsProvider(credentialsHelper
                    .getCredentialsProvider(host, port, defaultcreds));
        }
    }

}
