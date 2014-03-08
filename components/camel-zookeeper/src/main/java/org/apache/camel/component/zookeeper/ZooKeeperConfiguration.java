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

/**
 * <code>ZookeeperConfiguration</code> encapsulates the configuration used to
 * interact with a ZooKeeper cluster. Most typically it is parsed from endpoint
 * uri but may also be configured programatically and applied to a
 * {@link ZooKeeperComponent}. A copy of this component's configuration will be
 * injected into any {@link ZooKeeperEndpoint}s the component creates.
 */
public class ZooKeeperConfiguration implements Cloneable {

    private transient boolean changed;

    private int timeout = 5000;
    private long backoff = 5000;
    private List<String> servers;
    private String path;
    private boolean awaitExistence = true;
    private boolean repeat;
    private boolean listChildren;
    private boolean shouldCreate;
    private String createMode;
    private boolean sendEmptyMessageOnDelete = true;

    public void addZookeeperServer(String server) {
        if (servers == null) {
            servers = new ArrayList<String>();
        }
        servers.add(server);
        changed = true;
    }

    public List<String> getServers() {
        return servers;
    }

    public void setServers(List<String> servers) {
        this.servers = servers;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
        changed = true;
    }

    public boolean isListChildren() {
        return listChildren;
    }

    public void setListChildren(boolean listChildren) {
        this.listChildren = listChildren;
    }

    public void clearChanged() {
        changed = false;
    }

    public boolean isChanged() {
        return changed;
    }

    public String getConnectString() {
        StringBuilder b = new StringBuilder();
        for (String server : servers) {
            b.append(server).append(",");
        }
        b.setLength(b.length() - 1);
        return b.toString();

    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public boolean shouldRepeat() {
        return repeat;
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    public ZooKeeperConfiguration copy() {
        try {
            return (ZooKeeperConfiguration)clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    /**
     * @deprecated The usage of this option has no effect at all.
     */
    @Deprecated
    public boolean shouldAwaitExistence() {
        return awaitExistence;
    }

    /**
     * @deprecated The usage of this option has no effect at all.
     */
    @Deprecated
    public void setAwaitExistence(boolean awaitExistence) {
        this.awaitExistence = awaitExistence;
    }

    public long getBackoff() {
        return backoff;
    }

    public void setBackoff(long backoff) {
        this.backoff = backoff;
    }

    public void setCreate(boolean shouldCreate) {
        this.shouldCreate = shouldCreate;
    }

    public boolean shouldCreate() {
        return shouldCreate;
    }

    public String getCreateMode() {
        return createMode;
    }

    public void setCreateMode(String createMode) {
        this.createMode = createMode;
    }

    public boolean isSendEmptyMessageOnDelete() {
        return sendEmptyMessageOnDelete;
    }

    public void setSendEmptyMessageOnDelete(boolean sendEmptyMessageOnDelete) {
        this.sendEmptyMessageOnDelete = sendEmptyMessageOnDelete;
    }

}
