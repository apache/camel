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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.CollectionStringBuffer;

/**
 * <code>ZookeeperConfiguration</code> encapsulates the configuration used to
 * interact with a ZooKeeper cluster. Most typically it is parsed from endpoint
 * uri but may also be configured programatically and applied to a
 * {@link ZooKeeperComponent}. A copy of this component's configuration will be
 * injected into any {@link ZooKeeperEndpoint}s the component creates.
 */
@UriParams
public class ZooKeeperConfiguration implements Cloneable {

    @UriPath @Metadata(required = "true")
    private String serverUrls;
    private List<String> servers;
    @UriPath @Metadata(required = "true")
    private String path;
    @UriParam(defaultValue = "5000")
    private int timeout = 5000;
    @UriParam(label = "consumer", defaultValue = "5000")
    private long backoff = 5000;
    @UriParam(defaultValue = "true")
    @Deprecated
    private boolean awaitExistence = true;
    @UriParam(label = "consumer")
    private boolean repeat;
    @UriParam
    private boolean listChildren;
    @UriParam(label = "producer")
    private boolean create;
    @UriParam(label = "producer", enums = "PERSISTENT,PERSISTENT_SEQUENTIAL,EPHEMERAL,EPHEMERAL_SEQUENTIAL", defaultValue = "EPHEMERAL")
    private String createMode;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean sendEmptyMessageOnDelete = true;

    public void addZookeeperServer(String server) {
        if (servers == null) {
            servers = new ArrayList<String>();
        }
        servers.add(server);
    }

    public ZooKeeperConfiguration copy() {
        try {
            return (ZooKeeperConfiguration)clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public List<String> getServers() {
        return servers;
    }

    /**
     * The zookeeper server hosts (multiple servers can be separated by comma)
     */
    public String getServerUrls() {
        if (servers != null) {
            CollectionStringBuffer csb = new CollectionStringBuffer(",");
            for (String server : servers) {
                csb.append(server);
            }
            return csb.toString();
        }
        return null;
    }

    /**
     * The zookeeper server hosts
     */
    public void setServers(List<String> servers) {
        this.servers = servers;
    }

    public int getTimeout() {
        return timeout;
    }

    /**
     * The time interval to wait on connection before timing out.
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public boolean isListChildren() {
        return listChildren;
    }

    /**
     * Whether the children of the node should be listed
     */
    public void setListChildren(boolean listChildren) {
        this.listChildren = listChildren;
    }

    public String getConnectString() {
        StringBuilder b = new StringBuilder();
        for (String server : servers) {
            b.append(server).append(",");
        }
        b.setLength(b.length() - 1);
        return b.toString();

    }

    /**
     * The node in the ZooKeeper server (aka znode)
     */
    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public boolean isRepeat() {
        return repeat;
    }

    /**
     * Should changes to the znode be 'watched' and repeatedly processed.
     */
    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    /**
     * @deprecated The usage of this option has no effect at all.
     */
    @Deprecated
    public boolean shouldAwaitExistence() {
        return awaitExistence;
    }

    /**
     * Not in use
     * @deprecated The usage of this option has no effect at all.
     */
    @Deprecated
    public void setAwaitExistence(boolean awaitExistence) {
        this.awaitExistence = awaitExistence;
    }

    public long getBackoff() {
        return backoff;
    }

    /**
     * The time interval to backoff for after an error before retrying.
     */
    public void setBackoff(long backoff) {
        this.backoff = backoff;
    }

    public boolean isCreate() {
        return create;
    }

    /**
     * Should the endpoint create the node if it does not currently exist.
     */
    public void setCreate(boolean shouldCreate) {
        this.create = shouldCreate;
    }

    public String getCreateMode() {
        return createMode;
    }

    /**
     * The create mode that should be used for the newly created node
     */
    public void setCreateMode(String createMode) {
        this.createMode = createMode;
    }

    public boolean isSendEmptyMessageOnDelete() {
        return sendEmptyMessageOnDelete;
    }

    /**
     * Upon the delete of a znode, should an empty message be send to the consumer
     */
    public void setSendEmptyMessageOnDelete(boolean sendEmptyMessageOnDelete) {
        this.sendEmptyMessageOnDelete = sendEmptyMessageOnDelete;
    }

}
