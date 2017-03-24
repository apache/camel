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
package org.apache.camel.component.zookeepermaster.group.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.component.zookeepermaster.group.Group;
import org.apache.camel.component.zookeepermaster.group.GroupListener;
import org.apache.camel.component.zookeepermaster.group.NodeState;
import org.apache.curator.framework.CuratorFramework;

import static org.apache.curator.utils.CloseableUtils.closeQuietly;

public class DelegateZooKeeperGroup<T extends NodeState> implements Group<T> {

    private final String path;
    private final Class<T> clazz;
    private final List<GroupListener<T>> listeners;
    private Group<T> group;
    private T state;
    private AtomicBoolean started = new AtomicBoolean();

    public DelegateZooKeeperGroup(String path, Class<T> clazz) {
        this.listeners = new ArrayList<GroupListener<T>>();
        this.path = path;
        this.clazz = clazz;
    }

    public void useCurator(CuratorFramework curator) {
        Group<T> group = this.group;
        if (group != null) {
            closeQuietly(group);
        }
        if (curator != null) {
            group = createGroup(curator, path, clazz);
            group.update(state);
            for (GroupListener<T> listener : listeners) {
                group.add(listener);
            }
            if (started.get()) {
                group.start();
            }
            this.group = group;
        }
    }

    protected Group<T> createGroup(CuratorFramework client, String path, Class<T> clazz) {
        return new ZooKeeperGroup<T>(client, path, clazz);
    }

    @Override
    public void add(GroupListener<T> listener) {
        listeners.add(listener);
        Group<T> group = this.group;
        if (group != null) {
            group.add(listener);
        }
    }

    @Override
    public void remove(GroupListener<T> listener) {
        listeners.remove(listener);
        Group<T> group = this.group;
        if (group != null) {
            group.remove(listener);
        }
    }

    @Override
    public boolean isConnected() {
        Group<T> group = this.group;
        if (group != null) {
            return group.isConnected();
        } else {
            return false;
        }
    }

    @Override
    public void start() {
        if (started.compareAndSet(false, true)) {
            doStart();
        }
    }

    protected void doStart() {
        if (group != null) {
            group.start();
        }
    }

    @Override
    public void close() throws IOException {
        if (started.compareAndSet(true, false)) {
            doStop();
        }
    }

    protected void doStop() throws IOException {
        closeQuietly(group);
    }

    @Override
    public void update(T state) {
        this.state = state;
        Group<T> group = this.group;
        if (group != null) {
            group.update(state);
        }
    }

    @Override
    public Map<String, T> members() {
        Group<T> group = this.group;
        if (group != null) {
            return group.members();
        } else {
            return Collections.emptyMap();
        }
    }

    @Override
    public boolean isMaster() {
        Group<T> group = this.group;
        if (group != null) {
            return group.isMaster();
        } else {
            return false;
        }
    }

    @Override
    public T master() {
        Group<T> group = this.group;
        if (group != null) {
            return group.master();
        } else {
            return null;
        }
    }

    @Override
    public List<T> slaves() {
        Group<T> group = this.group;
        if (group != null) {
            return group.slaves();
        } else {
            return Collections.emptyList();
        }
    }

    public Group<T> getGroup() {
        return group;
    }

    @Override
    public T getLastState() {
        return group != null ? group.getLastState() : null;
    }

}
