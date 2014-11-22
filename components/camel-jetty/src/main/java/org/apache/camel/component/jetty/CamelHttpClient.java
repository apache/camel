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

import java.util.Collection;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;

import org.apache.camel.util.ObjectHelper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;

public class CamelHttpClient extends HttpClient {
    
    private boolean supportRedirect;
    
    public CamelHttpClient() {
        super();
        setConnectorTypeJetty8();
    }
    
    public CamelHttpClient(SslContextFactory sslContextFactory) {
        super(sslContextFactory);
        setConnectorTypeJetty8();
    }
    
    private void setConnectorTypeJetty8() {
        if (Server.getVersion().startsWith("8")) {
            try {
                HttpClient.class.getMethod("setConnectorType", Integer.TYPE).invoke(this, 2);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }
    
    @Deprecated
    /**
     * It does nothing here, please setup SslContextFactory directly, it will be removed in Camel 2.16.0
     * @param context
     */
    public void setSSLContext(SSLContext context) {
        // do nothing here, please setup SslContextFactory directly.
    }
    
    @Override
    protected void doStart() throws Exception {
        if (!hasThreadPool()) {
            // if there is no thread pool then create a default thread pool using daemon threads
            QueuedThreadPool qtp = new QueuedThreadPool();
            // 16 max threads is the default in the http client
            qtp.setMaxThreads(16);
            qtp.setDaemon(true);
            // let the thread names indicate they are from the client
            qtp.setName("CamelJettyClient(" + ObjectHelper.getIdentityHashCode(this) + ")");
            setThreadPoolOrExecutor(qtp);
        }
        if (Server.getVersion().startsWith("8") && isSupportRedirect()) {
            setupRedirectListener();
        }
        super.doStart();
    }
 
    private void setupRedirectListener() {
        // setup the listener for it
        try {
            getClass().getMethod("registerListener", String.class).invoke(this, CamelRedirectListener.class.getName());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    
    
    private boolean hasThreadPool() {
        try {
            return getClass().getMethod("getExecutor").invoke(this) != null;
        } catch (Exception ex) {
            try {
                return getClass().getMethod("getThreadPool").invoke(this) != null;
            } catch (Exception ex2) {
                throw new RuntimeException(ex);
            }
        }
    }

    void setThreadPoolOrExecutor(Executor pool) {
        try {
            getClass().getMethod("setExecutor", Executor.class).invoke(this, pool);
        } catch (Exception ex) {
            try {
                getClass().getMethod("setThreadPool", ThreadPool.class).invoke(this, pool);
            } catch (Exception ex2) {
                throw new RuntimeException(ex);
            }
        }
    }
    
    public void setProxy(String host, int port) {
        try {
            if (Server.getVersion().startsWith("8")) {
                //setProxy(new org.eclipse.jetty.client.Address(host, port));
                Class<?> c = Class.forName("org.eclipse.jetty.client.Address");
                Object o = c.getConstructor(String.class, Integer.TYPE).newInstance(host, port);
                this.getClass().getMethod("setProxy", c).invoke(this, o);
            } else {
                //getProxyConfiguration().getProxies().add(new org.eclipse.jetty.client.HttpProxy(host, port));
                Object o = this.getClass().getMethod("getProxyConfiguration").invoke(this);
                @SuppressWarnings("unchecked")
                Collection<Object> c = (Collection<Object>)o.getClass().getMethod("getProxies").invoke(o);
                c.clear();
                Class<?> cls = Class.forName("org.eclipse.jetty.client.HttpProxy");
                o = cls.getConstructor(String.class, Integer.TYPE).newInstance(host, port);
                c.add(o);
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    
    public boolean isSupportRedirect() {
        return supportRedirect;
    }

    public void setSupportRedirect(boolean supportRedirect) {
        this.supportRedirect = supportRedirect;
    }

}
