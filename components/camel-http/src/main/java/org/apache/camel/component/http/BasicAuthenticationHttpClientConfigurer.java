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

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;

public class BasicAuthenticationHttpClientConfigurer implements HttpClientConfigurer {
    private final String username;
    private final String password;
    private final String domain;
    private final String host;

    public BasicAuthenticationHttpClientConfigurer(String user, String pwd, String domain, String host) {
        this.username = user;
        this.password = pwd;
        this.domain = domain;
        this.host = host;
    }

    @Override
    public void configureHttpClient(HttpClientBuilder clientBuilder) {
        Credentials defaultcreds;
        if (domain != null) {
            defaultcreds = new NTCredentials(username, password, host, domain);
        } else {
            defaultcreds = new UsernamePasswordCredentials(username, password);
        }
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, defaultcreds);
        clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
    }

}
