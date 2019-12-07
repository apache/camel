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
package org.apache.camel.component.zookeeper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Component that creates {@link ZooKeeperEndpoint}s for interacting with a ZooKeeper cluster.
 */
@Component("zookeeper")
public class ZooKeeperComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private ZooKeeperConfiguration configuration = new ZooKeeperConfiguration();

    public ZooKeeperComponent() {
    }

    public ZooKeeperComponent(ZooKeeperConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (getCamelContext() == null) {
            throw new CamelException("No Camel context has been provided to this zookeeper component");
        }

        ZooKeeperConfiguration config = getConfiguration().copy();
        extractConfigFromUri(uri, config);

        Endpoint endpoint = new ZooKeeperEndpoint(uri, this, config);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    private void extractConfigFromUri(String remaining, ZooKeeperConfiguration config) throws URISyntaxException {
        URI fullUri = new URI(remaining);
        String[] hosts = fullUri.getAuthority().split(",");
        for (String host : hosts) {
            config.addZookeeperServer(host.trim());
        }
        config.setPath(fullUri.getPath());
    }

    public ZooKeeperConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use a shared {@link ZooKeeperConfiguration}
     */
    public void setConfiguration(ZooKeeperConfiguration configuration) {
        this.configuration = configuration;
    }
}
