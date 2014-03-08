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
package org.apache.camel.component.zookeeper.operations;

import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

/**
 * <code>DataChangedOperation</code> is an watch driven operation. It will wait
 * for an watched event indicating that the data contained in a given
 * node has changed before optionally retrieving the changed data.
 */
@SuppressWarnings("rawtypes")
public class DataChangedOperation extends FutureEventDrivenOperation<byte[]> {

    protected static final Class[] CONSTRUCTOR_ARGS = {ZooKeeper.class, String.class, boolean.class, boolean.class};
    
    private boolean getChangedData;
    private boolean sendEmptyMessageOnDelete;

    public DataChangedOperation(ZooKeeper connection, String znode, boolean getChangedData) {
        this(connection, znode, getChangedData, false);
    }

    public DataChangedOperation(ZooKeeper connection, String znode, boolean getChangedData, boolean sendEmptyMessageOnDelete) {
        super(connection, znode, EventType.NodeDataChanged, EventType.NodeDeleted);
        this.getChangedData = getChangedData;
        this.sendEmptyMessageOnDelete = sendEmptyMessageOnDelete;
    }

    @Override
    protected void installWatch() {
        connection.getData(getNode(), this, new DataCallback() {
            public void processResult(int rc, String path, Object ctx, byte[] data, Stat stat) {
            }
        }, null);
    }

    public OperationResult<byte[]> getResult() {
        OperationResult<byte[]> answer;
        if (EventType.NodeDeleted.equals(getWatchedEvent().getType()) && sendEmptyMessageOnDelete) {
            answer = new OperationResult<byte[]>((byte[])null, null);
        } else if (getChangedData) {
            answer = new GetDataOperation(connection, getNode()).getResult();
        } else {
            answer = null;
        }
        return answer;
    }

    @Override
    public ZooKeeperOperation<?> createCopy() throws Exception {
        return getClass().getConstructor(CONSTRUCTOR_ARGS).newInstance(new Object[] {connection, node, getChangedData, sendEmptyMessageOnDelete});
    }
}
