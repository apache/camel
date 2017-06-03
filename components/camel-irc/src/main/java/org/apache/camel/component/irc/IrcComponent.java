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
package org.apache.camel.component.irc;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCEventListener;
import org.schwering.irc.lib.ssl.SSLIRCConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the <a href="http://camel.apache.org/irc.html">IRC Component</a>
 *
 * @version
 */
public class IrcComponent extends UriEndpointComponent implements SSLContextParametersAware {
    private static final Logger LOG = LoggerFactory.getLogger(IrcComponent.class);
    private final transient Map<String, IRCConnection> connectionCache = new HashMap<String, IRCConnection>();

    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public IrcComponent() {
        super(IrcEndpoint.class);
    }

    public IrcEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // every endpoint gets it's own configuration
        IrcConfiguration config = new IrcConfiguration();
        config.configure(uri);

        IrcEndpoint endpoint = new IrcEndpoint(uri, this, config);
        setProperties(endpoint.getConfiguration(), parameters);
        return endpoint;
    }

    public synchronized IRCConnection getIRCConnection(IrcConfiguration configuration) {
        final IRCConnection connection;
        if (connectionCache.containsKey(configuration.getCacheKey())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Returning Cached Connection to {}:{}", configuration.getHostname(), configuration.getNickname());
            }
            connection = connectionCache.get(configuration.getCacheKey());
        } else {
            connection = createConnection(configuration);
            connectionCache.put(configuration.getCacheKey(), connection);
        }
        return connection;
    }

    protected IRCConnection createConnection(IrcConfiguration configuration) {
        IRCConnection conn = null;
        IRCEventListener ircLogger;

        if (configuration.getUsingSSL()) {

            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating SSL Connection to {} destination(s): {} nick: {} user: {}",
                    new Object[]{configuration.getHostname(), configuration.getListOfChannels(), configuration.getNickname(), configuration.getUsername()});
            }

            SSLContextParameters sslParams = configuration.getSslContextParameters();
            if (sslParams == null) {
                sslParams = retrieveGlobalSslContextParameters();
            }

            if (sslParams != null) {
                conn = new CamelSSLIRCConnection(configuration.getHostname(), configuration.getPorts(), configuration.getPassword(),
                                                 configuration.getNickname(), configuration.getUsername(), configuration.getRealname(),
                                                 sslParams, getCamelContext());
            } else {
                SSLIRCConnection sconn = new SSLIRCConnection(configuration.getHostname(), configuration.getPorts(), configuration.getPassword(),
                        configuration.getNickname(), configuration.getUsername(), configuration.getRealname());

                sconn.addTrustManager(configuration.getTrustManager());
                conn = sconn;
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating Connection to {} destination(s): {} nick: {} user: {}",
                        new Object[]{configuration.getHostname(), configuration.getListOfChannels(), configuration.getNickname(), configuration.getUsername()});
            }

            conn = new IRCConnection(configuration.getHostname(), configuration.getPorts(), configuration.getPassword(),
                    configuration.getNickname(), configuration.getUsername(), configuration.getRealname());
        }
        conn.setEncoding("UTF-8");
        conn.setColors(configuration.isColors());
        conn.setPong(true);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding IRC event logging listener");
            ircLogger = createIrcLogger(configuration.getHostname());
            conn.addIRCEventListener(ircLogger);
        }

        try {
            conn.connect();
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
        return conn;
    }

    public void closeConnection(String key, IRCConnection connection) {
        try {
            connection.doQuit();
            connection.close();
        } catch (Exception e) {
            LOG.warn("Error during closing connection.", e);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // lets use a copy so we can clear the connections eagerly in case of exceptions
        Map<String, IRCConnection> map = new HashMap<String, IRCConnection>(connectionCache);
        connectionCache.clear();
        for (Map.Entry<String, IRCConnection> entry : map.entrySet()) {
            closeConnection(entry.getKey(), entry.getValue());
        }
        super.doStop();
    }

    protected IRCEventListener createIrcLogger(String hostname) {
        return new IrcLogger(LOG, hostname);
    }

    @Deprecated
    protected String preProcessUri(String uri) {
        return IrcConfiguration.sanitize(uri);
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }
}
