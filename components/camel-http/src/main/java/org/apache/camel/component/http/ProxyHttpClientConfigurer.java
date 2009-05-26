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

/**
 * Strategy for configuring the HttpClient with a proxy
 *
 * @version 
 */
public class ProxyHttpClientConfigurer implements HttpClientConfigurer {

    private final String host;
    private final Integer port;
    
    private final String username;
    private final String password;
    
    public ProxyHttpClientConfigurer(String host, Integer port) {
        this(host, port, null, null);
    }
    
    public ProxyHttpClientConfigurer(String host, Integer port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public void configureHttpClient(HttpClient client) {
        client.getHostConfiguration().setProxy(host, port);

        if (username != null && password != null) {
            Credentials defaultcreds = new UsernamePasswordCredentials(username, password);
            client.getState().setProxyCredentials(AuthScope.ANY, defaultcreds);
        }
    }
}
