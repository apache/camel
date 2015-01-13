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
import org.eclipse.jetty.client.HttpClient;
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
        if (isSupportRedirect()) {
            setupRedirectListener();
        }
        super.doStart();
    }

    private void setConnectorType() {
        try {
            HttpClient.class.getMethod("setConnectorType", Integer.TYPE).invoke(this, 2);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    
    protected boolean hasThreadPool() {
        try {
            return getClass().getMethod("getThreadPool").invoke(this) != null;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    protected void setThreadPoolOrExecutor(Executor pool) {
        try {
            getClass().getMethod("setThreadPool", ThreadPool.class).invoke(this, pool);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public void setProxy(String host, int port) {
        try {
            // setProxy(new org.eclipse.jetty.client.Address(host, port));
            Class<?> c = Class.forName("org.eclipse.jetty.client.Address");
            Object o = c.getConstructor(String.class, Integer.TYPE).newInstance(host, port);
            this.getClass().getMethod("setProxy", c).invoke(this, o);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void setupRedirectListener() {
        // setup the listener for it
        try {
            getClass().getMethod("registerListener", String.class).invoke(this, CamelRedirectListener.class.getName());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
