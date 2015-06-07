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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.jetty.CamelHttpClient;
import org.apache.camel.component.jetty.JettyHttpComponent;
import org.apache.camel.component.jetty.JettyHttpEndpoint;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class JettyHttpComponent8 extends JettyHttpComponent {

    protected CamelHttpClient createCamelHttpClient(SslContextFactory sslContextFactory) {
        return new CamelHttpClient8(sslContextFactory);
    }

    protected AbstractConnector createConnectorJettyInternal(Server server,
                                                      JettyHttpEndpoint endpoint,
                                                      SslContextFactory sslcf) {
        //Jetty 8
        AbstractConnector result = null;
        String hosto = endpoint.getHttpUri().getHost();
        int porto = endpoint.getPort();
        try {
            if (sslcf == null && !"https".equals(endpoint.getProtocol())) { 
                result = (AbstractConnector)ObjectHelper
                    .loadClass("org.eclipse.jetty.server.nio.SelectChannelConnector",
                               Server.class.getClassLoader()).newInstance();
            } else if (sslcf == null) {
                result = (AbstractConnector)ObjectHelper
                    .loadClass("org.eclipse.jetty.server.ssl.SslSelectChannelConnector",
                               Server.class.getClassLoader()).newInstance();
            } else {
                result = (AbstractConnector)ObjectHelper
                    .loadClass("org.eclipse.jetty.server.ssl.SslSelectChannelConnector",
                               Server.class.getClassLoader()).getConstructor(SslContextFactory.class)
                               .newInstance(sslcf);
            }
            Server.class.getMethod("setSendServerVersion", Boolean.TYPE).invoke(server, 
                                                                                endpoint.isSendServerVersion());
            
            Server.class.getMethod("setSendDateHeader", Boolean.TYPE).invoke(server, 
                                                                             endpoint.isSendDateHeader());
            
            
            if (result != null && requestBufferSize != null) {
                result.getClass().getMethod("setRequestBufferSize", Integer.TYPE)
                    .invoke(result, requestBufferSize);
            }
            if (result != null && requestHeaderSize != null) {
                result.getClass().getMethod("setRequestHeaderSize", Integer.TYPE)
                    .invoke(result, requestHeaderSize);
            }
            if (result != null && responseBufferSize != null) {
                result.getClass().getMethod("setResponseBufferSize", Integer.TYPE)
                    .invoke(result, responseBufferSize);
            }
            if (result != null && responseHeaderSize != null) {
                result.getClass().getMethod("setResponseHeaderSize", Integer.TYPE)
                    .invoke(result, responseHeaderSize);
            }
            result.getClass().getMethod("setPort", Integer.TYPE).invoke(result, porto);
            if (hosto != null) {
                result.getClass().getMethod("setHost", String.class).invoke(result, hosto);
            }
        } catch (RuntimeException rex) {
            throw rex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        try {
            result.getClass().getMethod("setPort", Integer.TYPE).invoke(result, porto);
            if (hosto != null) {
                result.getClass().getMethod("setHost", String.class).invoke(result, hosto);
            }
            if (getSocketConnectorProperties() != null && !"https".equals(endpoint.getProtocol())) {
                // must copy the map otherwise it will be deleted
                Map<String, Object> properties = new HashMap<String, Object>(getSocketConnectorProperties());
                IntrospectionSupport.setProperties(result, properties);
                if (properties.size() > 0) {
                    throw new IllegalArgumentException("There are " + properties.size()
                        + " parameters that couldn't be set on the SocketConnector."
                        + " Check the uri if the parameters are spelt correctly and that they are properties of the SelectChannelConnector."
                        + " Unknown parameters=[" + properties + "]");
                }
            } else if (getSslSocketConnectorProperties() != null && "https".equals(endpoint.getProtocol())) {
                // must copy the map otherwise it will be deleted
                Map<String, Object> properties = new HashMap<String, Object>(getSslSocketConnectorProperties());
                IntrospectionSupport.setProperties(result, properties);
                if (properties.size() > 0) {
                    throw new IllegalArgumentException("There are " + properties.size()
                        + " parameters that couldn't be set on the SocketConnector."
                        + " Check the uri if the parameters are spelt correctly and that they are properties of the SelectChannelConnector."
                        + " Unknown parameters=[" + properties + "]");
                }                
            }

        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        return result;
    }

    @Override
    protected JettyHttpEndpoint createEndpoint(URI endpointUri, URI httpUri) throws URISyntaxException {
        return new JettyHttpEndpoint8(this, endpointUri.toString(), httpUri);
    }
}
