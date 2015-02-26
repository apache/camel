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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.jetty.CamelHttpClient;
import org.apache.camel.component.jetty.JettyHttpComponent;
import org.apache.camel.component.jetty.JettyHttpEndpoint;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class JettyHttpComponent9 extends JettyHttpComponent {

    protected CamelHttpClient createCamelHttpClient(SslContextFactory sslContextFactory) {
        return new CamelHttpClient9(sslContextFactory);
    }

    protected JettyHttpEndpoint createEndpoint(URI endpointUri, URI httpUri) throws URISyntaxException {
        return new JettyHttpEndpoint9(this, endpointUri.toString(), httpUri);
    }
    
    protected Connector getSslSocketConnector(Server server, JettyHttpEndpoint endpoint) {
        Connector answer = null;
        /*
        if (sslSocketConnectors != null) {
            SslContextFactory con = sslSocketConnectors.get(endpoint.getPort());
            if (con != null) {
                SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(con, null);
                @SuppressWarnings("resource")
                ServerConnector sc = new ServerConnector(server, sslConnectionFactory);
                sc.setPort(endpoint.getPort());
                sc.setHost(endpoint.getHttpUri().getHost());
                answer = sc;
            }
        }
        */
        if (answer == null) {
            answer = createConnector(server, endpoint);
        }
        return answer;
    }
    
    protected AbstractConnector createConnectorJettyInternal(Server server,
                                                      JettyHttpEndpoint endpoint,
                                                      SslContextFactory sslcf) {
        try {
            String hosto = endpoint.getHttpUri().getHost();
            int porto = endpoint.getPort();
            org.eclipse.jetty.server.HttpConfiguration httpConfig = new org.eclipse.jetty.server.HttpConfiguration();
            httpConfig.setSendServerVersion(endpoint.isSendServerVersion());
            httpConfig.setSendDateHeader(endpoint.isSendDateHeader());
            httpConfig.setSendDateHeader(endpoint.isSendDateHeader());
            
            if (requestBufferSize != null) {
                // Does not work
                //httpConfig.setRequestBufferSize(requestBufferSize);
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
            
            HttpConnectionFactory httpFactory = new org.eclipse.jetty.server.HttpConnectionFactory(httpConfig); 

            ArrayList<ConnectionFactory> connectionFactories = new ArrayList<ConnectionFactory>();
            ServerConnector result = new org.eclipse.jetty.server.ServerConnector(server);
            if (sslcf != null) {
                httpConfig.addCustomizer(new org.eclipse.jetty.server.SecureRequestCustomizer());
                SslConnectionFactory scf = new org.eclipse.jetty.server.SslConnectionFactory(sslcf, "HTTP/1.1");
                connectionFactories.add(scf);
                result.setDefaultProtocol("SSL-HTTP/1.1");
            }
            connectionFactories.add(httpFactory);
            result.setConnectionFactories(connectionFactories);
            result.setPort(porto);
            if (hosto != null) {
                result.setHost(hosto);
            }
            /*
            if (getSocketConnectorProperties() != null && !"https".equals(endpoint.getProtocol())) {
                // must copy the map otherwise it will be deleted
                Map<String, Object> properties = new HashMap<String, Object>(getSocketConnectorProperties());
                IntrospectionSupport.setProperties(httpConfig, properties);
                if (properties.size() > 0) {
                    throw new IllegalArgumentException("There are " + properties.size()
                        + " parameters that couldn't be set on the SocketConnector."
                        + " Check the uri if the parameters are spelt correctly and that they are properties of the SelectChannelConnector."
                        + " Unknown parameters=[" + properties + "]");
                }
            } else*/
            if (getSslSocketConnectorProperties() != null && "https".equals(endpoint.getProtocol())) {
                // must copy the map otherwise it will be deleted
                Map<String, Object> properties = new HashMap<String, Object>(getSslSocketConnectorProperties());
                IntrospectionSupport.setProperties(sslcf, properties);
                if (properties.size() > 0) {
                    throw new IllegalArgumentException("There are " + properties.size()
                        + " parameters that couldn't be set on the SocketConnector."
                        + " Check the uri if the parameters are spelt correctly and that they are properties of the SelectChannelConnector."
                        + " Unknown parameters=[" + properties + "]");
                }                
            }
            return result;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }
}
