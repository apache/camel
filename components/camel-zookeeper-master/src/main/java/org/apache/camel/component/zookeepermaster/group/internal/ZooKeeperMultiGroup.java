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
package org.apache.camel.component.zookeepermaster.group.internal;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import org.apache.camel.component.zookeepermaster.group.MultiGroup;
import org.apache.camel.component.zookeepermaster.group.NodeState;
import org.apache.curator.framework.CuratorFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZooKeeperMultiGroup<T extends NodeState> extends ZooKeeperGroup<T> implements MultiGroup<T> {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public ZooKeeperMultiGroup(CuratorFramework client, String path, Class<T> clazz) {
        super(client, path, clazz);
    }

    public ZooKeeperMultiGroup(CuratorFramework client, String path, Class<T> clazz, ExecutorService executorService) {
        super(client, path, clazz, executorService);
    }

    public ZooKeeperMultiGroup(CuratorFramework client, String path, Class<T> clazz, ThreadFactory threadFactory) {
        super(client, path, clazz, threadFactory);
    }

    @Override
    public boolean isMaster(String name) {
        List<ChildData<T>> children = getActiveChildren();
        Collections.sort(children, getSequenceComparator());
        for (ChildData child : children) {
            NodeState node = (NodeState) child.getNode();
            if (node.id.equals(name)) {
                return child.getPath().equals(getId());
            }
        }
        return false;
    }

}
