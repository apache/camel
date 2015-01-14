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
package org.apache.camel.component.jetty8;

import java.util.concurrent.Executor;

import org.apache.camel.component.jetty.CamelHttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.ThreadPool;

@SuppressWarnings("unchecked")
public class CamelHttpClient8 extends CamelHttpClient {

    public CamelHttpClient8(SslContextFactory sslContextFactory) {
        super(sslContextFactory);
        setConnectorType();
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    private void setConnectorType() {
        setConnectorType(2);
    }
    
    protected boolean hasThreadPool() {
        return getThreadPool() != null;
    }

    protected void setThreadPoolOrExecutor(Executor pool) {
        setThreadPool((ThreadPool)pool);
    }
    
    public void setProxy(String host, int port) {
        setProxy(new org.eclipse.jetty.client.Address(host, port));
    }

    private void setupRedirectListener() {
        registerListener(CamelRedirectListener.class.getName());
    }

    @Override
    public String getProxyHost() {
        return getProxy().getHost();
    }

    @Override
    public int getProxyPort() {
        return getProxy().getPort();
    }
    
}
