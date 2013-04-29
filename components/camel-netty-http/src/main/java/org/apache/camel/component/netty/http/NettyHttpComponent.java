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
package org.apache.camel.component.netty.http;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.netty.NettyComponent;
import org.apache.camel.component.netty.NettyConfiguration;

public class NettyHttpComponent extends NettyComponent {

    private NettyHttpBinding nettyHttpBinding;

    public NettyHttpComponent() {
        // use the http configuration
        setConfiguration(new NettyHttpConfiguration());
    }

    // TODO: allow to turn mapMessage=true|false
    // TODO: netty http producer
    // TODO: make it easy to turn chunked on|off
    // TODO: make it easy to turn compression on|off
    // TODO: use HeaderFilterStrategy to filter headers
    // TODO: add logging

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        NettyConfiguration config;
        if (getConfiguration() != null) {
            config = getConfiguration().copy();
        } else {
            config = new NettyHttpConfiguration();
        }

        config = parseConfiguration(config, remaining, parameters);

        // validate config
        config.validateConfiguration();

        NettyHttpEndpoint nettyEndpoint = new NettyHttpEndpoint(remaining, this, config);
        nettyEndpoint.setTimer(getTimer());
        setProperties(nettyEndpoint.getConfiguration(), parameters);
        return nettyEndpoint;
    }

    @Override
    protected NettyConfiguration parseConfiguration(NettyConfiguration configuration, String remaining, Map<String, Object> parameters) throws Exception {
        configuration.parseURI(new URI(remaining), parameters, this, "http", "https");

        // force using tcp as the underlying transport
        configuration.setProtocol("tcp");
        configuration.setTextline(false);

        return configuration;
    }

    public NettyHttpBinding getNettyHttpBinding() {
        return nettyHttpBinding;
    }

    public void setNettyHttpBinding(NettyHttpBinding nettyHttpBinding) {
        this.nettyHttpBinding = nettyHttpBinding;
    }
}
