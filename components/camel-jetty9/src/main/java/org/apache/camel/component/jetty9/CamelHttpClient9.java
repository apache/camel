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
package org.apache.camel.component.jetty9;

import java.util.concurrent.Executor;

import org.apache.camel.component.jetty.CamelHttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.util.ssl.SslContextFactory;

@Deprecated
public class CamelHttpClient9 extends CamelHttpClient {
    
    public CamelHttpClient9(SslContextFactory sslContextFactory) {
        super(sslContextFactory);
    }

    public CamelHttpClient9(HttpClientTransport transport, SslContextFactory sslContextFactory) {
        super(transport, sslContextFactory);
    }

    protected boolean hasThreadPool() {
        return getExecutor() != null;
    }

    protected void setThreadPoolOrExecutor(Executor pool) {
        setExecutor(pool);
    }
    
    public void setProxy(String host, int port) {
        getProxyConfiguration().getProxies().add(new org.eclipse.jetty.client.HttpProxy(host, port));
    }

    @Override
    public String getProxyHost() {
        return getProxyConfiguration().getProxies().get(0).getAddress().getHost();
    }

    @Override
    public int getProxyPort() {
        return getProxyConfiguration().getProxies().get(0).getAddress().getPort();
    }
    
}
