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

import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

/**
 * <code>DataChangedOperation</code> is an watch driven operation. It will wait
 * for an watched event indicating that a given node has been created or
 * deleted.
 */
public class ExistenceChangedOperation extends FutureEventDrivenOperation<String> {

    public ExistenceChangedOperation(ZooKeeper connection, String znode) {
        super(connection, znode, EventType.NodeCreated, EventType.NodeDeleted);
    }

    @Override
    protected void installWatch() {
        connection.exists(getNode(), this, new StatCallback() {
            public void processResult(int rc, String path, Object ctx, Stat stat) {
            }
        }, null);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Installed exists watch");
        }
    }

    @Override
    public OperationResult<String> getResult() {
        try {
            String path = getNode();
            Stat statistics = connection.exists(path, true);
            return new OperationResult<>(path, statistics);
        } catch (Exception e) {
            return new OperationResult<>(e);
        }
    }
}
