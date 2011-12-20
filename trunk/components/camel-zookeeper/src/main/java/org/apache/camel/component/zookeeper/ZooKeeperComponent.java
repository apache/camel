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
package org.apache.camel.component.zookeeper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

/**
 * Component that creates {@link ZooKeeperEndpoint}s for interacting with a ZooKeeper cluster.
 */
public class ZooKeeperComponent extends DefaultComponent {

    private ZooKeeperConfiguration configuration;

    public ZooKeeperComponent() {
    }

    public ZooKeeperComponent(CamelContext context) {
        super(context);
    }

    public ZooKeeperComponent(ZooKeeperConfiguration configuration) {
        this.configuration = configuration;
    }

    @SuppressWarnings("all")
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        if (getCamelContext() == null) {
            throw new CamelException("No Camel context has been provided to this zookeeper component");
        }

        ZooKeeperConfiguration config = getConfiguration();
        extractConfigFromUri(uri, config);
        setProperties(config, parameters);

        return new ZooKeeperEndpoint(uri, this, config.copy());
    }

    private void extractConfigFromUri(String remaining, ZooKeeperConfiguration config) throws URISyntaxException {
        URI u = new URI(remaining);
        config.addZookeeperServer(u.getHost() + (u.getPort() != -1 ? ":" + u.getPort() : ""));
        config.setPath(u.getPath());
    }

    public ZooKeeperConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new ZooKeeperConfiguration();
        }
        return configuration;
    }

    public void setConfiguration(ZooKeeperConfiguration configuration) {
        this.configuration = configuration;
    }
}
