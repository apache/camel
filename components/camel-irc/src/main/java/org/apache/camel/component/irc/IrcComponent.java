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

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultComponent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCEventListener;
import org.schwering.irc.lib.ssl.SSLIRCConnection;

/**
 * Defines the <a href="http://camel.apache.org/irc.html">IRC Component</a>
 *
 * @version $Revision$
 */
public class IrcComponent extends DefaultComponent {
    private static final transient Log LOG = LogFactory.getLog(IrcComponent.class);
    private IrcConfiguration configuration;
    private final Map<String, IRCConnection> connectionCache = new HashMap<String, IRCConnection>();
    private IRCEventListener ircLogger;

    public IrcComponent() {
        configuration = new IrcConfiguration();
    }

    public IrcComponent(IrcConfiguration configuration) {
        this.configuration = configuration;
    }

    public IrcComponent(CamelContext context) {
        super(context);
        configuration = new IrcConfiguration();
    }

    protected IrcEndpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        // lets make sure we copy the configuration as each endpoint can customize its own version
        IrcConfiguration config = getConfiguration().copy();
        config.configure(uri);

        IrcEndpoint endpoint = new IrcEndpoint(uri, this, config);
        setProperties(endpoint.getConfiguration(), parameters);
        return endpoint;
    }

    public synchronized IRCConnection getIRCConnection(IrcConfiguration configuration) {
        final IRCConnection connection;
        if (connectionCache.containsKey(configuration.getCacheKey())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Returning Cached Connection to " + configuration.getHostname() + ":" + configuration.getNickname());
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

        if (configuration.getUsingSSL()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating SSL Connection to " + configuration.getHostname() + " destination(s): " + configuration.getListOfChannels()
                        + " nick: " + configuration.getNickname() + " user: " + configuration.getUsername());
            }
            SSLIRCConnection sconn = new SSLIRCConnection(configuration.getHostname(), configuration.getPorts(), configuration.getPassword(),
                                                         configuration.getNickname(), configuration.getUsername(), configuration.getRealname());

            sconn.addTrustManager(configuration.getTrustManager());
            conn = sconn;

        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating Connection to " + configuration.getHostname() + " destination(s): " + configuration.getListOfChannels()
                        + " nick: " + configuration.getNickname() + " user: " + configuration.getUsername());
            }

            conn = new IRCConnection(configuration.getHostname(), configuration.getPorts(), configuration.getPassword(),
                                                         configuration.getNickname(), configuration.getUsername(), configuration.getRealname());
        }
        conn.setEncoding("UTF-8");
        conn.setColors(configuration.isColors());
        conn.setPong(true);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding IRC event logging listener");
        }

        ircLogger = createIrcLogger();
        conn.addIRCEventListener(ircLogger);

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
    protected synchronized void doStop() throws Exception {
        // lets use a copy so we can clear the connections eagerly in case of exceptions
        Map<String, IRCConnection> map = new HashMap<String, IRCConnection>(connectionCache);
        connectionCache.clear();
        for (Map.Entry<String, IRCConnection> entry : map.entrySet()) {
            closeConnection(entry.getKey(), entry.getValue());
        }
        super.doStop();
    }

    protected IRCEventListener createIrcLogger() {
        return new IrcLogger(LOG);
    }

    public IrcConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(IrcConfiguration configuration) {
        this.configuration = configuration;
    }

}
