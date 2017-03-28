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
package org.apache.camel.component.jetty;

import java.util.concurrent.Executor;

import org.apache.camel.util.ObjectHelper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

@Deprecated
public abstract class CamelHttpClient extends HttpClient {
    
    private boolean supportRedirect;

    public CamelHttpClient(SslContextFactory sslContextFactory) {
        super(sslContextFactory);
    }

    public CamelHttpClient(HttpClientTransport transport, SslContextFactory sslContextFactory) {
        super(transport, sslContextFactory);
    }

    @Override
    protected void doStart() throws Exception {
        if (!hasThreadPool()) {
            // if there is no thread pool then create a default thread pool using daemon threads with default size (200)
            QueuedThreadPool qtp = new QueuedThreadPool();
            qtp.setDaemon(true);
            // let the thread names indicate they are from the client
            qtp.setName("CamelJettyClient(" + ObjectHelper.getIdentityHashCode(this) + ")");
            setThreadPoolOrExecutor(qtp);
        }
        super.doStart();
    }
 
    protected abstract boolean hasThreadPool();

    protected abstract void setThreadPoolOrExecutor(Executor pool);
    
    public abstract void setProxy(String host, int port);
    
    public boolean isSupportRedirect() {
        return supportRedirect;
    }

    public void setSupportRedirect(boolean supportRedirect) {
        this.supportRedirect = supportRedirect;
    }
    
    public abstract String getProxyHost();

    public abstract int getProxyPort();

}
