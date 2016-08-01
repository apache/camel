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
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Component that creates {@link ZooKeeperEndpoint}s for interacting with a ZooKeeper cluster.
 */
public class ZooKeeperComponent extends UriEndpointComponent {

    private ZooKeeperConfiguration configuration;

    public ZooKeeperComponent() {
        super(ZooKeeperEndpoint.class);
    }

    public ZooKeeperComponent(CamelContext context) {
        super(context, ZooKeeperEndpoint.class);
    }

    public ZooKeeperComponent(ZooKeeperConfiguration configuration) {
        super(ZooKeeperEndpoint.class);
        this.configuration = configuration;
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (getCamelContext() == null) {
            throw new CamelException("No Camel context has been provided to this zookeeper component");
        }

        ZooKeeperConfiguration config = getConfiguration().copy();
        extractConfigFromUri(uri, config);
        setProperties(config, parameters);

        return new ZooKeeperEndpoint(uri, this, config);
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
        if (configuration == null) {
            configuration = new ZooKeeperConfiguration();
        }
        return configuration;
    }

    /**
     * To use a shared ZooKeeperConfiguration. Properties of the shared configuration can also be set individually.
     */
    public void setConfiguration(ZooKeeperConfiguration configuration) {
        this.configuration = configuration;
    }

    private ZooKeeperConfiguration getConfigurationOrCreate() {
        if (this.getConfiguration() == null) {
            this.setConfiguration(new ZooKeeperConfiguration());
        }
        return this.getConfiguration();
    }

    public List<String> getServers() {
        return getConfigurationOrCreate().getServers();
    }

    /**
     * The zookeeper server hosts
     */
    public void setServers(List<String> servers) {
        getConfigurationOrCreate().setServers(servers);
    }

    public int getTimeout() {
        return getConfigurationOrCreate().getTimeout();
    }

    /**
     * The time interval to wait on connection before timing out.
     * @param timeout
     */
    public void setTimeout(int timeout) {
        getConfigurationOrCreate().setTimeout(timeout);
    }

    public boolean isListChildren() {
        return getConfigurationOrCreate().isListChildren();
    }

    /**
     * Whether the children of the node should be listed
     * @param listChildren
     */
    public void setListChildren(boolean listChildren) {
        getConfigurationOrCreate().setListChildren(listChildren);
    }

    public String getConnectString() {
        return getConfigurationOrCreate().getConnectString();
    }

    /**
     * The node in the ZooKeeper server (aka znode)
     * @param path
     */
    public void setPath(String path) {
        getConfigurationOrCreate().setPath(path);
    }

    public String getPath() {
        return getConfigurationOrCreate().getPath();
    }

    public boolean isRepeat() {
        return getConfigurationOrCreate().isRepeat();
    }

    /**
     * Should changes to the znode be 'watched' and repeatedly processed.
     * @param repeat
     */
    public void setRepeat(boolean repeat) {
        getConfigurationOrCreate().setRepeat(repeat);
    }

    public long getBackoff() {
        return getConfigurationOrCreate().getBackoff();
    }

    /**
     * The time interval to backoff for after an error before retrying.
     * @param backoff
     */
    public void setBackoff(long backoff) {
        getConfigurationOrCreate().setBackoff(backoff);
    }

    public boolean isCreate() {
        return getConfigurationOrCreate().isCreate();
    }

    /**
     * Should the endpoint create the node if it does not currently exist.
     * @param shouldCreate
     */
    public void setCreate(boolean shouldCreate) {
        getConfigurationOrCreate().setCreate(shouldCreate);
    }

    public String getCreateMode() {
        return getConfigurationOrCreate().getCreateMode();
    }

    /**
     * The create mode that should be used for the newly created node
     * @param createMode
     */
    public void setCreateMode(String createMode) {
        getConfigurationOrCreate().setCreateMode(createMode);
    }

    public boolean isSendEmptyMessageOnDelete() {
        return getConfigurationOrCreate().isSendEmptyMessageOnDelete();
    }

    /**
     * Upon the delete of a znode, should an empty message be send to the consumer
     * @param sendEmptyMessageOnDelete
     */
    public void setSendEmptyMessageOnDelete(boolean sendEmptyMessageOnDelete) {
        getConfigurationOrCreate().setSendEmptyMessageOnDelete(sendEmptyMessageOnDelete);
    }
}
