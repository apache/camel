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

import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * <code>ZooKeeperEndpoint</code>
 */
@ManagedResource(description = "ZooKeeper Endpoint")
public class ZooKeeperEndpoint extends DefaultEndpoint {
    private ZooKeeperConfiguration configuration;
    private ZooKeeperConnectionManager connectionManager;

    public ZooKeeperEndpoint(String uri, ZooKeeperComponent component, ZooKeeperConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
        this.connectionManager = new ZooKeeperConnectionManager(this);
    }

    public Producer createProducer() throws Exception {
        return new ZookeeperProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        ZooKeeperConsumer answer = new ZooKeeperConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public boolean isSingleton() {
        return true;
    }

    public void setConfiguration(ZooKeeperConfiguration configuration) {
        this.configuration = configuration;
    }

    public ZooKeeperConfiguration getConfiguration() {
        return configuration;
    }

    ZooKeeperConnectionManager getConnectionManager() {
        return connectionManager;
    }

    @ManagedAttribute
    public void setPath(String path) {
        getConfiguration().setPath(path);
    }

    @ManagedAttribute
    public String getPath() {
        return getConfiguration().getPath();
    }

    @ManagedAttribute
    public int getTimeout() {
        return getConfiguration().getTimeout();
    }

    @ManagedAttribute
    public void setTimeout(int timeout) {
        getConfiguration().setTimeout(timeout);
    }

    @ManagedAttribute
    public boolean getRepeat() {
        return getConfiguration().shouldRepeat();
    }

    @ManagedAttribute
    public void setRepeat(boolean shouldRepeat) {
        getConfiguration().setRepeat(shouldRepeat);
    }

    @ManagedAttribute
    public List<String> getServers() {
        return getConfiguration().getServers();
    }

    @ManagedAttribute
    public void setServers(List<String> servers) {
        getConfiguration().setServers(servers);
    }

    @ManagedAttribute
    public boolean isListChildren() {
        return getConfiguration().isListChildren();
    }

    @ManagedAttribute
    public void setListChildren(boolean listChildren) {
        getConfiguration().setListChildren(listChildren);
    }

    @ManagedAttribute
    public boolean getCreate() {
        return getConfiguration().shouldCreate();
    }

    @ManagedAttribute
    public void setCreate(boolean shouldCreate) {
        getConfiguration().setCreate(shouldCreate);
    }

    @ManagedAttribute
    public long getBackoff() {
        return getConfiguration().getBackoff();
    }

    @ManagedAttribute
    public void setBackoff(long backoff) {
        getConfiguration().setBackoff(backoff);
    }

    /**
     * @deprecated The usage of this property has no effect at all.
     */
    @Deprecated
    @ManagedAttribute
    public boolean getAwaitExistence() {
        return getConfiguration().shouldAwaitExistence();
    }

    /**
     * @deprecated The usage of this property has no effect at all.
     */
    @Deprecated
    @ManagedAttribute
    public void setAwaitExistence(boolean awaitExistence) {
        getConfiguration().setAwaitExistence(awaitExistence);
    }

    @ManagedOperation
    public void addServer(String server) {
        getConfiguration().addZookeeperServer(server);
    }

    @ManagedOperation
    public void clearServers() {
        getConfiguration().getServers().clear();
    }

    public Object getManagedObject(ZooKeeperEndpoint arg0) {
        return this;
    }

    @ManagedAttribute
    public boolean isSendEmptyMessageOnDelete() {
        return getConfiguration().isSendEmptyMessageOnDelete();
    }

    @ManagedAttribute
    public void setSendEmptyMessageOnDelete(boolean sendEmptyMessageOnDelete) {
        getConfiguration().setSendEmptyMessageOnDelete(sendEmptyMessageOnDelete);
    }

}
