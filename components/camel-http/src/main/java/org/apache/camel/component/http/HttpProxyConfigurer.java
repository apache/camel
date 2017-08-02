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

import java.util.Optional;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.ProxyHost;

public class HttpProxyConfigurer implements HttpClientConfigurer {
    private final Optional<String> proxyHost;
    private final Optional<Integer> proxyPort;

    public HttpProxyConfigurer(Optional<String> proxyHost) {
        this(proxyHost, Optional.empty());
    }

    public HttpProxyConfigurer(Optional<String> proxyHost, Optional<Integer> proxyPort) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    @Override
    public void configureHttpClient(HttpClient client) {
        if (proxyHost.isPresent() && proxyPort.isPresent()) {
            client.getHostConfiguration().setProxyHost(new ProxyHost(proxyHost.get(), proxyPort.get()));
        } 
        if (proxyHost.isPresent()) {
            client.getHostConfiguration().setProxyHost(new ProxyHost(proxyHost.get()));
        }
    }
}
