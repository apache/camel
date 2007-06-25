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
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.schwering.irc.lib.IRCConnection;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class IrcComponent extends DefaultComponent<IrcExchange> {

    private IrcConfiguration configuration;

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

    public static IrcComponent ircComponent() {
        return new IrcComponent();
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

    final Map<String, IRCConnection> connectionCache = new HashMap<String, IRCConnection>();

    public synchronized IRCConnection getIRCConnection(IrcConfiguration configuration) {
        final IRCConnection connection;
        if (connectionCache.containsKey(configuration.getCacheKey())) {
            System.out.println("Returning Cached Connection to " + configuration.getHostname() + " " + configuration.getTarget());
            connection = connectionCache.get(configuration.getCacheKey());
        } else {
            connection = createConnection(configuration);
            connectionCache.put(configuration.getCacheKey(), connection);
        }
        return connection;
    }

    protected IRCConnection createConnection(IrcConfiguration configuration) {
        System.out.println("Creating Connection to " + configuration.getHostname() + " " + configuration.getTarget());

        final IRCConnection conn = new IRCConnection(configuration.getHostname(), configuration.getPorts(), configuration.getPassword(), configuration.getNickname(), configuration.getUsername(), configuration.getRealname());
        conn.setEncoding("UTF-8");
//        conn.setDaemon(true);
        conn.setColors(configuration.isColors());
        conn.setPong(true);

        try {
            conn.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return conn;
    }

    public void closeConnection(String key, IRCConnection connection) {
        try {
            connection.doQuit();
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connectionCache.remove(key); // TODO this is probably bad in the for each
        }
    }

    @Override
    protected void doStop() throws Exception {
        for (Map.Entry<String, IRCConnection> entry : connectionCache.entrySet()) {
            closeConnection(entry.getKey(), entry.getValue());
        }
        super.doStop();
    }
}
