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
package org.apache.camel.component.http;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;

public class BasicAuthenticationHttpClientConfigurer implements HttpClientConfigurer {
    private final boolean proxy;
    private final String username;
    private final String password;

    public BasicAuthenticationHttpClientConfigurer(boolean proxy, String user, String pwd) {
        this.proxy = proxy;
        this.username = user;
        this.password = pwd;
    }

    public void configureHttpClient(HttpClient client) {
        Credentials credentials = new UsernamePasswordCredentials(username, password);
        if (proxy) {
            client.getState().setProxyCredentials(AuthScope.ANY, credentials);
        } else {
            client.getState().setCredentials(AuthScope.ANY, credentials);
        }
    }

    public boolean isProxy() {
        return proxy;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
