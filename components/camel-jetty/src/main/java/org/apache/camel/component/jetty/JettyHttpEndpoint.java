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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.camel.component.http.HttpConsumer;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.client.Address;
import org.mortbay.jetty.client.HttpClient;

/**
 * @version $Revision$
 */
public class JettyHttpEndpoint extends HttpEndpoint {

    private static final transient Log LOG = LogFactory.getLog(JettyHttpEndpoint.class);
    private boolean sessionSupport;
    private List<Handler> handlers;

    public JettyHttpEndpoint(JettyHttpComponent component, String uri, URI httpURL, HttpClientParams clientParams,
                             HttpConnectionManager httpConnectionManager, HttpClientConfigurer clientConfigurer) throws URISyntaxException {
        super(uri, component, httpURL, clientParams, httpConnectionManager, clientConfigurer);
    }

    public JettyHttpEndpoint(JettyHttpComponent component, String uri, URI httpURL) throws URISyntaxException {
        super(uri, component, httpURL);
    }

    @Override
    public JettyHttpComponent getComponent() {
        return (JettyHttpComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new JettyHttpProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new HttpConsumer(this, processor);
    }   

    public void setSessionSupport(boolean support) {
        sessionSupport = support;
    }

    public boolean isSessionSupport() {
        return sessionSupport;
    }

    public List<Handler> getHandlers() {
        return handlers;
    }

    public void setHandlers(List<Handler> handlers) {
        this.handlers = handlers;
    }

    /**
     * Factory method used by producers and consumers to create a new {@link org.apache.commons.httpclient.HttpClient} instance
     */
    public HttpClient getJettyHttpClient() throws Exception {
        HttpClient answer = getComponent().getHttpClient();
        if (answer == null) {
            answer = new HttpClient();
            answer.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);

            if (System.getProperty("http.proxyHost") != null && System.getProperty("http.proxyPort") != null) {
                String host = System.getProperty("http.proxyHost");
                int port = Integer.parseInt(System.getProperty("http.proxyPort"));
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Java System Property http.proxyHost and http.proxyPort detected. Using http proxy host: "
                            + host + " port: " + port);
                }
                answer.setProxy(new Address(host, port));
            }

            // TODO: allow jetty producer configuration from uri

            answer.start();
        }
        return answer;
    }
    
}
