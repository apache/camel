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
package org.apache.camel.component.zookeeper.operations;

import java.util.List;

import org.apache.zookeeper.AsyncCallback.Children2Callback;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

/**
 * <code>ChildrenChangedOperation</code> is an watch driven operation.
 * It will wait for an watched event indicating that the children associated with a
 * given node have been modified before retrieving the changed list.
 */
public class ChildrenChangedOperation extends FutureEventDrivenOperation<List<String>> {

    private boolean getChangedListing;

    public ChildrenChangedOperation(ZooKeeper connection, String znode) {
        this(connection, znode, true);
    }

    public ChildrenChangedOperation(ZooKeeper connection, String znode, boolean getChangedListing) {
        super(connection, znode, EventType.NodeChildrenChanged);
        this.getChangedListing = getChangedListing;
    }

    @Override
    protected void installWatch() {
        connection.getChildren(getNode(), this, new Children2Callback() {
            public void processResult(int rc, String path, Object ctx, List<String> children, Stat stat) {
            }
        }, null);
    }

    @Override
    public OperationResult<List<String>> getResult() {
        return getChangedListing ? new GetChildrenOperation(connection, node).getResult() : null;
    }

    @Override
    public ZooKeeperOperation<?> createCopy() throws Exception {
        ChildrenChangedOperation copy = (ChildrenChangedOperation) super.createCopy();
        copy.getChangedListing = getChangedListing;
        return copy;
    }
}
