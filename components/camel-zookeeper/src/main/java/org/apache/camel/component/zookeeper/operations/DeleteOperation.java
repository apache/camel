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

import static java.lang.String.format;

import org.apache.zookeeper.ZooKeeper;

/**
 * <code>setdataOperation</code> sets the content of a ZooKeeper node. An optional version
 * may be specified that the node must currently have for the operation to succeed.
 * @see {@link ZooKeeper#setData(String, byte[], int)}
 */

public class DeleteOperation extends ZooKeeperOperation<Boolean> {

    private int version = -1;

    public DeleteOperation(ZooKeeper connection, String node) {
        super(connection, node);
    }

    public OperationResult<Boolean> getResult() {
        try {
            connection.delete(node, version);
            if (LOG.isDebugEnabled()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace(format("Set data of node '%s'", node));
                } else {
                    LOG.debug(format("Set data of node '%s'", node));
                }
            }
            return new OperationResult<Boolean>(true, null, true);
        } catch (Exception e) {
            return new OperationResult<Boolean>(e);
        }
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public ZooKeeperOperation<?> createCopy() throws Exception {
        DeleteOperation copy = (DeleteOperation) super.createCopy();
        copy.version = -1; // set the version to -1 for 'any version'
        return copy;
    }

}
