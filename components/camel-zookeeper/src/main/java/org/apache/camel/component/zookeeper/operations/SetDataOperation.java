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

import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import static java.lang.String.format;

/**
 * <code>SetDataOperation</code> sets the content of a ZooKeeper node. An optional version may be specified that the
 * node must currently have for the operation to succeed.
 *
 * @see {@link ZooKeeper#setData(String, byte[], int)}
 */
public class SetDataOperation extends ZooKeeperOperation<byte[]> {

    private byte[] data;

    private int version = -1;

    public SetDataOperation(ZooKeeper connection, String node, byte[] data) {
        super(connection, node);
        this.data = data;
    }

    @Override
    public OperationResult<byte[]> getResult() {
        try {
            Stat statistics = connection.setData(node, data, version);
            if (LOG.isDebugEnabled()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace(format("Set data of node '%s'  with '%d' bytes of data, retrieved statistics '%s' ",
                            node, data != null ? data.length : 0, statistics));
                } else {
                    LOG.debug(format("Set data of node '%s' with '%d' bytes of data", node, data != null ? data.length : 0));
                }
            }
            return new OperationResult<>(data, statistics);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new OperationResult<>(e);
        } catch (Exception e) {
            return new OperationResult<>(e);
        }
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public ZooKeeperOperation<?> createCopy() throws Exception {
        SetDataOperation copy = (SetDataOperation) super.createCopy();
        copy.version = -1; // set the version to -1 for 'any version'
        return copy;
    }

}
