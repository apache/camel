/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.jmxconnect;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spring.remoting.CamelServiceExporter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * <p>The client end of a JMX API connector.  An object of this type can
 * be used to establish a connection to a connector server.</p>
 * <p/>
 * <p>A newly-created object of this type is unconnected.  Its {@link#connect}
 * method must be called before it can be used.
 * However, objects created by {@link
 * JMXConnectorFactory#connect(JMXServiceURL, Map)
 * JMXConnectorFactory.connect} are already connected.</p>
 *
 * @version $Revision$
 */
public class CamelJmxConnectorServer extends JMXConnectorServer implements CamelContextAware {

    private static final Log log = LogFactory.getLog(CamelJmxConnectorServer.class);
    private JMXServiceURL url;
    private final Map env;
    private volatile boolean stopped = true;
    private CamelServiceExporter service;
    private MBeanCamelServerConnectionImpl serverConnection;
    private String endpointUri;
    private CamelContext camelContext;


    public CamelJmxConnectorServer(JMXServiceURL url, String endpointUri, Map environment, MBeanServer server) throws IOException {
        super(server);
        this.url = url;
        this.env = environment;
        this.endpointUri = endpointUri;
    }

    public CamelJmxConnectorServer(JMXServiceURL url, Map environment, MBeanServer server) throws IOException {
        this(url, CamelJmxConnectorSupport.getEndpointUri(url, "camel"), environment, server);
        //set any props in the url
        // TODO
        // populateProperties(this, endpointUri);
    }

    /**
     * start the connector
     *
     * @throws IOException
     */
    public void start() throws IOException {
        try {
            service = new CamelServiceExporter();
            service.setCamelContext(getCamelContext());
            service.setServiceInterface(MBeanCamelServerConnection.class);
            service.setUri(endpointUri);

            this.serverConnection = new MBeanCamelServerConnectionImpl(getMBeanServer(), /* TODO */ null);
            service.setService(serverConnection);

            service.afterPropertiesSet();
            stopped = false;
        } catch (Exception e) {
            log.error("Failed to start ", e);
            throw new IOException(e.toString());
        }

    }

    /**
     * stop the connector
     *
     * @throws IOException
     */
    public void stop() throws IOException {
        try {
            if (!stopped) {
                stopped = true;
                service.destroy();
            }
        } catch (Exception e) {
            log.error("Failed to stop ", e);
            throw new IOException(e.toString());
        }

    }

    public boolean isActive() {
        return !stopped;
    }

    public JMXServiceURL getAddress() {
        return url;
    }

    public Map getAttributes() {
        return Collections.unmodifiableMap(env);
    }


    public CamelContext getCamelContext() {
        if (camelContext == null) {
            log.warn("No CamelContext injected so creating a default implementation");
            // TODO should we barf or create a default one?
            camelContext = new DefaultCamelContext();
        }
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
}