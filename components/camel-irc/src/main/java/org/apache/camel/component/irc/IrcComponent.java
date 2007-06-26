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
package org.apache.camel.component.irc;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.schwering.irc.lib.IRCConnection;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines the <a href="http://activemq.apache.org/camel/irc.html">IRC Component</a>
 *
 * @version $Revision:$
 */
public class IrcComponent extends DefaultComponent<IrcExchange> {
    private static final transient Log log = LogFactory.getLog(IrcComponent.class);
    private IrcConfiguration configuration;
    private final Map<String, IRCConnection> connectionCache = new HashMap<String, IRCConnection>();

    public static IrcComponent ircComponent() {
        return new IrcComponent();
    }

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
        IrcConfiguration config = getConfiguration().copy();
        config.configure(new URI(uri));

        // lets make sure we copy the configuration as each endpoint can customize its own version
        final IrcEndpoint endpoint = new IrcEndpoint(uri, this, config);

        IntrospectionSupport.setProperties(endpoint.getConfiguration(), parameters);
        return endpoint;
    }

    public IrcConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(IrcConfiguration configuration) {
        this.configuration = configuration;
    }

    public synchronized IRCConnection getIRCConnection(IrcConfiguration configuration) {
        final IRCConnection connection;
        if (connectionCache.containsKey(configuration.getCacheKey())) {
            if (log.isDebugEnabled()) {
                log.debug("Returning Cached Connection to " + configuration.getHostname() + " " + configuration.getTarget());
            }
            connection = connectionCache.get(configuration.getCacheKey());
        }
        else {
            connection = createConnection(configuration);
            connectionCache.put(configuration.getCacheKey(), connection);
        }
        return connection;
    }

    protected IRCConnection createConnection(IrcConfiguration configuration) {
        log.debug("Creating Connection to " + configuration.getHostname() + " destination: " + configuration.getTarget()
                + " nick: " + configuration.getNickname() + " user: " + configuration.getUsername());

        final IRCConnection conn = new IRCConnection(configuration.getHostname(), configuration.getPorts(), configuration.getPassword(), configuration.getNickname(), configuration.getUsername(), configuration.getRealname());
        conn.setEncoding("UTF-8");
//        conn.setDaemon(true);
        conn.setColors(configuration.isColors());
        conn.setPong(true);

        try {
            conn.connect();
        }
        catch (Exception e) {
            log.error("Failed to connect: " + e, e);

            // TODO use checked exceptions?
            throw new RuntimeCamelException(e);
        }
        return conn;
    }

    public void closeConnection(String key, IRCConnection connection) {
        try {
            connection.doQuit();
            connection.close();
        }
        catch (Exception e) {
            e.printStackTrace();
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
}
