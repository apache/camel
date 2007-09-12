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
package org.apache.camel.component.jhc;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultComponent;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.SessionRequestCallback;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.nio.protocol.BufferingHttpClientHandler;
import org.apache.http.nio.protocol.HttpRequestExecutionHandler;
import org.apache.http.nio.protocol.EventListener;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.util.concurrent.ThreadFactory;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.DefaultClientIOEventDispatch;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.protocol.*;
import org.apache.http.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.URI;
import java.util.Map;
import java.util.Iterator;
import java.io.InterruptedIOException;
import java.io.IOException;

public class JhcComponent extends DefaultComponent<JhcExchange> {

    private static Log LOG = LogFactory.getLog(JhcComponent.class);

    private HttpParams params;

    public JhcComponent() {
        params = new BasicHttpParams(null)
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, 5000)
            .setIntParameter(HttpConnectionParams.CONNECTION_TIMEOUT, 10000)
            .setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(HttpConnectionParams.TCP_NODELAY, true)
            .setParameter(HttpProtocolParams.USER_AGENT, "Camel-JhcComponent/1.1");
    }

    public HttpParams getParams() {
        return params;
    }

    public void setParams(HttpParams params) {
        this.params = params;
    }

    protected Endpoint<JhcExchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        return new JhcEndpoint(uri, this, new URI(uri.substring(uri.indexOf(':') + 1)));
    }

}