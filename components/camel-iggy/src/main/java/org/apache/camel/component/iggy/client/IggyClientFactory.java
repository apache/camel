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
package org.apache.camel.component.iggy.client;

import org.apache.camel.RuntimeCamelException;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.iggy.client.blocking.IggyBaseClient;
import org.apache.iggy.client.blocking.IggyClientBuilder;
import org.apache.iggy.client.blocking.http.IggyHttpClient;
import org.apache.iggy.client.blocking.tcp.IggyTcpClient;

public class IggyClientFactory extends BasePooledObjectFactory<IggyBaseClient> {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String transport;

    public IggyClientFactory(String host, int port, String username, String password, String transport) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.transport = transport;
    }

    @Override
    public IggyBaseClient create() throws Exception {
        IggyBaseClient iggyBaseClient = new IggyClientBuilder().withBaseClient(createClient()).build().getBaseClient();

        loginIggyClient(iggyBaseClient);

        return iggyBaseClient;
    }

    @Override
    public PooledObject<IggyBaseClient> wrap(IggyBaseClient iggyBaseClient) {
        return new DefaultPooledObject<>(iggyBaseClient);
    }

    private IggyBaseClient createClient() {
        return switch (transport) {
            case "HTTP" ->
                new IggyHttpClient(String.format("http://%s:%d", host, port));
            case "TCP" -> new IggyTcpClient(host, port);
            default -> throw new RuntimeCamelException("Only HTTP or TCP transports are supported");
        };
    }

    private void loginIggyClient(IggyBaseClient client) {
        if (username != null) {
            client.users().login(username, password);
        }
    }
}
