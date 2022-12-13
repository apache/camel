/*
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
package org.apache.camel.component.jetty10;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.jetty.JettyHttpComponent;
import org.apache.camel.component.jetty.JettyHttpEndpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.PropertyBindingSupport;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("jetty")
public class JettyHttpComponent10 extends JettyHttpComponent {

    public static Map<String, Throwable> connectorCreation = new ConcurrentHashMap<>();

    private static final Logger LOG = LoggerFactory.getLogger(JettyHttpComponent10.class);

    @Override
    protected JettyHttpEndpoint createEndpoint(URI endpointUri, URI httpUri) throws URISyntaxException {
        return new JettyHttpEndpoint10(this, endpointUri.toString(), httpUri);
    }

    @Override
    protected AbstractConnector createConnectorJettyInternal(
            Server server, JettyHttpEndpoint endpoint, SslContextFactory.Server sslcf) {
        try {
            String host = endpoint.getHttpUri().getHost();
            int port = endpoint.getPort();
            org.eclipse.jetty.server.HttpConfiguration httpConfig = new org.eclipse.jetty.server.HttpConfiguration();
            httpConfig.setSendServerVersion(endpoint.isSendServerVersion());
            httpConfig.setSendDateHeader(endpoint.isSendDateHeader());

            if (requestBufferSize != null) {
                // Does not work
                // httpConfig.setRequestBufferSize(requestBufferSize);
            }
            if (requestHeaderSize != null) {
                httpConfig.setRequestHeaderSize(requestHeaderSize);
            }
            if (responseBufferSize != null) {
                httpConfig.setOutputBufferSize(responseBufferSize);
            }
            if (responseHeaderSize != null) {
                httpConfig.setResponseHeaderSize(responseHeaderSize);
            }
            if (useXForwardedForHeader) {
                httpConfig.addCustomizer(new ForwardedRequestCustomizer());
            }
            HttpConnectionFactory httpFactory = new org.eclipse.jetty.server.HttpConnectionFactory(httpConfig);

            ArrayList<ConnectionFactory> connectionFactories = new ArrayList<>();
            ServerConnector result = new org.eclipse.jetty.server.ServerConnector(server);
            if (sslcf != null) {
                httpConfig.addCustomizer(new org.eclipse.jetty.server.SecureRequestCustomizer());
                SslConnectionFactory scf = new org.eclipse.jetty.server.SslConnectionFactory(sslcf, "HTTP/1.1");
                connectionFactories.add(scf);
                // The protocol name can be "SSL" or "SSL-HTTP/1.1" depending on
                // the version of Jetty
                result.setDefaultProtocol(scf.getProtocol());
            }
            connectionFactories.add(httpFactory);
            for (ConnectionFactory cf : connectionFactories) {
                result.addConnectionFactory(cf);
            }
            result.setPort(port);
            if (host != null) {
                result.setHost(host);
            }
            if (sslcf != null) {
                if (getSslSocketConnectorProperties() != null && "https".equals(endpoint.getProtocol())) {
                    // must copy the map otherwise it will be deleted
                    Map<String, Object> properties = new HashMap<>(getSslSocketConnectorProperties());
                    PropertyBindingSupport.bindProperties(getCamelContext(), sslcf, properties);
                    if (properties.size() > 0) {
                        throw new IllegalArgumentException(
                                "There are " + properties.size() + " parameters that couldn't be set on the SocketConnector."
                                                           + " Check the uri if the parameters are spelt correctly and that they are properties of the SelectChannelConnector."
                                                           + " Unknown parameters=[" + properties + "]");
                    }
                }

                LOG.info(
                        "Connector on port: {} is using includeCipherSuites: {} excludeCipherSuites: {} includeProtocols: {} excludeProtocols: {}",
                        port,
                        sslcf.getIncludeCipherSuites(), sslcf.getExcludeCipherSuites(), sslcf.getIncludeProtocols(),
                        sslcf.getExcludeProtocols());
            }

            String ckey = endpoint.getProtocol() + ":" + endpoint.getHttpUri().getHost() + ":" + endpoint.getPort();
            connectorCreation.computeIfAbsent(ckey, c -> new Throwable());
            return result;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

}
